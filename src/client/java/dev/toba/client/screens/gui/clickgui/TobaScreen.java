/**
 * @author Fogma
 * @2026-02-3
 */
package dev.toba.client.screens.gui.clickgui;

import dev.toba.client.api.config.TobaConfig;
import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import dev.toba.client.features.impl.module.client.ClickGUI;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import dev.toba.client.api.utils.*;
import dev.toba.client.screens.gui.clickgui.settings.SettingsRenderer;
import imgui.*;
import imgui.flag.*;
import imgui.type.ImBoolean;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.*;

public class TobaScreen extends Screen implements RenderInterface {
    private final SettingsRenderer settingsRenderer = new SettingsRenderer();
    private Module bindingModule = null;
    private static final Set<Module> openSettings = new HashSet<>();

    public TobaScreen() { super(Text.literal("Toba")); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(ImGuiIO io) {
        ClickGUI c = ModuleManager.getInstance().getModule(ClickGUI.class);
        if (c != null) {
            ImGuiImpl.currentFontFamily = c.fontMode.getValue();
        }
        int p = ColorUtil.argbToAbgr(c != null ? c.primaryColor.getValue() : 0xFF5C6BC0), b = ColorUtil.argbToAbgr(c != null ? c.backgroundColor.getValue() : 0xFF1E1E2E), t = ColorUtil.argbToAbgr(c != null ? c.textColor.getValue() : 0xFFFFFFFF);
        if (bindingModule != null && KeyUtil.handleBinding(bindingModule)) bindingModule = null;
        applyStyle(p, b, t);
        float x = 50, y = 50;
        for (Module.Category cat : Module.Category.values()) {
            ImGui.setNextWindowPos(x, y, ImGuiCond.FirstUseEver);
            ImGui.setNextWindowSize(240, 0);
            if (ImGui.begin(cat.getDisplayName(), ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDocking)) {
                for (Module m : ModuleManager.getInstance().getModulesByCategory(cat)) renderModule(m, p, b, t);
            }
            ImGui.end();
            x += 260; if (x > 1000) { x = 50; y += 450; }
        }
        new HashSet<>(openSettings).forEach(m -> renderSettingsWindow(m, p, b, t));
        if (c != null) {
            ImGuiImpl.currentFontFamily = c.fontMode.getValue();
        }
        cleanupStyle();
    }
    private void renderModule(Module m, int p, int b, int t) {
        boolean so = m.isSettingsOnly();
        int bc = so ? ColorUtil.lighten(b, 15) : m.isEnabled() ? p : ColorUtil.lighten(b, 10);
        ImGui.pushStyleColor(ImGuiCol.Button, bc);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ColorUtil.lighten(bc, 8));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, bc);
        if (ImGui.button("##" + m.getName(), ImGui.getContentRegionAvailX(), 26)) {
            if (!so) m.toggle();
        }
        if (ImGui.isItemHovered() && m.getDescription() != null) ImGui.setTooltip(m.getDescription());
        float ty = ImGui.getItemRectMinY() + 5, lx = ImGui.getItemRectMinX() + 6;
        ImGui.getWindowDrawList().addText(lx, ty, t, m.getName());
        if (!so) {
            String bt = bindingModule == m ? "[...]" : m.getKeyBind() != -1 ? "[" + KeyUtil.getKeyName(m.getKeyBind()) + "]" : null;
            if (bt != null) ImGui.getWindowDrawList().addText(ImGui.getItemRectMaxX() - ImGui.calcTextSize(bt).x - 6, ty, t, bt);
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemClicked(1)) { if (openSettings.contains(m)) openSettings.remove(m); else if (!m.getSettings().isEmpty()) openSettings.add(m); }
        if (!so) {
            if (ImGui.isItemClicked(2)) bindingModule = (bindingModule == m) ? null : m;
        }
    }

    private void renderSettingsWindow(Module m, int p, int b, int t) {
        String title = m.getName() + " Settings";
        float maxW = ImGui.calcTextSize(title).x + 40;
        for (Module.Setting<?> s : m.getSettings()) {
            maxW = Math.max(maxW, ImGui.calcTextSize(s.getName()).x + 230);
        }
        ImGui.setNextWindowSize(maxW, 0);
        ImBoolean open = new ImBoolean(true);

        if (ImGui.begin(title, open, ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar | ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoDocking)) {
            settingsRenderer.renderSettings(m.getSettings(), p, b, t);
        }
        ImGui.end();
        if (!open.get()) openSettings.remove(m);
    }

    private void applyStyle(int p, int b, int t) {
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 4f);
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 2f);
        ImGui.pushStyleColor(ImGuiCol.WindowBg, b);
        ImGui.pushStyleColor(ImGuiCol.TitleBg, ColorUtil.lighten(b, 8));
        ImGui.pushStyleColor(ImGuiCol.TitleBgActive, p);
        ImGui.pushStyleColor(ImGuiCol.Border, ColorUtil.lighten(b, 25));
        ImGui.pushStyleColor(ImGuiCol.Text, t);
        int btn = ColorUtil.lighten(b, 10);
        ImGui.pushStyleColor(ImGuiCol.Button, btn);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, btn);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, btn);
    }

    private void cleanupStyle() { ImGui.popStyleColor(8); ImGui.popStyleVar(2); }
    @Override public void close() { TobaConfig.save(); super.close(); }
}
