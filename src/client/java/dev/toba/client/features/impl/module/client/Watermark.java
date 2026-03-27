package dev.toba.client.features.impl.module.client;

import dev.toba.client.api.imgui.*;
import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import imgui.*;
import imgui.flag.*;
import net.minecraft.client.MinecraftClient;

public class Watermark extends Module {
    private final Setting<String>  text      = addSetting(new Setting<>("Text", "GAMER HACK", Setting.SettingType.STRING));
    private final Setting<Float>   posX      = addSetting(new Setting<>("X", 4f, Setting.SettingType.FLOAT).range(0, 2000));
    private final Setting<Float>   posZ      = addSetting(new Setting<>("Z", 4f, Setting.SettingType.FLOAT).range(0, 2000));
    private final Setting<Float>   scale     = addSetting(new Setting<>("Scale", 1.5f, Setting.SettingType.FLOAT).range(0.5, 2.0));
    private final Setting<String>  font      = addSetting(new Setting<>("Font", "Roboto", Setting.SettingType.MODE).modes("Rubik", "Roboto", "Montserrat", "Minecraft"));
    private final Setting<Integer> color     = addSetting(new Setting<>("Color", 0xFFFFFFFF, Setting.SettingType.COLOR));

    private final RenderInterface renderRef = this::render;

    public Watermark() { super("Watermark", "just renders text ig", Category.CLIENT); }

    @Override protected void onEnable()  { ImGuiImpl.registerHudOverlay(renderRef); }
    @Override protected void onDisable() { ImGuiImpl.unregisterHudOverlay(renderRef); }

    private void render(ImGuiIO io) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        String prev = ImGuiImpl.currentFontFamily;
        ImGuiImpl.currentFontFamily = font.getValue();
        ImFont f = ImGuiImpl.getActiveFont("regular");
        if (f != null) ImGui.pushFont(f);

        ImGui.setNextWindowPos(posX.getValue(), posZ.getValue(), ImGuiCond.Always);
        if (ImGui.begin("##WM", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoResize | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoBackground | (io.getWantCaptureMouse() ? 0 : ImGuiWindowFlags.NoInputs))) {

            if (ImGui.isWindowFocused() && io.getWantCaptureMouse()) {
                posX.setValue(ImGui.getWindowPosX());
                posZ.setValue(ImGui.getWindowPosY());
            }

            ImGui.getWindowDrawList().addText(ImGui.getFont(), (int)(ImGui.getFontSize() * scale.getValue()),
                    ImGui.getWindowPosX(), ImGui.getWindowPosY(), ColorUtil.argbToAbgr(color.getValue()), text.getValue());

            ImGui.dummy(ImGui.calcTextSize(text.getValue()).x * scale.getValue(), ImGui.getFontSize() * scale.getValue());
        }
        ImGui.end();

        if (f != null) ImGui.popFont();
        ImGuiImpl.currentFontFamily = prev;
    }
}
