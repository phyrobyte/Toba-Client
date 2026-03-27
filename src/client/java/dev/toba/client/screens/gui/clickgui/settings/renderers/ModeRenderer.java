/**
 * @author Fogma
 * @since 2026-02-17
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.settings.Module;
import imgui.ImGui;

public class ModeRenderer implements ISettingRenderer<String> {
    @Override
    public void render(Module.Setting<String> setting, int primary, int background, int text) {
        if (ImGui.beginCombo(setting.getName(), setting.getValue())) {

            for (String mode : setting.getModes()) {
                boolean isSelected = setting.getValue().equals(mode);


                if (ImGui.selectable(mode, isSelected)) {
                    setting.setValue(mode);
                }


                if (isSelected) {
                    ImGui.setItemDefaultFocus();
                }
            }
            ImGui.endCombo();
        }
    }
}