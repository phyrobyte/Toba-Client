package dev.toba.client.features.impl.module.combat;

import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.Module.Setting;
import dev.toba.client.features.settings.Module.Setting.RangeValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

import java.util.Random;

public class TriggerBotModule extends Module {


    public final Setting<RangeValue> attackDelay = addSetting(new Setting<>("attack delay", new RangeValue(500f, 520f), Setting.SettingType.MULTI_RANGE).range(10.0, 1000.0));

    private final Random random = new Random();
    private long nextAttackMs = 0L;
    private long fallStartMs = 0L;
    private boolean wasFalling = false;
    private long targetLockTime = 0L;
    private Entity lastSeenTarget = null;

    public TriggerBotModule() {
        super("TriggerBot", "attacks when players is on crosshair", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        nextAttackMs = 0L;
        fallStartMs = 0L;
        wasFalling = false;
        targetLockTime = 0L;
        lastSeenTarget = null;
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.interactionManager == null) return;


        if (mc.player.isUsingItem()) return;
        if (System.currentTimeMillis() < nextAttackMs) return;


        if (!(mc.targetedEntity instanceof PlayerEntity target)) {
            lastSeenTarget = null;
            return;
        }


        if (target != lastSeenTarget) {
            lastSeenTarget = target;
            targetLockTime = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - targetLockTime < (10 + random.nextInt(10))) return;


        double velocityY = mc.player.getVelocity().y;
        if (velocityY < 0 && !wasFalling) {
            fallStartMs = System.currentTimeMillis();
            wasFalling = true;
        } else if (velocityY >= 0) {
            wasFalling = false;
        }

        if (!mc.player.isOnGround()) {
            long fallDelay = 50 + random.nextInt(10);
            if (velocityY >= 0 || !wasFalling || (System.currentTimeMillis() - fallStartMs) < fallDelay) {
                return;
            }
        }


        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        nextAttackMs = System.currentTimeMillis() + (long) attackDelay.getValue().random();


        targetLockTime = System.currentTimeMillis();
    }
}