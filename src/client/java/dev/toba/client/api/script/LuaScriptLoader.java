/**
 * @author Fogma
 * @since 2026-03-01
 * no1 touch this either except Fogma
 */
package dev.toba.client.api.script;

import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.settings.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.file.Files;

public class LuaScriptLoader {

    public static final File SCRIPTS_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("toba/scripts").toFile();

    public static void init() {
        try {
            Files.createDirectories(SCRIPTS_DIR.toPath());
        } catch (Exception e) {
            TobaChat.error("failed to create script directory: " + e.getMessage());
            return;
        }

        File[] files = SCRIPTS_DIR.listFiles((d, n) -> n.endsWith(".lua"));
        if (files == null || files.length == 0) return;

        for (File f : files) {
            try {
                LuaScriptModule mod = LuaScriptModule.load(f);
                if (mod != null) ModuleManager.getInstance().register(mod);
            } catch (Exception e) {
                System.err.println("[Toba Scripts] failed to load " + f.getName() + ": " + e.getMessage());
                TobaChat.error("failed to load " + f.getName() + ": " + e.getMessage());
            }
        }
    }

    public static void reload() {
        // Disable old script modules first so onDisable() runs and ImGui overlays are unregistered
        for (var m : ModuleManager.getInstance().getModules()) {
            if (m instanceof LuaScriptModule && m.isEnabled()) {
                m.setEnabled(false);
            }
        }
        ModuleManager.getInstance().getModules().removeIf(m -> m instanceof LuaScriptModule);

        File[] files = SCRIPTS_DIR.listFiles((d, n) -> n.endsWith(".lua"));
        if (files == null || files.length == 0) return;

        for (File f : files) {
            try {
                LuaScriptModule mod = LuaScriptModule.load(f);
                if (mod != null) ModuleManager.getInstance().register(mod);
            } catch (Exception e) {
                System.err.println("[Toba Scripts] failed to load " + f.getName() + ": " + e.getMessage());
                TobaChat.error("failed to load " + f.getName() + ": " + e.getMessage());
            }
        }
    }
}