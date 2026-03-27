/**
 * @author Fogma
 * @2026-02-05
 */
package dev.toba.client.screens.gui.menu;

import dev.toba.client.api.config.TobaConfig;
import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import dev.toba.client.api.utils.*;
import dev.toba.client.features.impl.module.client.ClickGUI;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import dev.toba.client.screens.gui.clickgui.settings.SettingsRenderer;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.HashSet;
import java.util.Set;

public class TobaScreen extends Screen implements RenderInterface {
    private final SettingsRenderer settingsRenderer = new SettingsRenderer();
    private static Module.Category selectedCategory = Module.Category.MACRO;
    private Module bindingModule = null;
    private final Set<Module> expandedModules = new HashSet<>();
    private final long openTime = System.currentTimeMillis();

    public TobaScreen() { super(Text.literal("Toba2")); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(ImGuiIO io) {
        float ease = (float) Math.sin(Math.min(1f, (System.currentTimeMillis() - openTime) / 250f) * Math.PI / 2);
        ClickGUI c = ModuleManager.getInstance().getModule(ClickGUI.class);
        if (c != null) {
            ImGuiImpl.currentFontFamily = c.fontMode.getValue();
        }

        int p = ColorUtil.argbToAbgr(c != null ? c.primaryColor.getValue() : 0xFF5C6BC0);
        int b = ColorUtil.argbToAbgr(c != null ? c.backgroundColor.getValue() : 0xBB1E1E2E);
        int t = ColorUtil.argbToAbgr(c != null ? c.textColor.getValue() : 0xFFFFFFFF);

        int alpha = (int) (ease * ((b >> 24) & 0xFF)) << 24;
        int textAlpha = (int) (ease * 255) << 24;
        int pA = (p & 0x00FFFFFF) | textAlpha, bA = (b & 0x00FFFFFF) | alpha, tA = (t & 0x00FFFFFF) | textAlpha;

        if (bindingModule != null && KeyUtil.handleBinding(bindingModule)) bindingModule = null;

        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 6f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 3f);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 0f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0f);

        ImGui.pushStyleColor(ImGuiCol.WindowBg, bA);
        ImGui.pushStyleColor(ImGuiCol.Border, 0);
        ImGui.pushStyleColor(ImGuiCol.Text, tA);

        ImGui.setNextWindowPos(io.getDisplaySizeX() * 0.5f, io.getDisplaySizeY() * 0.5f, ImGuiCond.Always, 0.5f, 0.5f);
        ImGui.setNextWindowSize(720 * ease, 480 * ease);

        if (ImGui.begin("##Main", ImGuiWindowFlags.NoDecoration)) {
            if (ease > 0.1f) {
                ImGui.beginChild("Sidebar", 180, 0, false);
                ImGui.textColored(pA, "T"); ImGui.sameLine(0, 0); ImGui.text("oba"); // T is primary color, oba is text color
                ImGui.spacing();
                for (Module.Category cat : Module.Category.values()) {
                    boolean sel = (selectedCategory == cat);
                    ImGui.pushStyleColor(ImGuiCol.Header, sel ? pA : 0);
                    ImGui.pushStyleColor(ImGuiCol.HeaderHovered, sel ? pA : 0);
                    ImGui.pushStyleColor(ImGuiCol.HeaderActive, sel ? pA : 0);

                    if (ImGui.selectable(cat.getDisplayName(), sel)) selectedCategory = cat;

                    ImGui.popStyleColor(3);
                    ImGui.spacing();
                }
                ImGui.endChild(); ImGui.sameLine();
                ImGui.beginChild("Content", 0, 0, false);
                for (Module m : ModuleManager.getInstance().getModulesByCategory(selectedCategory)) renderModule(m, pA, bA, tA);
                ImGui.endChild();
            }
        }
        ImGui.end();
        if (c != null) {
            ImGuiImpl.currentFontFamily = c.fontMode.getValue();
        }
        ImGui.popStyleColor(3); ImGui.popStyleVar(5);
    }

    private void renderModule(Module m, int p, int b, int t) {
        float sY = ImGui.getCursorScreenPosY();
        int color = m.isEnabled() ? p : (ColorUtil.lighten(b, 8) & 0xFFFFFFFF);

        ImGui.pushStyleColor(ImGuiCol.Button, color);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, color);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, color);

        ImGui.button("##" + m.getName(), ImGui.getContentRegionAvailX(), 30);
        if (ImGui.isItemClicked(0)) m.toggle();
        if (ImGui.isItemClicked(1)) { if (!expandedModules.remove(m)) expandedModules.add(m); }
        if (ImGui.isItemClicked(2)) bindingModule = (bindingModule == m) ? null : m;

        ImGui.getWindowDrawList().addText(ImGui.getItemRectMinX() + 10, sY + 7, t, m.getName());
        String bind = bindingModule == m ? "[...]" : (m.getKeyBind() != -1 ? "[" + KeyUtil.getKeyName(m.getKeyBind()) + "]" : "");
        if (!bind.isEmpty()) ImGui.getWindowDrawList().addText(ImGui.getItemRectMaxX() - ImGui.calcTextSize(bind).x - 10, sY + 7, t, bind);

        ImGui.popStyleColor(3);
        if (expandedModules.contains(m)) {
            ImGui.beginChild(m.getName() + "S", 0, 0, ImGuiChildFlags.AutoResizeY, ImGuiWindowFlags.NoScrollbar);
            settingsRenderer.renderSettings(m.getSettings(), p, b, t);
            ImGui.endChild();
        }
    }

    @Override public void close() { TobaConfig.save(); super.close(); }
}
