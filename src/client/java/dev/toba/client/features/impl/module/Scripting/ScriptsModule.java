/**
 * @author Fogma
 * @since 2026-03-01
 * no1 touch this either except Fogma
 */
package dev.toba.client.features.impl.module.Scripting;

import dev.toba.client.api.script.LuaScriptLoader;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.settings.Module;

import java.awt.Desktop;

public class ScriptsModule extends Module {

    public ScriptsModule() {
        super("ScriptsFolder", "Opens the Lua scripts directory", Category.SCRIPT);
        setSettingsOnly(true);

        addSetting(new Setting<>("open folder", null, Setting.SettingType.BUTTON)
                .callback(() -> {
                    try {
                        Desktop.getDesktop().open(LuaScriptLoader.SCRIPTS_DIR);
                    } catch (Exception e) {
                        TobaChat.error("Could not open scripts folder.");
                    }
                }));

        addSetting(new Setting<>("refresh/reload scripts", null, Setting.SettingType.BUTTON)
                .callback(() -> {
                    try {
                        LuaScriptLoader.reload();
                        TobaChat.info("it appears scripts got loaded successfully");
                    } catch (Exception e) {
                        TobaChat.error("failed to reload scripts, reason being: " + e.getMessage());
                    }
                }));

        super.setEnabled(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) return;
        super.setEnabled(true);
    }

    @Override
    public void toggle() {}

    @Override
    protected void onEnable() {}

    @Override
    protected void onDisable() {}
}
