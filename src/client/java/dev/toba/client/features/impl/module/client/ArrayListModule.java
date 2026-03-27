/**
 * @author Fogma
 * @since 2026-02-21
 */
package dev.toba.client.features.impl.module.client;

import dev.toba.client.api.imgui.*;
import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import imgui.*;
import imgui.flag.*;
import java.util.List;
import net.minecraft.client.MinecraftClient;

public class ArrayListModule extends Module {
    private final Setting<Float>   posX            = addSetting(new Setting<>("X pos",       4f,      Setting.SettingType.FLOAT).range(0, 2000));
    private final Setting<Float>   posZ            = addSetting(new Setting<>("Z pos",       25f,      Setting.SettingType.FLOAT).range(0, 2000));
    private final Setting<Float>   fontScale       = addSetting(new Setting<>("Font scale",  1.5f,      Setting.SettingType.FLOAT).range(0.5, 2.0));
    private final Setting<String> fontMode = addSetting(new Setting<>("Font", "Montserrat", Setting.SettingType.MODE)
            .modes("Rubik", "Roboto", "Bebas", "Playwrite", "OpenSans", "Minecraft", "Montserrat"));
    private final Setting<Boolean> colorSettings   = addSetting(new Setting<>("Colors",         true,       Setting.SettingType.SUB_CONFIG));
    private final Setting<Integer> primaryColor    = new Setting<>("Accent color (sidebar)",     0xFF233888, Setting.SettingType.COLOR);
    private final Setting<Integer> backgroundColor = new Setting<>("Background color", 0xFF1E1E2E, Setting.SettingType.COLOR);
    private final Setting<Integer> textColor       = new Setting<>("Text color",       0xFFFFFFFF, Setting.SettingType.COLOR);

    private final RenderInterface renderRef = this::render;

    public ArrayListModule() {
        super("ModuleList", "shows a list of all active modules", Category.CLIENT);
        colorSettings.child(primaryColor);
        colorSettings.child(backgroundColor);
        colorSettings.child(textColor);
    }

    @Override protected void onEnable()  { ImGuiImpl.registerHudOverlay(renderRef); }
    @Override protected void onDisable() { ImGuiImpl.unregisterHudOverlay(renderRef); }

    private void render(ImGuiIO io) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        List<Module> active = ModuleManager.getInstance().getModules().stream()
                .filter(m -> m.isEnabled() && !m.isHidden() && !m.isSettingsOnly() && m != this)
                .sorted((a, b) -> Float.compare(
                        ImGui.calcTextSize(b.getName()).x,
                        ImGui.calcTextSize(a.getName()).x))
                .toList();

        if (active.isEmpty()) return;


        String prevFamily = ImGuiImpl.currentFontFamily;
        ImGuiImpl.currentFontFamily = fontMode.getValue();
        ImFont activeFont = ImGuiImpl.getActiveFont("regular");
        if (activeFont != null) ImGui.pushFont(activeFont);


        float s       = fontScale.getValue();
        float padX    = 3f;
        float padY    = 1f;
        float accentW = 5f;
        float h       = (ImGui.getFontSize() * s) + (padY * 2f);

        int bg  = ColorUtil.argbToAbgr(backgroundColor.getValue());
        int txt = ColorUtil.argbToAbgr(textColor.getValue());
        int acc = ColorUtil.argbToAbgr(primaryColor.getValue());

        ImGui.setNextWindowPos(posX.getValue(), posZ.getValue(), ImGuiCond.Always);

        if (ImGui.begin("##ML",
                ImGuiWindowFlags.NoTitleBar       |
                        ImGuiWindowFlags.NoResize         |
                        ImGuiWindowFlags.AlwaysAutoResize |
                        ImGuiWindowFlags.NoBackground     |
                        (io.getWantCaptureMouse() ? 0 : ImGuiWindowFlags.NoInputs))) {

            if (ImGui.isWindowFocused() && io.getWantCaptureMouse()) {
                posX.setValue(ImGui.getWindowPosX());
                posZ.setValue(ImGui.getWindowPosY());
            }

            float winX = ImGui.getWindowPosX();
            float curY = ImGui.getWindowPosY();
            ImDrawList dl = ImGui.getWindowDrawList();
            float maxW = 0f;

            for (Module mod : active) {
                float textW  = ImGui.calcTextSize(mod.getName()).x * s;
                float rowW   = accentW + padX + textW + padX;
                float bottom = curY + h;
                dl.addRectFilled(winX, curY, winX + rowW, bottom, bg);
                dl.addRectFilled(winX, curY, winX + accentW, bottom, acc);
                dl.addText(ImGui.getFont(), (int)(ImGui.getFontSize() * s),
                        winX + accentW + padX, curY + padY, txt, mod.getName());
                if (rowW > maxW) maxW = rowW;

                curY += h;
            }


            ImGui.dummy(maxW, active.size() * h);
        }
        ImGui.end();


        if (activeFont != null) ImGui.popFont();
        ImGuiImpl.currentFontFamily = prevFamily;

    }
}
