/**
 * @author Fogma
 * @since 2026-02-21
 */
package dev.toba.client.features.impl.module.combat;

import dev.toba.client.api.utils.RotationUtil;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.Module.Setting;
import dev.toba.client.features.settings.Module.Setting.RangeValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Comparator;
import java.util.Random;
import java.util.UUID;
import java.util.stream.StreamSupport;

public class AimAssistModule extends Module {

    public final Setting<Float>      range = addSetting(new Setting<>("Range", 6.0f,                       Setting.SettingType.FLOAT).range(1.0, 20.0));
    public final Setting<Float>      fov   = addSetting(new Setting<>("FOV",   90.0f,                      Setting.SettingType.FLOAT).range(10.0, 360.0));
    public final Setting<RangeValue> speed = addSetting(new Setting<>("Speed", new RangeValue(0.1f, 0.3f), Setting.SettingType.MULTI_RANGE).range(0.01, 1.0));

    private final Random random    = new Random();
    private UUID  lockedTarget     = null;
    private float lockedSpeed      = 0.2f;
    private long  tickCount        = 0;

    public AimAssistModule() {
        super("AimAssist", "looks at nearest player", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        lockedTarget = null;
        tickCount    = 0;
        RotationUtil.clearTarget();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        tickCount++;

        StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(e -> e instanceof PlayerEntity && e != mc.player)
                .filter(e -> mc.player.distanceTo(e) <= range.getValue())
                .filter(e -> RotationUtil.inFOV(e, fov.getValue()))
                .min(Comparator.comparingDouble(mc.player::distanceTo))
                .ifPresentOrElse(
                        target -> {
                            // Re-roll speed only when switching targets — keeps delta ratio constant within a lock
                            if (!target.getUuid().equals(lockedTarget)) {
                                lockedTarget = target.getUuid();
                                lockedSpeed  = speed.getValue().random();
                            }

                            float[] r = RotationUtil.getRotations(target.getEyePos());





                            float driftYaw   = (float) Math.sin(tickCount * 0.07)  * 0.08f;
                            float driftPitch = (float) Math.sin(tickCount * 0.05 + 1.2) * 0.05f;

                            RotationUtil.setTarget(r[0] + driftYaw, r[1] + driftPitch, lockedSpeed);
                        },
                        () -> {
                            lockedTarget = null;
                            RotationUtil.clearTarget();
                        }
                );
    }
}