package dev.toba.client.features.impl.module.macro.combat;

import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.utils.RotationUtil;
import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Mob Killer — automatically locates, paths to, and attacks mobs.
 * Supports targeting hostile, passive, or all mobs.
 *
 * Uses soft-aim: a tracked position that slowly drifts toward the actual
 * target, producing gradual human-like rotations instead of instant locks.
 * Aiming starts during PATHING so the player is already looking at the mob
 * well before arriving in attack range.
 */
public class MobKillerModule extends Module {

    private final Setting<String> targetMode;
    private final Setting<Integer> searchRadius;
    private final Setting<Float> attackReach;
    private final Setting<Integer> highlightColor;
    private final Setting<Boolean> sprint;
    private final Setting<Float> maxYDiff;

    private enum State { SEARCHING, PATHING, WALKING, ATTACKING }

    private State state = State.SEARCHING;
    private LivingEntity target = null;

    private int repathCooldown = 0;
    private static final int REPATH_COOLDOWN_TICKS = 20;

    private int pathFailures = 0;
    private static final int MAX_PATH_FAILURES = 2;

    private final Set<Integer> skippedEntities = new HashSet<>();
    private int walkFailureTicks = 0;
    private double lastWalkDist = Double.MAX_VALUE;

    // ── Soft-aim system ────────────────────────────────────────────
    // Instead of snapping the aim to the target every tick, we maintain
    // a "soft aim" position that slowly lerps toward the real target.
    // This naturally produces gradual rotations — the further the target
    // moves, the more the soft aim drifts behind it, creating realistic
    // delay. The lerp factor controls how fast the aim catches up:
    //   0.06 = very slow/lazy,  0.15 = moderate,  0.3 = responsive
    private Vec3d softAimPos = null;
    private static final double SOFT_AIM_LERP_APPROACH = 0.18; // while pathing (moderate drift)
    private static final double SOFT_AIM_LERP_COMBAT = 0.35;   // while attacking (responsive)

    // ── Auto-detect mob type ────────────────────────────────────
    // In "Auto" mode, we scan nearby mobs, group by mob name (e.g., "Crypt Ghoul"),
    // not just entity class. This prevents mixing different SkyBlock mob types
    // that share the same base class (e.g., all zombies = ZombieEntity).
    private String lockedMobGroup = null;
    private int autoRescanCooldown = 0;
    private static final int AUTO_RESCAN_INTERVAL = 60; // re-evaluate every 3 seconds

    // Aim jitter for human-like imprecision
    private final Random jitterRng = new Random();
    private double jitterOffsetX = 0;
    private double jitterOffsetY = 0;
    private double jitterOffsetZ = 0;
    private int jitterUpdateTicks = 0;

    public MobKillerModule() {
        super("Mob Killer", "Automatically kills nearby mobs", Category.MACRO);
        setIsScript(true);

        targetMode = addSetting(new Setting<>("Target", "Auto", Setting.SettingType.MODE));
        targetMode.modes("Auto", "Hostile", "Passive", "All");

        searchRadius = addSetting(new Setting<>("Search Radius", 32, Setting.SettingType.INTEGER));
        searchRadius.range(8, 64);

        attackReach = addSetting(new Setting<>("Attack Reach", 3.5f, Setting.SettingType.FLOAT));
        attackReach.range(2.0, 5.0);

        highlightColor = addSetting(new Setting<>("Highlight Color", 0xFFFF0000, Setting.SettingType.COLOR));

        sprint = addSetting(new Setting<>("Sprint", true, Setting.SettingType.BOOLEAN));

        maxYDiff = addSetting(new Setting<>("Max Y Difference", 10.0f, Setting.SettingType.FLOAT));
        maxYDiff.range(3.0, 30.0);
    }

    /**
     * Called every frame for ESP rendering.
     */
    public void onFrameUpdate(float tickDelta) {
        if (!isEnabled() || target == null || !target.isAlive()) return;
        float[] c = argbToFloats(highlightColor.getValue());
        ESPRenderer.getInstance().addEntity(target, tickDelta, c[0], c[1], c[2], 0.6f);
    }

    @Override
    public void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (repathCooldown > 0) repathCooldown--;
        if (autoRescanCooldown > 0) autoRescanCooldown--;
        updateJitter();

        switch (state) {
            case SEARCHING -> tickSearching(client);
            case PATHING -> tickPathing(client);
            case WALKING -> tickWalking(client);
            case ATTACKING -> tickAttacking(client);
        }
    }

    // ── Soft-aim tracking ──────────────────────────────────────────

    /**
     * Update the soft aim position, lerping toward the real target.
     * @param lerpFactor how fast to catch up (0.06 = lazy, 0.12 = responsive)
     */
    private void updateSoftAim(LivingEntity entity, double lerpFactor) {
        Vec3d realPos = getRealAimPos(entity);
        if (softAimPos == null) {
            // First frame — initialize near the target but offset toward the player's
            // current look direction so the rotation starts naturally.
            softAimPos = realPos;
        }
        // Lerp each axis independently
        softAimPos = new Vec3d(
                softAimPos.x + (realPos.x - softAimPos.x) * lerpFactor,
                softAimPos.y + (realPos.y - softAimPos.y) * lerpFactor,
                softAimPos.z + (realPos.z - softAimPos.z) * lerpFactor
        );
    }

    /** The actual aim point on the entity (chest level + jitter). */
    private Vec3d getRealAimPos(LivingEntity entity) {
        return entity.getSyncedPos().add(
                jitterOffsetX,
                entity.getHeight() * 0.35 + jitterOffsetY,
                jitterOffsetZ
        );
    }

    // ── Aim jitter ─────────────────────────────────────────────────

    private void updateJitter() {
        jitterUpdateTicks++;
        if (jitterUpdateTicks >= 8 + jitterRng.nextInt(8)) {
            jitterUpdateTicks = 0;
            double targetJitterX = (jitterRng.nextDouble() - 0.5) * 0.3;
            double targetJitterY = (jitterRng.nextDouble() - 0.5) * 0.25;
            double targetJitterZ = (jitterRng.nextDouble() - 0.5) * 0.3;
            jitterOffsetX += (targetJitterX - jitterOffsetX) * 0.4;
            jitterOffsetY += (targetJitterY - jitterOffsetY) * 0.4;
            jitterOffsetZ += (targetJitterZ - jitterOffsetZ) * 0.4;
        }
    }

    // ── Target filtering ───────────────────────────────────────────

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity instanceof PlayerEntity) return false;
        if (!living.isAlive()) return false;
        if (skippedEntities.contains(entity.getId())) return false;

        String mode = targetMode.getValue();
        return switch (mode) {
            case "Auto" -> {
                // Must be a hostile or passive mob
                if (!(entity instanceof HostileEntity) && !(entity instanceof PassiveEntity)) yield false;
                // If we have a locked group, only accept mobs with the same group key
                yield lockedMobGroup == null || lockedMobGroup.equals(getMobGroupKey(entity));
            }
            case "Hostile" -> entity instanceof HostileEntity;
            case "Passive" -> entity instanceof PassiveEntity;
            case "All" -> entity instanceof HostileEntity || entity instanceof PassiveEntity;
            default -> false;
        };
    }

    /**
     * Get a grouping key for a mob. Named mobs are grouped by their display name
     * (stripped of formatting codes and health info), so "Crypt Ghoul" zombies
     * won't be mixed with regular unnamed zombies.
     */
    private String getMobGroupKey(Entity entity) {
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            String name = entity.getCustomName().getString()
                    .replaceAll("§.", "") // strip formatting codes
                    .trim();
            // Remove trailing health/heart info (digits, commas, hearts, spaces)
            name = name.replaceAll("[\\s]*[\\d,./❤♥]+[\\s]*$", "").trim();
            if (!name.isEmpty()) return name;
        }
        return entity.getClass().getSimpleName();
    }

    /**
     * Scan nearby mobs, group by entity class, and lock onto the most common type.
     * Returns true if a mob class was successfully detected.
     */
    private boolean autoDetectMobType(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        double radius = searchRadius.getValue();
        double radiusSq = radius * radius;
        float yDiffLimit = maxYDiff.getValue();

        Map<String, Integer> groupCounts = new HashMap<>();

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity) continue;
            if (!((LivingEntity) entity).isAlive()) continue;
            if (!(entity instanceof HostileEntity) && !(entity instanceof PassiveEntity)) continue;
            if (skippedEntities.contains(entity.getId())) continue;

            double dx = player.getX() - entity.getX();
            double dy = player.getY() - entity.getY();
            double dz = player.getZ() - entity.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;
            if (Math.abs(dy) > yDiffLimit) continue;

            String groupKey = getMobGroupKey(entity);
            groupCounts.merge(groupKey, 1, Integer::sum);
        }

        if (groupCounts.isEmpty()) {
            lockedMobGroup = null;
            return false;
        }

        // Pick the group with the most entities nearby
        String bestGroup = null;
        int bestCount = 0;
        for (var entry : groupCounts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestGroup = entry.getKey();
            }
        }

        lockedMobGroup = bestGroup;
        autoRescanCooldown = AUTO_RESCAN_INTERVAL;
        return true;
    }

    // ── SEARCHING ──────────────────────────────────────────────────

    private void tickSearching(MinecraftClient client) {
        // In Auto mode, (re)detect the dominant mob type before searching
        if ("Auto".equals(targetMode.getValue()) && (lockedMobGroup == null || autoRescanCooldown <= 0)) {
            autoDetectMobType(client);
        }

        LivingEntity best = findBestTarget(client);
        if (best == null) {
            // No targets found — if Auto, clear the lock so we re-scan next tick
            if ("Auto".equals(targetMode.getValue())) lockedMobGroup = null;
            return;
        }

        target = best;
        softAimPos = null; // reset soft aim for new target
        pathFailures = 0;
        walkFailureTicks = 0;
        lastWalkDist = Double.MAX_VALUE;

        double dist = client.player.distanceTo(best);
        if (dist <= attackReach.getValue()) {
            state = State.ATTACKING;
        } else {
            startPathToTarget();
        }
    }

    private LivingEntity findBestTarget(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        double radius = searchRadius.getValue();
        double radiusSq = radius * radius;
        float yDiffLimit = maxYDiff.getValue();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (!isValidTarget(entity)) continue;
            LivingEntity mob = (LivingEntity) entity;

            double dx = player.getX() - mob.getX();
            double dy = player.getY() - mob.getY();
            double dz = player.getZ() - mob.getZ();
            double horizDistSq = dx * dx + dz * dz;

            if (horizDistSq > radiusSq) continue;

            double absDy = Math.abs(dy);
            if (absDy > yDiffLimit) continue;

            double score = Math.sqrt(horizDistSq) + absDy * 3.0;

            if (score < bestScore) {
                best = mob;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean retargetIfBetter(MinecraftClient client) {
        LivingEntity best = findBestTarget(client);
        if (best == null || best == target) return false;

        ClientPlayerEntity player = client.player;
        double bestScore = scoreMob(player, best);
        double currentScore = (target != null && target.isAlive()) ? scoreMob(player, target) : Double.MAX_VALUE;

        if (bestScore < currentScore - 5.0) {
            stopPathing();
            target = best;
            softAimPos = null; // reset for new target
            pathFailures = 0;
            walkFailureTicks = 0;
            lastWalkDist = Double.MAX_VALUE;
            return true;
        }
        return false;
    }

    private double scoreMob(ClientPlayerEntity player, LivingEntity mob) {
        double dx = player.getX() - mob.getX();
        double dy = player.getY() - mob.getY();
        double dz = player.getZ() - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(dy) * 3.0;
    }

    // ── PATHING ────────────────────────────────────────────────────

    private void tickPathing(MinecraftClient client) {
        if (target == null || !target.isAlive()) {
            stopPathing();
            target = null;
            state = State.SEARCHING;
            return;
        }

        if (retargetIfBetter(client)) {
            startPathToTarget();
            return;
        }

        double dist = client.player.distanceTo(target);

        if (dist <= attackReach.getValue()) {
            stopPathing();
            state = State.ATTACKING;
            return;
        }

        if (sprint.getValue()) {
            client.player.setSprinting(true);
        }

        // Start aiming at the target while still pathing (slow drift).
        // Clamp the pitch so the player doesn't look straight up/down when the
        // target is nearly overhead — that would break pathfollowing because
        // forward movement becomes unpredictable at extreme pitch angles.
        updateSoftAim(target, SOFT_AIM_LERP_APPROACH);

        Vec3d eyePos = client.player.getEyePos();
        double aimDy = softAimPos.y - eyePos.y;
        double aimHDist = Math.sqrt(
                (softAimPos.x - eyePos.x) * (softAimPos.x - eyePos.x) +
                (softAimPos.z - eyePos.z) * (softAimPos.z - eyePos.z));
        // Max pitch ~30° (tan(30°) ≈ 0.577). If the aim point is steeper,
        // flatten the Y so the player keeps their head level enough to walk.
        double maxPitchTan = 0.577;
        double clampedAimY = softAimPos.y;
        if (aimHDist > 0.5) {
            double maxDy = aimHDist * maxPitchTan;
            clampedAimY = eyePos.y + net.minecraft.util.math.MathHelper.clamp(
                    (float) aimDy, (float) -maxDy, (float) maxDy);
        } else {
            // Target nearly directly above/below — don't adjust pitch at all
            clampedAimY = eyePos.y;
        }
        Vec3d pathingAimPos = new Vec3d(softAimPos.x, clampedAimY, softAimPos.z);
        float[] aimAngles = RotationUtil.getRotations(pathingAimPos);
        RotationUtil.setTarget(aimAngles[0], aimAngles[1], 0.4f);

        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null && !pf.isNavigating() && repathCooldown <= 0) {
            pathFailures++;

            if (pathFailures >= MAX_PATH_FAILURES) {
                // Target is unreachable — skip it and find a new one
                skippedEntities.add(target.getId());
                target = null;
                softAimPos = null;
                state = State.SEARCHING;
                RotationUtil.clearTarget();
                return;
            }

            repathCooldown = REPATH_COOLDOWN_TICKS;
            pf.startPathTo(target.getBlockPos(), () -> {
                if (state == State.PATHING) {
                    pathFailures = 0;
                    state = State.ATTACKING;
                }
            });
        }
    }

    // ── WALKING (direct approach fallback) ─────────────────────────

    private void tickWalking(MinecraftClient client) {
        if (target == null || !target.isAlive()) {
            releaseMovement(client);
            target = null;
            state = State.SEARCHING;
            return;
        }

        if (retargetIfBetter(client)) {
            releaseMovement(client);
            startPathToTarget();
            return;
        }

        double dist = client.player.distanceTo(target);

        if (dist <= attackReach.getValue()) {
            releaseMovement(client);
            state = State.ATTACKING;
            return;
        }

        walkFailureTicks++;
        if (walkFailureTicks % 40 == 0) {
            if (dist >= lastWalkDist - 1.0) {
                releaseMovement(client);
                skippedEntities.add(target.getId());
                target = null;
                state = State.SEARCHING;
                return;
            }
            lastWalkDist = dist;
        }

        updateSoftAim(target, SOFT_AIM_LERP_COMBAT);
        float[] walkAngles = RotationUtil.getRotations(softAimPos);
        RotationUtil.setTarget(walkAngles[0], walkAngles[1], 0.4f);

        float yawDiff = Math.abs(MathHelper.wrapDegrees(walkAngles[0] - client.player.getYaw()));

        if (yawDiff < 45f) {
            client.options.forwardKey.setPressed(true);
            client.options.sprintKey.setPressed(sprint.getValue());
        } else {
            client.options.forwardKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
        }

        if (client.player.horizontalCollision && client.player.isOnGround()) {
            client.options.jumpKey.setPressed(true);
        } else {
            client.options.jumpKey.setPressed(false);
        }
    }

    // ── ATTACKING ──────────────────────────────────────────────────

    private void tickAttacking(MinecraftClient client) {
        if (target == null || !target.isAlive()) {
            releaseAttack(client);
            target = null;
            state = State.SEARCHING;
            return;
        }

        if (retargetIfBetter(client)) {
            releaseAttack(client);
            double dist2 = client.player.distanceTo(target);
            if (dist2 <= attackReach.getValue()) {
                state = State.ATTACKING;
            } else {
                startPathToTarget();
            }
            return;
        }

        double dist = client.player.distanceTo(target);

        if (dist > attackReach.getValue()) {
            releaseAttack(client);
            // Target moved out of range — try pathing again or skip
            if (pathFailures >= MAX_PATH_FAILURES) {
                skippedEntities.add(target.getId());
                target = null;
                softAimPos = null;
                state = State.SEARCHING;
            } else {
                startPathToTarget();
            }
            return;
        }

        updateSoftAim(target, SOFT_AIM_LERP_COMBAT);
        float[] atkAngles = RotationUtil.getRotations(softAimPos);
        RotationUtil.setTarget(atkAngles[0], atkAngles[1], 0.4f);

        float yawDiff = Math.abs(MathHelper.wrapDegrees(atkAngles[0] - client.player.getYaw()));
        float pitchDiff = Math.abs(atkAngles[1] - client.player.getPitch());

        if (yawDiff < 15f && pitchDiff < 15f) {
            client.options.attackKey.setPressed(true);
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        } else {
            client.options.attackKey.setPressed(false);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private void startPathToTarget() {
        if (target == null) return;
        state = State.PATHING;
        repathCooldown = REPATH_COOLDOWN_TICKS;
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) {
            pf.startPathTo(target.getBlockPos(), () -> {
                if (state == State.PATHING) {
                    pathFailures = 0;
                    state = State.ATTACKING;
                }
            });
        }
    }

    private void stopPathing() {
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) {
            pf.stop();
        }
    }

    private void releaseMovement(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        RotationUtil.clearTarget();
    }

    private void releaseAttack(MinecraftClient client) {
        client.options.attackKey.setPressed(false);
        RotationUtil.clearTarget();
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            releaseAttack(client);
            releaseMovement(client);
        }
        stopPathing();
        target = null;
        softAimPos = null;
        lockedMobGroup = null;
        autoRescanCooldown = 0;
        state = State.SEARCHING;
        repathCooldown = 0;
        pathFailures = 0;
        walkFailureTicks = 0;
        lastWalkDist = Double.MAX_VALUE;
        skippedEntities.clear();
    }

    private static float[] argbToFloats(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }
}
