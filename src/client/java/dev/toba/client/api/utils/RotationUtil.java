/**
 * @author Fogma
 * @2026-02-22
 */
package dev.toba.client.api.utils;

import dev.toba.client.mixin.EntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class RotationUtil {

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static float targetYaw;
    private static float targetPitch;

    private static float smoothing = 0.3f;

    private static boolean active = false;

    public static void setTarget(float yaw, float pitch, float smoothing) {
        RotationUtil.targetYaw   = yaw;
        RotationUtil.targetPitch = pitch;
        RotationUtil.smoothing   = MathHelper.clamp(smoothing, 0.01f, 1.0f);
        active = true;
    }

    public static void clearTarget() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static void onFrame(float frameDelta) {
        if (!active || mc.player == null) return;

        float currentYaw   = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();


        float yawDelta   = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float alpha = 1f - (float) Math.pow(1f - smoothing, frameDelta);
        float smoothedYawDelta   = yawDelta * alpha;
        float smoothedPitchDelta = pitchDelta * alpha;

        float sens = mc.options.getMouseSensitivity().getValue().floatValue();
        float f = sens * 0.6f + 0.2f;
        float gcd = f * f * f * 8.0f * 0.15f;


        int deltaX = Math.round(smoothedYawDelta / gcd);
        int deltaY = Math.round(smoothedPitchDelta / gcd);


        float finalYawDelta   = deltaX * gcd;
        float finalPitchDelta = deltaY * gcd;


        if (deltaX == 0 && deltaY == 0 && Math.abs(yawDelta) < 1.0f && Math.abs(pitchDelta) < 1.0f) {
            clearTarget();
            return;
        }


        float newYaw   = currentYaw + finalYawDelta;
        float newPitch = MathHelper.clamp(currentPitch + finalPitchDelta, -90f, 90f);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
        syncPrev();
    }

    private static void syncPrev() {
        if (mc.player == null) return;
        EntityAccessor acc = (EntityAccessor) mc.player;
        acc.setLastYaw(mc.player.getYaw());
        acc.setLastPitch(mc.player.getPitch());
    }

    public static float[] getRotations(Vec3d target) {
        if (mc.player == null) return new float[]{0f, 0f};
        Vec3d eyes = mc.player.getEyePos();
        double dx    = target.x - eyes.x;
        double dy    = target.y - eyes.y;
        double dz    = target.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw    = (float)  Math.toDegrees(Math.atan2(-dx, dz));
        float pitch  = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{ yaw, pitch };
    }

    public static float[] getRotations(Entity entity) {
        return getRotations(entity.getEyePos());
    }

    public static boolean inFOV(Entity entity, float fov) {
        if (mc.player == null) return false;
        Vec3d toTarget = entity.getEyePos().subtract(mc.player.getEyePos()).normalize();
        Vec3d look     = mc.player.getRotationVec(1.0f);
        double angle   = Math.toDegrees(Math.acos(MathHelper.clamp(toTarget.dotProduct(look), -1.0, 1.0)));
        return angle <= fov / 2.0;
    }
}