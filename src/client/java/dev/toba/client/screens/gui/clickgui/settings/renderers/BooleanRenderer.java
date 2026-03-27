/**
 * @author Fogma
 * @2026-02-3
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.type.ImBoolean;

public class BooleanRenderer implements ISettingRenderer<Boolean> {
    @Override
    public void render(Module.Setting<Boolean> setting, int primary, int background, int text) {
        ImBoolean value = new ImBoolean(setting.getValue());
        if (ImGui.checkbox(setting.getName(), value)) {
            setting.setValue(value.get());
        }
    }
}