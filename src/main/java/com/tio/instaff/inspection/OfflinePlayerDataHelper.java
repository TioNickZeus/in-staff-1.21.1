package com.tio.instaff.inspection;

import com.tio.instaff.InStaff;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Defensive offline playerdata (<UUID>.dat) reader and writer.
 * Manipulates raw NBT tags atomically with temporary file swaps (.tmp)
 * without requiring the target player to be connected.
 */
public final class OfflinePlayerDataHelper {

    private OfflinePlayerDataHelper() {
    }

    public static class OfflineInspectionResult {
        private final Container container;
        private final Runnable saveCallback;

        public OfflineInspectionResult(@NotNull Container container, @NotNull Runnable saveCallback) {
            this.container = container;
            this.saveCallback = saveCallback;
        }

        @NotNull
        public Container getContainer() {
            return container;
        }

        @NotNull
        public Runnable getSaveCallback() {
            return saveCallback;
        }
    }

    /**
     * Resolves the path to a player's .dat file inside world/playerdata/.
     */
    @NotNull
    public static Path getPlayerDataPath(@NotNull MinecraftServer server, @NotNull UUID targetUUID) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(targetUUID + ".dat");
    }

    /**
     * Loads an offline player's inventory (main, armor, offhand) into a 41-slot container.
     */
    @Nullable
    public static OfflineInspectionResult loadOfflineInventory(@NotNull MinecraftServer server, @NotNull UUID targetUUID) {
        Path datFile = getPlayerDataPath(server, targetUUID);
        if (!Files.exists(datFile)) {
            InStaff.LOGGER.warn("Cannot inspect offline inventory: playerdata file does not exist for UUID {}", targetUUID);
            return null;
        }

        try {
            CompoundTag rootTag = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());
            ListTag inventoryList = rootTag.getList("Inventory", Tag.TAG_COMPOUND);
            HolderLookup.Provider provider = server.registryAccess();

            SimpleContainer virtualContainer = new SimpleContainer(InvseeMenu.TARGET_INV_SIZE);

            for (int i = 0; i < inventoryList.size(); i++) {
                CompoundTag itemCompound = inventoryList.getCompound(i);
                byte slot = itemCompound.getByte("Slot");
                ItemStack stack = ItemStack.parseOptional(provider, itemCompound);

                if (slot >= 0 && slot < 36) {
                    virtualContainer.setItem(slot, stack);
                } else if (slot >= 100 && slot <= 103) {
                    // Armor slots: 100 (feet) -> 39, 101 (legs) -> 38, 102 (chest) -> 37, 103 (head) -> 36
                    int mappedIndex = 36 + (103 - slot);
                    virtualContainer.setItem(mappedIndex, stack);
                } else if (slot == -106) {
                    // Offhand slot
                    virtualContainer.setItem(40, stack);
                }
            }

            Runnable saveCallback = () -> {
                try {
                    ListTag newInventoryList = new ListTag();
                    for (int slotIndex = 0; slotIndex < InvseeMenu.TARGET_INV_SIZE; slotIndex++) {
                        ItemStack stack = virtualContainer.getItem(slotIndex);
                        if (!stack.isEmpty()) {
                            CompoundTag itemTag = (CompoundTag) stack.saveOptional(provider);
                            if (slotIndex < 36) {
                                itemTag.putByte("Slot", (byte) slotIndex);
                            } else if (slotIndex < 40) {
                                itemTag.putByte("Slot", (byte) (103 - (slotIndex - 36)));
                            } else {
                                itemTag.putByte("Slot", (byte) -106);
                            }
                            newInventoryList.add(itemTag);
                        }
                    }

                    rootTag.put("Inventory", newInventoryList);
                    savePlayerDataAtomic(datFile, rootTag);
                    InStaff.LOGGER.info("Saved modified offline inventory for UUID {}", targetUUID);
                } catch (Exception e) {
                    InStaff.LOGGER.error("Failed to save offline inventory for UUID {}", targetUUID, e);
                }
            };

            return new OfflineInspectionResult(virtualContainer, saveCallback);
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to load offline playerdata for UUID {}", targetUUID, e);
            return null;
        }
    }

    /**
     * Loads an offline player's Ender Chest into a 27-slot container.
     */
    @Nullable
    public static OfflineInspectionResult loadOfflineEnderChest(@NotNull MinecraftServer server, @NotNull UUID targetUUID) {
        Path datFile = getPlayerDataPath(server, targetUUID);
        if (!Files.exists(datFile)) {
            InStaff.LOGGER.warn("Cannot inspect offline enderchest: playerdata file does not exist for UUID {}", targetUUID);
            return null;
        }

        try {
            CompoundTag rootTag = NbtIo.readCompressed(datFile, NbtAccounter.unlimitedHeap());
            ListTag enderList = rootTag.getList("EnderItems", Tag.TAG_COMPOUND);
            HolderLookup.Provider provider = server.registryAccess();

            SimpleContainer virtualContainer = new SimpleContainer(EnderseeMenu.ENDER_CHEST_SIZE);

            for (int i = 0; i < enderList.size(); i++) {
                CompoundTag itemCompound = enderList.getCompound(i);
                byte slot = itemCompound.getByte("Slot");
                if (slot >= 0 && slot < EnderseeMenu.ENDER_CHEST_SIZE) {
                    ItemStack stack = ItemStack.parseOptional(provider, itemCompound);
                    virtualContainer.setItem(slot, stack);
                }
            }

            Runnable saveCallback = () -> {
                try {
                    ListTag newEnderList = new ListTag();
                    for (int slotIndex = 0; slotIndex < EnderseeMenu.ENDER_CHEST_SIZE; slotIndex++) {
                        ItemStack stack = virtualContainer.getItem(slotIndex);
                        if (!stack.isEmpty()) {
                            CompoundTag itemTag = (CompoundTag) stack.saveOptional(provider);
                            itemTag.putByte("Slot", (byte) slotIndex);
                            newEnderList.add(itemTag);
                        }
                    }

                    rootTag.put("EnderItems", newEnderList);
                    savePlayerDataAtomic(datFile, rootTag);
                    InStaff.LOGGER.info("Saved modified offline enderchest for UUID {}", targetUUID);
                } catch (Exception e) {
                    InStaff.LOGGER.error("Failed to save offline enderchest for UUID {}", targetUUID, e);
                }
            };

            return new OfflineInspectionResult(virtualContainer, saveCallback);
        } catch (Exception e) {
            InStaff.LOGGER.error("Failed to load offline enderchest for UUID {}", targetUUID, e);
            return null;
        }
    }

    private static void savePlayerDataAtomic(Path datFile, CompoundTag rootTag) throws IOException {
        Path tempFile = datFile.resolveSibling(datFile.getFileName() + ".tmp");
        NbtIo.writeCompressed(rootTag, tempFile);

        try {
            Files.move(tempFile, datFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, datFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
