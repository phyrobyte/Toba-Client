/**
 * @author Fogma
 * @since 2026-02-03
 */
package dev.toba.client.screens.gui.clickgui.settings.renderers;
import dev.toba.client.features.settings.Module;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import java.util.HashSet;
import java.util.Set;

public class NumberRenderer {
    private static final Set<String> inputMode = new HashSet<>();

    public static void renderInt(Module.Setting<Integer> setting) {
        ImInt value = new ImInt(setting.getValue());
        boolean isInput = inputMode.contains(setting.getName());
        if (isInput) ImGui.setKeyboardFocusHere();
        if ((isInput ? ImGui.inputInt(setting.getName(), value, 0, 0, ImGuiInputTextFlags.EnterReturnsTrue)
                : ImGui.sliderInt(setting.getName(), value.getData(), (int) setting.getMin(), (int) setting.getMax()))) {
            setting.setValue(Math.max((int) setting.getMin(), Math.min((int) setting.getMax(), isInput ? value.get() : value.get())));
            inputMode.remove(setting.getName());
        }
        if (ImGui.isItemClicked(1)) { if (isInput) inputMode.remove(setting.getName()); else inputMode.add(setting.getName()); }
    }

    public static void renderFloat(Module.Setting<Float> setting) {
        ImFloat value = new ImFloat(setting.getValue());
        boolean isInput = inputMode.contains(setting.getName());
        if (isInput) ImGui.setKeyboardFocusHere();
        if ((isInput ? ImGui.inputFloat(setting.getName(), value, 0, 0, "%.2f", ImGuiInputTextFlags.EnterReturnsTrue)
                : ImGui.sliderFloat(setting.getName(), value.getData(), (float) setting.getMin(), (float) setting.getMax(), "%.2f"))) {
            setting.setValue(Math.max((float) setting.getMin(), Math.min((float) setting.getMax(), value.get())));
            inputMode.remove(setting.getName());
        }
        if (ImGui.isItemClicked(1)) { if (isInput) inputMode.remove(setting.getName()); else inputMode.add(setting.getName()); }
    }
}
