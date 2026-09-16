package com.tio.instaff.inspection;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared container menu for live and offline Ender Chest inspection (/endersee).
 * Synchronizes slot interactions bidirectionally between staff and target player's Ender Chest.
 */
public class EnderseeMenu extends AbstractContainerMenu {

    public static final int ENDER_CHEST_SIZE = 27;

    private final Container targetContainer;
    private final String targetName;
    private final UUID targetUUID;
    private final boolean offline;
    private final Runnable onCloseCallback;

    /**
     * Client constructor instantiated via IMenuTypeExtension.
     */
    public EnderseeMenu(int containerId, Inventory staffInventory, RegistryFriendlyByteBuf extraData) {
        this(
                containerId,
                staffInventory,
                new SimpleContainer(ENDER_CHEST_SIZE),
                extraData.readUtf(),
                extraData.readUUID(),
                extraData.readBoolean(),
                null
        );
    }

    /**
     * Server constructor.
     */
    @SuppressWarnings("this-escape")
    public EnderseeMenu(
            int containerId,
            Inventory staffInventory,
            Container targetContainer,
            String targetName,
            UUID targetUUID,
            boolean offline,
            @Nullable Runnable onCloseCallback
    ) {
        super(ModMenus.ENDERSEE.get(), containerId);
        checkContainerSize(targetContainer, ENDER_CHEST_SIZE);
        this.targetContainer = targetContainer;
        this.targetName = targetName;
        this.targetUUID = targetUUID;
        this.offline = offline;
        this.onCloseCallback = onCloseCallback;

        targetContainer.startOpen(staffInventory.player);

        // 1. Target Ender Chest (27 slots: 3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(targetContainer, col + (row * 9), 8 + (col * 18), 18 + (row * 18)));
            }
        }

        // 2. Staff Player Main Inventory (3 rows of 9)
        int staffInvYStart = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(staffInventory, 9 + col + (row * 9), 8 + (col * 18), staffInvYStart + (row * 18)));
            }
        }

        // 3. Staff Player Hotbar (1 row of 9)
        int staffHotbarYStart = staffInvYStart + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(staffInventory, col, 8 + (col * 18), staffHotbarYStart));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.targetContainer.stopOpen(player);
        if (this.onCloseCallback != null) {
            this.onCloseCallback.run();
        }
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index < ENDER_CHEST_SIZE) {
                // Moving from target Ender Chest to staff inventory
                if (!this.moveItemStackTo(stackInSlot, ENDER_CHEST_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from staff inventory to target Ender Chest
                if (!this.moveItemStackTo(stackInSlot, 0, ENDER_CHEST_SIZE, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public boolean isOffline() {
        return offline;
    }
}
