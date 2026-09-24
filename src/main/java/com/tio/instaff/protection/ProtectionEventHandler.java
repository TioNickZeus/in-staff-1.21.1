package com.tio.instaff.protection;

import com.tio.instaff.InStaff;
import com.tio.instaff.util.LocalizationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Event handler for server-authoritative item protection:
 * - Prevents right-click usage of banned items.
 * - Prevents placement of banned blocks.
 * - Prevents pickup and confiscates items with TOTAL restriction.
 */
@EventBusSubscriber(modid = InStaff.MODID)
public final class ProtectionEventHandler {

    private ProtectionEventHandler() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.hasPermissions(2)) {
            return; // Staff bypass
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        BanItemMode mode = BanItemManager.getInstance().getMode(stack);
        if (mode != null && mode.blocksUse()) {
            event.setCanceled(true);
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.banitem.denied_use"));
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.hasPermissions(2)) {
            return; // Staff bypass
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        BanItemMode mode = BanItemManager.getInstance().getMode(stack);
        if (mode != null && mode.blocksPlacement()) {
            event.setCanceled(true);
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.banitem.denied_place"));
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.hasPermissions(2)) {
            return; // Staff bypass
        }

        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        BanItemMode mode = BanItemManager.getInstance().getMode(blockKey.toString());
        if (mode != null && mode.blocksPlacement()) {
            event.setCanceled(true);
            player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.banitem.denied_place"));
        }
    }

    @SubscribeEvent
    public static void onPickupItem(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (player.hasPermissions(2)) {
            return; // Staff bypass
        }

        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) {
            return;
        }

        BanItemMode mode = BanItemManager.getInstance().getMode(stack);
        if (mode != null && mode.blocksPossession()) {
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }

            if (player.hasPermissions(2)) {
                return; // Staff bypass
            }

            // Inventory confiscation check every 20 ticks (1 second)
            if (player.tickCount % 20 != 0) {
                return;
            }

            boolean confiscated = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) {
                    continue;
                }

                BanItemMode mode = BanItemManager.getInstance().getMode(stack);
                if (mode != null && mode.blocksPossession()) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    confiscated = true;
                }
            }

            if (confiscated) {
                player.sendSystemMessage(LocalizationHelper.getPrefixedMessage("instaff.banitem.confiscated"));
            }
        } catch (Throwable t) {
            InStaff.LOGGER.error("[In-Staff] Error in ProtectionEventHandler.onPlayerTick", t);
        }
    }
}
