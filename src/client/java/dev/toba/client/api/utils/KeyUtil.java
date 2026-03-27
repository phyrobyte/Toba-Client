/**
 * @author Fogma
 * @2026-02-4
 */

package dev.toba.client.api.utils;

import dev.toba.client.features.settings.Module;
import imgui.ImGui;

public class KeyUtil {

    public static String getKeyName(int keyCode) {
        if (keyCode == -1) return "";

        int scancode = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(keyCode);
        String name = scancode != -1 ? org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, scancode) : null;
        if (name == null) {
            return switch (keyCode) {
                case 340 -> "LSHIFT";
                case 344 -> "RSHIFT";
                case 341 -> "LCTRL";
                case 345 -> "RCTRL";
                case 342 -> "LALT";
                case 346 -> "RALT";
                case 32 -> "SPACE";
                case 258 -> "TAB";
                case 280 -> "CAPS";
                case 257 -> "ENTER";
                default -> "KEY" + keyCode;
            };
        }
        return name.toUpperCase();
    }



    public static boolean handleBinding(Module module) {
        if (module == null) return true;

        for (int k = 32; k <= 348; k++) {
            if (ImGui.isKeyPressed(k)) {
                if (k == 256) { // escape (go out of gui)
                    return true;
                } else if (k == 259) { // backspace (unbind)
                    module.setKeyBind(-1);
                    return true;
                } else {
                    module.setKeyBind(k);
                    return true;
                }
            }
        }
        return false;
    }
}
