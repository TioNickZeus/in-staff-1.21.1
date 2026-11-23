package com.tio.instaff.client.screen;

import com.tio.instaff.client.config.InStaffClientConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Intercepts navigation to the Multiplayer screen until the client acknowledges
 * the one-time data notice. Registered exclusively inside InStaffClient.
 */
public final class MultiplayerConsentHandler {

    private MultiplayerConsentHandler() {
    }

    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (InStaffClientConfig.isMultiplayerNoticeAccepted()) {
            return;
        }
        if (event.getNewScreen() instanceof JoinMultiplayerScreen multiplayerScreen) {
            Screen returnScreen = (event.getCurrentScreen() instanceof TitleScreen)
                    ? event.getCurrentScreen()
                    : new TitleScreen();
            event.setNewScreen(new MultiplayerConsentScreen(returnScreen, multiplayerScreen));
        }
    }
}
