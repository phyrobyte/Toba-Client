package dev.toba.client.features.impl.module.client;

import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import dev.toba.client.screens.gui.clickgui.TobaScreen;
import dev.toba.client.api.imgui.ImGuiImpl;

public class ClickGUI extends Module {
    public final Setting<Boolean> colorSettings;
    public final Setting<Integer> primaryColor;
    public final Setting<Integer> backgroundColor;
    public final Setting<Integer> textColor;
    public final Setting<String> mode;
    public final Setting<Boolean> advanced;
    public final Setting<Boolean> allowChatDebug;
    public final Setting<Boolean> allowCustomTitle;
    public final Setting<String> customTitleText;
    public final Setting<String> fontMode;

    public ClickGUI() {
        super("Toba", "allows you to configure the looks of this client.", Category.CLIENT);
        setKeyBind(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);

        mode = addSetting(new Setting<>("Mode", "Menu", Setting.SettingType.MODE)
                .modes("ClickGUI", "Menu"));

        fontMode = addSetting(new Setting<>("Font", "Rubik", Setting.SettingType.MODE)
                .modes("Rubik", "Roboto", "Bebas", "Playwrite", "OpenSans", "Minecraft", "Montserrat"));

        colorSettings = addSetting(new Setting<>("Color settings", true, Setting.SettingType.SUB_CONFIG));
        primaryColor = new Setting<>("Primary color", 0xFF233888, Setting.SettingType.COLOR);
        backgroundColor = new Setting<>("Background", 0xFF1E1E2E, Setting.SettingType.COLOR);
        textColor = new Setting<>("Text color", 0xFFFFFFFF, Setting.SettingType.COLOR);
        colorSettings.child(primaryColor);
        colorSettings.child(backgroundColor);
        colorSettings.child(textColor);

        advanced = addSetting(new Setting<>("Advanced", true, Setting.SettingType.SUB_CONFIG));
        allowChatDebug = new Setting<>("allow chat debug", false, Setting.SettingType.BOOLEAN);
        allowCustomTitle = new Setting<>("allow custom title", true, Setting.SettingType.BOOLEAN);
        customTitleText = new Setting<>("title text", "Toba | go to settings ->Toba->Advanced ->Title Text to change or disable this text", Setting.SettingType.STRING);
        advanced.child(allowChatDebug);
        advanced.child(allowCustomTitle);
        advanced.child(customTitleText);

        super.setEnabled(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) return;
        super.setEnabled(true);
    }

    @Override
    public void toggle() {
        openScreen();
    }

    @Override
    protected void onEnable() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.mouse == null) return;
        openScreen();
    }

    @Override
    protected void onDisable() {}

    private void openScreen() {
        ImGuiImpl.currentFontFamily = fontMode.getValue();
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mode.getValue().equalsIgnoreCase("Menu")) {
            mc.setScreen(new dev.toba.client.screens.gui.menu.TobaScreen());
        } else {
            mc.setScreen(new TobaScreen());
        }
    }
}