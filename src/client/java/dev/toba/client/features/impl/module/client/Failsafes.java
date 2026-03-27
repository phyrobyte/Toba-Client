/**
 * @author Fogma
 * @since 2026-02-19
 */


package dev.toba.client.features.impl.module.client;

import dev.toba.client.features.settings.Module;

public class Failsafes extends Module {
    public final Setting<String> reaction;
    public final Setting<Boolean> failsafeTypes;
    public final Setting<Boolean> tpFailsafe;
    public final Setting<Boolean> rotationFailsafe;
    public final Setting<Boolean> bpsFailsafe;
    public final Setting<Boolean> badEffects;
    public final Setting<Boolean> bedrockCage;
    public final Setting<Boolean> dirt;

    public Failsafes() {
        super("Failsafes", "Settings for how & what to react to during macro checks", Category.CLIENT);

        reaction = addSetting(new Setting<>("Reaction", "react", Setting.SettingType.MODE)
                .modes("none/off", "react"));

        failsafeTypes = addSetting(new Setting<>("Failsafe Types", true, Setting.SettingType.SUB_CONFIG));
        tpFailsafe = new Setting<>("TP Failsafe", true, Setting.SettingType.BOOLEAN);
        rotationFailsafe = new Setting<>("Rotation Failsafe", true, Setting.SettingType.BOOLEAN);
        bpsFailsafe = new Setting<>("BPS Failsafe", true, Setting.SettingType.BOOLEAN);
        badEffects = new Setting<>("Bad effects Failsafe", true, Setting.SettingType.BOOLEAN);
        bedrockCage = new Setting<>("Bedrock cage Failsafe", true, Setting.SettingType.BOOLEAN);
        dirt = new Setting<>("Dirt Failsafe", true, Setting.SettingType.BOOLEAN);

        failsafeTypes.child(tpFailsafe);
        failsafeTypes.child(rotationFailsafe);
        failsafeTypes.child(bpsFailsafe);
        failsafeTypes.child(badEffects);
        failsafeTypes.child(bedrockCage);
        failsafeTypes.child(dirt);

        // Force enable on construction
        super.setEnabled(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        // always enabled
        if (!enabled) return;
        super.setEnabled(true);
    }

    @Override
    public void toggle() {
        // toggling off off?
    }
}