package dev.toba.client.api.utils;

import dev.toba.client.features.impl.module.client.ClickGUI;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TobaChat {
    private static final Text PREFIX = Text.empty()
            .append(Text.literal("[").formatted(Formatting.DARK_GREEN))
            .append(Text.literal("Toba").formatted(Formatting.GREEN))
            .append(Text.literal("] ").formatted(Formatting.DARK_GREEN));

    public static void send(String message) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.empty().append(PREFIX).append(Text.literal(message).formatted(Formatting.GRAY)), false);
        }
    }

    public static void sendDebug(String message) {
        ClickGUI clickGUI = ModuleManager.getInstance().getModule(ClickGUI.class);
        if (clickGUI != null && clickGUI.allowChatDebug.getValue()) {
            sendColored("[Debug] " + message, Formatting.YELLOW);
        }
    }

    public static void sendColored(String message, Formatting color) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.empty().append(PREFIX).append(Text.literal(message).formatted(color)), false);
        }
    }

    public static void send(Text text) {
        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.empty().append(PREFIX).append(text), false);
        }
    }

    public static void error(String message) {
        sendColored(message, Formatting.RED);
    }

    public static void info(String message) {
        sendColored(message, Formatting.GRAY);
    }
}