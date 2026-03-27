/**
 * @author Fogma
 * @since 2026-02-21
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.Module.Setting.RangeValue;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiMouseButton;

import java.util.HashMap;
import java.util.Map;

public class MultiRangeRenderer {
    private static final Map<String, Integer> activeHandles = new HashMap<>();

    public static void render(Module.Setting<RangeValue> setting) {
        RangeValue val = setting.getValue();
        float fMin = (float) setting.getMin();
        float fMax = (float) setting.getMax();
        String name = setting.getName();

        ImGui.text(name);

        float width  = ImGui.calcItemWidth();
        float height = ImGui.getFrameHeight();
        float grabW  = 8f;

        ImVec2 pos = ImGui.getCursorScreenPos();

        ImGui.invisibleButton("##mr_" + name, new ImVec2(width, height));

        boolean isActive  = ImGui.isItemActive();
        boolean isClicked = ImGui.isItemClicked(ImGuiMouseButton.Left);
        ImVec2  mouse     = ImGui.getMousePos();

        if (isClicked) {
            float minCenterX = pos.x + (float)((val.min - fMin) / (fMax - fMin) * (width - grabW)) + grabW / 2f;
            float maxCenterX = pos.x + (float)((val.max - fMin) / (fMax - fMin) * (width - grabW)) + grabW / 2f;
            int chosen = Math.abs(mouse.x - minCenterX) <= Math.abs(mouse.x - maxCenterX) ? 0 : 1;
            activeHandles.put(name, chosen);
        }

        if (!isActive) activeHandles.remove(name);

        int handle = activeHandles.getOrDefault(name, -1);
        if (isActive && handle != -1) {
            float clamped  = Math.max(pos.x, Math.min(pos.x + width - grabW, mouse.x - grabW / 2f));
            float newValue = fMin + ((clamped - pos.x) / (width - grabW)) * (fMax - fMin);
            if (handle == 0) val.min = Math.min(val.max, newValue);
            else             val.max = Math.max(val.min, newValue);
        }

        ImDrawList dl = ImGui.getWindowDrawList();

        float minX = pos.x + (float)((val.min - fMin) / (fMax - fMin) * (width - grabW));
        float maxX = pos.x + (float)((val.max - fMin) / (fMax - fMin) * (width - grabW));

        dl.addRectFilled(pos.x, pos.y, pos.x + width, pos.y + height, ImGui.getColorU32(ImGuiCol.FrameBg), 3f);

        int grabCol       = ImGui.getColorU32(ImGuiCol.SliderGrab);
        int grabActiveCol = ImGui.getColorU32(ImGuiCol.SliderGrabActive);
        dl.addRectFilled(minX, pos.y, minX + grabW, pos.y + height, handle == 0 ? grabActiveCol : grabCol, 2f);
        dl.addRectFilled(maxX, pos.y, maxX + grabW, pos.y + height, handle == 1 ? grabActiveCol : grabCol, 2f);

        String valueText = String.format("%.2f - %.2f", val.min, val.max);
        float  textW     = ImGui.calcTextSize(valueText).x;
        float  textH     = ImGui.getTextLineHeight();
        dl.addText(pos.x + (width - textW) / 2f, pos.y + (height - textH) / 2f, ImGui.getColorU32(ImGuiCol.Text), valueText);
    }
}