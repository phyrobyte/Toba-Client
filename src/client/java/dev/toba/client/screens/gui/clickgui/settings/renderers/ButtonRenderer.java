package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

/**
 * Renders a clickable button setting.
 * When clicked, fires the callback attached to the setting.
 */
public class ButtonRenderer implements ISettingRenderer<Boolean> {

    @Override
    public void render(Module.Setting<Boolean> setting, int primary, int background, int text) {
        ImGui.pushStyleColor(ImGuiCol.Button, primary);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ColorUtil.lighten(primary, 15));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ColorUtil.lighten(primary, 25));

        if (ImGui.button(setting.getName(), ImGui.getContentRegionAvailX(), 24)) {
            Runnable callback = setting.getButtonCallback();
            if (callback != null) {
                callback.run();
            }
        }

        ImGui.popStyleColor(3);
    }
}
