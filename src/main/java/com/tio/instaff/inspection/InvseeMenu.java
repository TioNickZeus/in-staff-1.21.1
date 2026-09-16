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
 * Shared container menu for live and offline inventory inspection (/invsee).
 * Synchronizes slot interactions bidirectionally between staff and target player.
 */
public class InvseeMenu extends AbstractContainerMenu {

    public static final int TARGET_INV_SIZE = 41;

    private final Container targetContainer;
    private final String targetName;
    private final UUID targetUUID;
    private final boolean offline;
    private final Runnable onCloseCallback;

    /**
     * Client constructor instantiated via IMenuTypeExtension.
     */
    public InvseeMenu(int containerId, Inventory staffInventory, RegistryFriendlyByteBuf extraData) {
        this(
                containerId,
                staffInventory,
                new SimpleContainer(TARGET_INV_SIZE),
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
    public InvseeMenu(
            int containerId,
            Inventory staffInventory,
            Container targetContainer,
            String targetName,
            UUID targetUUID,
            boolean offline,
            @Nullable Runnable onCloseCallback
    ) {
        super(ModMenus.INVSEE.get(), containerId);
        checkContainerSize(targetContainer, TARGET_INV_SIZE);
        this.targetContainer = targetContainer;
        this.targetName = targetName;
        this.targetUUID = targetUUID;
        this.offline = offline;
        this.onCloseCallback = onCloseCallback;

        targetContainer.startOpen(staffInventory.player);

        // 1. Target Armor Slots (slots 36 to 39)
        for (int i = 0; i < 4; i++) {
            this.addSlot(new Slot(targetContainer, 36 + i, 8 + (i * 18), 18));
        }

        // 2. Target Offhand Slot (slot 40)
        this.addSlot(new Slot(targetContainer, 40, 98, 18));

        // 3. Target Main Inventory (slots 9 to 35) -> 3 rows of 9
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(targetContainer, 9 + col + (row * 9), 8 + (col * 18), 40 + (row * 18)));
            }
        }

        // 4. Target Hotbar (slots 0 to 8) -> 1 row of 9
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(targetContainer, col, 8 + (col * 18), 98));
        }

        // 5. Staff Player Main Inventory (3 rows of 9)
        int staffInvYStart = 130;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(staffInventory, 9 + col + (row * 9), 8 + (col * 18), staffInvYStart + (row * 18)));
            }
        }

        // 6. Staff Player Hotbar (1 row of 9)
        int staffHotbarYStart = staffInvYStart + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(staffInventory, col, 8 + (col * 18), staffHotbarYStart));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return this.targetContainer.stillValid(player);
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

            if (index < TARGET_INV_SIZE) {
                // Moving from target inventory to staff inventory
                if (!this.moveItemStackTo(stackInSlot, TARGET_INV_SIZE, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Moving from staff inventory to target inventory
                if (!this.moveItemStackTo(stackInSlot, 0, TARGET_INV_SIZE, false)) {
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
