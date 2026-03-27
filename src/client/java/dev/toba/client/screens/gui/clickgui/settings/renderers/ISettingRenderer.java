/**
 * @author Fogma
 * @2026-02-3
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.settings.Module;

public interface ISettingRenderer<T> {
    void render(Module.Setting<T> setting, int primary, int background, int text);
}