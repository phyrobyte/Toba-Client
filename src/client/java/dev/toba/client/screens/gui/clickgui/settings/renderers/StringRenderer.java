/**
 * @author Fogma
 * @since 2026-02-19
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.type.ImString;

public class StringRenderer implements ISettingRenderer<String> {
    private final ImString imString = new ImString(999); // Is this enough? -Fogma

    @Override
    public void render(Module.Setting<String> setting, int primary, int background, int text) {
        imString.set(setting.getValue());

        if (ImGui.inputText(setting.getName(), imString)) {
            setting.setValue(imString.get());
        }
    }
}