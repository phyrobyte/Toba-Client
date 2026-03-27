package dev.toba.client.api.proxy;

import dev.toba.client.mixin.MultiplayerScreenAccessor;
import dev.toba.client.screens.gui.proxy.ProxyManagerScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

public final class ProxyUiHooks {
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 8;
    private static final int BUTTON_TOP = 6;

    private ProxyUiHooks() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof MultiplayerScreen multiplayerScreen)) {
                return;
            }

            ProxyManager proxyManager = ProxyManager.getInstance();
            ButtonWidget button = ButtonWidget.builder(Text.literal(proxyManager.getButtonLabel()), widget -> {
                        String preferredTarget = getSelectedServerAddress(multiplayerScreen);
                        client.setScreen(new ProxyManagerScreen(multiplayerScreen, preferredTarget));
                    })
                    .dimensions(scaledWidth - BUTTON_WIDTH - BUTTON_MARGIN, BUTTON_TOP, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.of(Text.literal(proxyManager.getTooltipText())))
                    .build();

            Screens.getButtons(screen).add(button);
        });
    }

    private static String getSelectedServerAddress(MultiplayerScreen multiplayerScreen) {
        MultiplayerServerListWidget serverListWidget = ((MultiplayerScreenAccessor) multiplayerScreen).toba$getServerListWidget();
        if (serverListWidget == null) {
            return null;
        }

        if (serverListWidget.getSelectedOrNull() instanceof MultiplayerServerListWidget.ServerEntry serverEntry) {
            ServerInfo server = serverEntry.getServer();
            return server == null ? null : server.address;
        }

        return null;
    }
}
