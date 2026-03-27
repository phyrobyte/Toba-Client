package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.api.utils.ColorUtil;
import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

import java.util.List;

/**
 * Renders the rewarp list setting: a "Set Rewarp" button at the top,
 * followed by a list of saved rewarp entries, each showing the name,
 * coordinates, and a red minus button to remove it.
 */
public class RewarpListRenderer implements ISettingRenderer<Boolean> {

    @Override
    public void render(Module.Setting<Boolean> setting, int primary, int background, int text) {
        Module.Setting.RewarpListProvider provider = setting.getRewarpListProvider();
        if (provider == null) return;

        // ── "Set Rewarp" button ──
        ImGui.pushStyleColor(ImGuiCol.Button, primary);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ColorUtil.lighten(primary, 15));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ColorUtil.lighten(primary, 25));

        if (ImGui.button("Set Rewarp", ImGui.getContentRegionAvailX(), 26)) {
            provider.onSetRewarp();
        }

        ImGui.popStyleColor(3);

        // ── Rewarp entries list ──
        List<Module.Setting.RewarpEntry> entries = provider.getRewarpEntries();
        if (entries == null || entries.isEmpty()) {
            ImGui.spacing();
            ImGui.textColored(ColorUtil.lighten(background, 60), "No rewarp points set");
            return;
        }

        ImGui.spacing();

        int removeIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            Module.Setting.RewarpEntry entry = entries.get(i);
            ImGui.pushID("rewarp_" + i);

            float availWidth = ImGui.getContentRegionAvailX();
            float buttonSize = 22;
            float entryWidth = availWidth - buttonSize - 6;

            // ── Entry background ──
            ImGui.pushStyleColor(ImGuiCol.Button, ColorUtil.lighten(background, 8));
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ColorUtil.lighten(background, 12));
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, ColorUtil.lighten(background, 12));

            // Entry box (non-interactive, just visual)
            ImGui.button("##entry_bg_" + i, entryWidth, 36);

            // Draw entry content on top of the button
            float rectMinX = ImGui.getItemRectMinX();
            float rectMinY = ImGui.getItemRectMinY();

            // Rewarp name
            ImGui.getWindowDrawList().addText(rectMinX + 8, rectMinY + 3, text, entry.name);

            // Coordinates in dimmer color
            int dimText = ColorUtil.lighten(background, 50);
            ImGui.getWindowDrawList().addText(rectMinX + 8, rectMinY + 18, dimText, entry.coordString());

            ImGui.popStyleColor(3);

            // ── Minus button ──
            ImGui.sameLine(0, 4);

            // Red minus button
            int red = ColorUtil.argbToAbgr(0xFF3D1111);
            int redHover = ColorUtil.argbToAbgr(0xFFCC3333);
            int redActive = ColorUtil.argbToAbgr(0xFFFF4444);
            ImGui.pushStyleColor(ImGuiCol.Button, red);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, redHover);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, redActive);

            // Center the minus button vertically with the entry
            float cursorY = ImGui.getCursorPosY();
            ImGui.setCursorPosY(cursorY + (36 - buttonSize) / 2f);

            if (ImGui.button("-##remove_" + i, buttonSize, buttonSize)) {
                removeIndex = i;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Remove rewarp point");
            }

            ImGui.popStyleColor(3);
            ImGui.popID();
            ImGui.spacing();
        }

        // Handle removal outside the loop to avoid ConcurrentModification
        if (removeIndex >= 0) {
            provider.onRemoveRewarp(removeIndex);
        }
    }
}
