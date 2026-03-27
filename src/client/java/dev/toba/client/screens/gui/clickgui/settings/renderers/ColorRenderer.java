/**
 * @author Fogma
 * @since 2026-02-03
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.flag.ImGuiColorEditFlags;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;

public class ColorRenderer implements ISettingRenderer<Integer> {
    @Override
    public void render(Module.Setting<Integer> setting, int primary, int background, int text) {
        float[] color = ColorUtil.intToFloatArray(setting.getValue());
        float outlineR = 1.0f - color[0], outlineG = 1.0f - color[1], outlineB = 1.0f - color[2];

        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 1.0f, 1.0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 4.0f);

        if (ImGui.colorButton("##" + setting.getName(), color[0], color[1], color[2], color[3], ImGuiColorEditFlags.NoTooltip, 150, 18)) {
            ImGui.openPopup("picker_" + setting.getName());
        }

        ImGui.sameLine();
        ImGui.text(setting.getName());

        if (ImGui.beginPopup("picker_" + setting.getName())) {
            ImGui.setNextWindowSize(280, 300, ImGuiCond.FirstUseEver);
            if (ImGui.colorPicker4("##picker", color, ImGuiColorEditFlags.AlphaBar | ImGuiColorEditFlags.DisplayRGB)) {
                setting.setValue(ColorUtil.floatArrayToInt(color));
            }
            ImGui.endPopup();
        }
        ImGui.popStyleVar(2);
    }
}