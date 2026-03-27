/**
 * @author Fogma
 * @since 2026-02-03
 */
package dev.toba.client.screens.gui.clickgui.settings;

import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.Module.Setting.RangeValue;
import dev.toba.client.screens.gui.clickgui.settings.renderers.*;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;

import java.util.List;

public class SettingsRenderer {
    private final BooleanRenderer      boolRenderer          = new BooleanRenderer();
    private final ColorRenderer        colorRenderer         = new ColorRenderer();
    private final StringRenderer       stringRenderer        = new StringRenderer();
    private final ModeRenderer         modeRenderer          = new ModeRenderer();
    private final ButtonRenderer       buttonRenderer        = new ButtonRenderer();
    private final RewarpListRenderer   rewarpListRenderer    = new RewarpListRenderer();
    private final MultiRangeRenderer   multiRangeRenderer    = new MultiRangeRenderer();
    private final BlockSelectorRenderer blockSelectorRenderer = new BlockSelectorRenderer();

    @SuppressWarnings("unchecked")
    public void renderSettings(List<Module.Setting<?>> settings, int primary, int background, int text) {
        ImGui.pushStyleColor(ImGuiCol.FrameBg,        ColorUtil.lighten(background, 15));
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, ColorUtil.lighten(background, 15));
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive,  ColorUtil.lighten(background, 15));
        ImGui.pushStyleColor(ImGuiCol.SliderGrab,     primary);
        ImGui.pushStyleColor(ImGuiCol.CheckMark,      primary);

        ImGui.pushItemWidth(200);

        for (Module.Setting<?> setting : settings) {
            ImGui.pushID(setting.getName());
            switch (setting.getType()) {
                case BOOLEAN      -> boolRenderer.render((Module.Setting<Boolean>) setting, primary, background, text);
                case INTEGER      -> NumberRenderer.renderInt((Module.Setting<Integer>) setting);
                case FLOAT        -> NumberRenderer.renderFloat((Module.Setting<Float>) setting);
                case COLOR        -> colorRenderer.render((Module.Setting<Integer>) setting, primary, background, text);
                case STRING       -> stringRenderer.render((Module.Setting<String>) setting, primary, background, text);
                case MODE         -> modeRenderer.render((Module.Setting<String>) setting, primary, background, text);
                case BUTTON       -> buttonRenderer.render((Module.Setting<Boolean>) setting, primary, background, text);
                case REWARP_LIST  -> rewarpListRenderer.render((Module.Setting<Boolean>) setting, primary, background, text);
                case MULTI_RANGE  -> multiRangeRenderer.render((Module.Setting<RangeValue>) setting);
                case BLOCK_LIST   -> blockSelectorRenderer.render((Module.Setting<List<String>>) setting, primary, background, text);
                case SUB_CONFIG   -> renderSubConfig(setting, primary, background, text);
            }
            ImGui.popID();
            ImGui.spacing();
        }

        ImGui.popItemWidth();
        ImGui.popStyleColor(5);
    }

    private void renderSubConfig(Module.Setting<?> setting, int primary, int background, int text) {
        ImGui.pushStyleColor(ImGuiCol.Header,        ColorUtil.lighten(background, 10));
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, ColorUtil.lighten(background, 10));
        ImGui.pushStyleColor(ImGuiCol.HeaderActive,  ColorUtil.lighten(background, 10));

        if (ImGui.treeNodeEx(setting.getName(), ImGuiTreeNodeFlags.SpanAvailWidth)) {
            if (setting.hasChildren()) {
                ImGui.indent(10);
                renderSettings(setting.getChildren(), primary, background, text);
                ImGui.unindent(10);
            }
            ImGui.treePop();
        }
        ImGui.popStyleColor(3);
    }
}