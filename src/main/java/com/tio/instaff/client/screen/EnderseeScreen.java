package com.tio.instaff.client.screen;

import com.tio.instaff.inspection.EnderseeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Client-side GUI screen for /endersee inspection.
 * Renders target player's Ender Chest slots.
 * This class is strictly CLIENT-side and must never be loaded on dedicated servers.
 */
public class EnderseeScreen extends AbstractContainerScreen<EnderseeMenu> {

    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public EnderseeScreen(EnderseeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(CONTAINER_TEXTURE, x, y, 0, 0, this.imageWidth, 71);
        guiGraphics.blit(CONTAINER_TEXTURE, x, y + 71, 0, 126, this.imageWidth, 96);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY) {
        String targetInfo = this.menu.getTargetName() + (this.menu.isOffline() ? " (Offline EnderChest)" : " (EnderChest)");
        guiGraphics.drawString(this.font, targetInfo, this.titleLabelX, 6, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
