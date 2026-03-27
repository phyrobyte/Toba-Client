package dev.toba.client.api.utils;

import dev.toba.client.api.config.TobaConfig;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import dev.toba.client.screens.gui.clickgui.TobaScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;

public class KeybindUtil {

    public static void handleModuleKeys(MinecraftClient client) {
        if (client.currentScreen != null || client.player == null) return;

        for (Module m : ModuleManager.getInstance().getModules()) {
            int kb = m.getKeyBind();
            boolean isPressed = kb != -1 && InputUtil.isKeyPressed(client.getWindow(), kb);

            if (isPressed && !m.wasKeyDown) {
                m.toggle();
                TobaConfig.save();
            }
            m.wasKeyDown = isPressed;
        }
    }

    public static void handleGuiKey(KeyBinding guiKey) {
        if (guiKey != null) while (guiKey.wasPressed()) MinecraftClient.getInstance().setScreen(new TobaScreen());
    }
}