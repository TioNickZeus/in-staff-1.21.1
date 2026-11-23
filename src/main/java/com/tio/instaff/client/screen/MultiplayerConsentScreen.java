package com.tio.instaff.client.screen;

import com.tio.instaff.client.config.InStaffClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * First-time client consent prompt shown when entering Multiplayer.
 * Explains that only mod IDs, SHA-256 mod hashes, and a per-server anonymous SHA-256 token
 * are sent to the connected server.
 * This class is strictly CLIENT-side and must never be loaded on dedicated servers.
 */
public class MultiplayerConsentScreen extends Screen {

    private final Screen returnScreen;
    private final Screen targetScreen;
    private MultiLineLabel messageLabel = MultiLineLabel.EMPTY;

    public MultiplayerConsentScreen(@NotNull Screen returnScreen, @NotNull Screen targetScreen) {
        super(Component.translatable("instaff.screen.consent.title"));
        this.returnScreen = returnScreen;
        this.targetScreen = targetScreen;
    }

    @Override
    protected void init() {
        int maxTextWidth = Math.min(360, this.width - 40);
        this.messageLabel = MultiLineLabel.create(this.font, Component.translatable("instaff.screen.consent.body"), maxTextWidth);

        int lineHeight = this.font.lineHeight + 2;
        int textHeight = this.messageLabel.getLineCount() * lineHeight;
        int buttonY = Math.min(this.height - 40, Math.max(this.height / 2 + textHeight / 2 + 16, 120));
        int buttonWidth = 140;
        int buttonHeight = 20;
        int gap = 12;

        this.addRenderableWidget(
                Button.builder(Component.translatable("instaff.screen.consent.accept"), btn -> {
                            InStaffClientConfig.markMultiplayerNoticeAccepted();
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(this.targetScreen);
                            }
                        })
                        .bounds(this.width / 2 - buttonWidth - gap / 2, buttonY, buttonWidth, buttonHeight)
                        .build()
        );

        this.addRenderableWidget(
                Button.builder(Component.translatable("instaff.screen.consent.decline"), btn -> onClose())
                        .bounds(this.width / 2 + gap / 2, buttonY, buttonWidth, buttonHeight)
                        .build()
        );
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int lineHeight = this.font.lineHeight + 2;
        int textHeight = this.messageLabel.getLineCount() * lineHeight;
        int bodyTopY = Math.max(48, (this.height - textHeight) / 2 - 12);

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, bodyTopY - 24, 0xFFFFFF);
        this.messageLabel.renderCentered(guiGraphics, this.width / 2, bodyTopY, lineHeight, 0xE0E0E0);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.returnScreen);
        }
    }
}
