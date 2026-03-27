/**
 * @author Fogma
 * @since 2026-02-21
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;

import dev.toba.client.features.impl.module.client.ClickGUI;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class BlockSelectorRenderer implements ISettingRenderer<List<String>> {

    private final ImString searchBuffer = new ImString("", 256);
    private List<String>   cachedBlockIds = null;

    private ClickGUI clickGUI() {
        return ModuleManager.getInstance().getModule(ClickGUI.class);
    }

    @Override
    public void render(Module.Setting<List<String>> setting, int primary, int background, int text) {
        if (setting.getValue() == null) setting.setValue(new ArrayList<>());

        ClickGUI gui = clickGUI();
        int col_primary = gui != null ? gui.primaryColor.getValue()    : primary;
        int col_bg      = gui != null ? gui.backgroundColor.getValue() : background;
        int col_text    = gui != null ? gui.textColor.getValue()        : text;

        List<String> selected = setting.getValue();
        String btnLabel = setting.getName() + ": " + selected.size() + " block(s)##openBtn";

        pushButtonColors(col_primary);
        if (ImGui.button(btnLabel, ImGui.getContentRegionAvailX(), 0))
            ImGui.openPopup("##BlockSelector_" + setting.getName());
        ImGui.popStyleColor(3);

        renderPopup(setting, col_primary, col_bg, col_text);
    }

    private void renderPopup(Module.Setting<List<String>> setting, int primary, int background, int text) {
        ImGui.setNextWindowSize(380, 460);
        ImGui.pushStyleColor(ImGuiCol.PopupBg, background);
        boolean open = ImGui.beginPopup("##BlockSelector_" + setting.getName(), ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoTitleBar);
        ImGui.popStyleColor();
        if (!open) return;

        // ── Search ────────────────────────────────────────────────────────────
        ImGui.pushStyleColor(ImGuiCol.Text, text);
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        ImGui.inputTextWithHint("##blockSearch", "Search...", searchBuffer);
        ImGui.spacing();

        // ── Block list ────────────────────────────────────────────────────────
        ImGui.pushStyleColor(ImGuiCol.ChildBg, background);
        if (ImGui.beginChild("##blockListChild", 0, 400, false)) {
            String       query    = searchBuffer.get().trim().toLowerCase();
            List<String> selected = setting.getValue();

            for (String id : getBlockIds()) {
                String display = id.startsWith("minecraft:") ? id.substring(10) : id;
                if (!query.isEmpty() && !display.contains(query) && !id.contains(query)) continue;

                boolean isSelected = selected.contains(id);

                if (isSelected) ImGui.pushStyleColor(ImGuiCol.CheckMark, primary);
                if (ImGui.checkbox("##chk_" + id, isSelected)) {
                    if (isSelected) selected.remove(id);
                    else            selected.add(id);
                }
                if (isSelected) ImGui.popStyleColor();

                ImGui.sameLine();
                ImGui.textColored(text, display);
            }
        }
        ImGui.endChild();
        ImGui.popStyleColor(2); // ChildBg + Text

        // ── Footer ────────────────────────────────────────────────────────────
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, text);
        ImGui.text(setting.getValue().size() + " selected");
        ImGui.popStyleColor();

        ImGui.sameLine();
        float doneW = 60;
        ImGui.setCursorPosX(ImGui.getWindowWidth() - doneW - ImGui.getStyle().getWindowPaddingX());
        pushButtonColors(primary);
        if (ImGui.button("Done", doneW, 0)) ImGui.closeCurrentPopup();
        ImGui.popStyleColor(3);

        ImGui.endPopup();
    }

    private void pushButtonColors(int primary) {
        ImGui.pushStyleColor(ImGuiCol.Button,        primary);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, dev.toba.client.api.utils.ColorUtil.lighten(primary, 20));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive,  dev.toba.client.api.utils.ColorUtil.lighten(primary, 10));
    }

    private List<String> getBlockIds() {
        if (cachedBlockIds == null) {
            cachedBlockIds = new ArrayList<>();
            for (Block block : Registries.BLOCK)
                cachedBlockIds.add(Registries.BLOCK.getId(block).toString());
            cachedBlockIds.sort(String::compareTo);
        }
        return cachedBlockIds;
    }
}