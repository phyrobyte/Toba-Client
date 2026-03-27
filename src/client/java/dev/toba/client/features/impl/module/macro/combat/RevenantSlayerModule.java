package dev.toba.client.features.impl.module.macro.combat;

import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.utils.RotationUtil;
import dev.toba.client.api.utils.AutoHeal;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Revenant Slayer — automated Revenant Horror slayer macro for Hypixel SkyBlock.
 *
 * Flow:
 *   1. Kill Crypt Ghouls (ZombieEntity) to fill the slayer XP bar
 *   2. Detect boss spawn (Revenant Horror T1-T4, Atoned Horror T5)
 *   3. Path to boss and left-click kill it
 *   4. T5: avoid RED_TERRACOTTA danger zones from TNT rain
 *   5. Loop back to step 1
 */
public class RevenantSlayerModule extends Module {

    // ── Settings ────────────────────────────────────────────────
    private final Setting<Integer> searchRadius;
    private final Setting<Float> attackReach;
    private final Setting<Float> healThreshold;
    private final Setting<Boolean> sprint;
    private final Setting<Float> maxYDiff;
    private final Setting<Integer> mobHighlightColor;
    private final Setting<Integer> bossHighlightColor;

    // ── State machine ──────────────────────────────────────────
    private enum State { KILLING_MOBS, BOSS_DETECTED, FIGHTING_BOSS, BOSS_DEAD }
    private enum MobSubState { SEARCHING, PATHING, WALKING, ATTACKING }

    private State state = State.KILLING_MOBS;
    private MobSubState mobSubState = MobSubState.SEARCHING;

    // ── Targets ────────────────────────────────────────────────
    private LivingEntity mobTarget = null;
    private LivingEntity bossTarget = null;

    // ── Auto-healing ───────────────────────────────────────────
    private final AutoHeal autoHeal = new AutoHeal();

    // ── Pathfinding ────────────────────────────────────────────
    private int repathCooldown = 0;
    private static final int REPATH_COOLDOWN_TICKS = 40;
    private int pathFailures = 0;
    private static final int MAX_PATH_FAILURES = 2;

    // ── Mob killing state ──────────────────────────────────────
    private final Set<Integer> skippedEntities = new HashSet<>();
    private int walkFailureTicks = 0;
    private double lastWalkDist = Double.MAX_VALUE;

    // ── Soft-aim ───────────────────────────────────────────────
    private Vec3d softAimPos = null;
    private static final double SOFT_AIM_LERP_APPROACH = 0.18;
    private static final double SOFT_AIM_LERP_COMBAT = 0.35;

    // ── Aim jitter ─────────────────────────────────────────────
    private final Random jitterRng = new Random();
    private double jitterOffsetX = 0, jitterOffsetY = 0, jitterOffsetZ = 0;
    private int jitterUpdateTicks = 0;

    // ── Boss dead cooldown ─────────────────────────────────────
    private int bossDeadCooldown = 0;
    private static final int BOSS_DEAD_COOLDOWN_TICKS = 40;

    // ── Combat movement (random strafing around boss) ─────────
    private int combatMoveTimer = 0;
    private int combatMoveDirection = 0; // 0-5: various directions

    // ── T5 TNT avoidance ───────────────────────────────────────
    private boolean isT5Boss = false; // auto-detected from boss name
    private final Set<BlockPos> dangerZones = new HashSet<>();
    private int dangerScanCooldown = 0;
    private static final int DANGER_SCAN_INTERVAL = 5;
    private static final int DANGER_SCAN_RADIUS = 8;
    private boolean tntEvading = false;

    public RevenantSlayerModule() {
        super("Revenant Slayer", "Automated Revenant Horror slayer macro", Category.MACRO);
        setIsScript(true);

        searchRadius = addSetting(new Setting<>("Search Radius", 32, Setting.SettingType.INTEGER));
        searchRadius.range(8, 64);

        attackReach = addSetting(new Setting<>("Attack Reach", 3.5f, Setting.SettingType.FLOAT));
        attackReach.range(2.0, 5.0);

        healThreshold = addSetting(new Setting<>("Heal Threshold", 0.30f, Setting.SettingType.FLOAT));
        healThreshold.range(0.05, 0.50);

        sprint = addSetting(new Setting<>("Sprint", true, Setting.SettingType.BOOLEAN));

        maxYDiff = addSetting(new Setting<>("Max Y Difference", 10.0f, Setting.SettingType.FLOAT));
        maxYDiff.range(3.0, 30.0);

        Setting<Boolean> colorSettings = addSetting(new Setting<>("Colors", false, Setting.SettingType.SUB_CONFIG));
        mobHighlightColor = new Setting<>("Mob Highlight", 0xFFFF0000, Setting.SettingType.COLOR);
        bossHighlightColor = new Setting<>("Boss Highlight", 0xFFAA00FF, Setting.SettingType.COLOR);
        colorSettings.child(mobHighlightColor).child(bossHighlightColor);
    }

    // ── ESP rendering ──────────────────────────────────────────

    public void onFrameUpdate(float tickDelta) {
        if (!isEnabled()) return;

        if (mobTarget != null && mobTarget.isAlive()) {
            float[] c = argbToFloats(mobHighlightColor.getValue());
            ESPRenderer.getInstance().addEntity(mobTarget, tickDelta, c[0], c[1], c[2], 0.6f);
        }

        if (bossTarget != null && bossTarget.isAlive()) {
            float[] c = argbToFloats(bossHighlightColor.getValue());
            ESPRenderer.getInstance().addEntity(bossTarget, tickDelta, c[0], c[1], c[2], 0.8f);
        }
    }

    // ── Main tick ──────────────────────────────────────────────

    @Override
    public void onTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (repathCooldown > 0) repathCooldown--;
        updateJitter();

        // Always check for boss during mob-killing phase
        if (state == State.KILLING_MOBS) {
            LivingEntity boss = findBoss(client);
            if (boss != null) {
                bossTarget = boss;
                stopMobKilling(client);
                TobaChat.send("Boss spawned! Engaging...");
                state = State.BOSS_DETECTED;
                softAimPos = null;
                pathFailures = 0;
                startPathToBoss();
                return;
            }
        }

        // AutoHeal during combat states
        if (state == State.KILLING_MOBS && mobSubState == MobSubState.ATTACKING
                || state == State.FIGHTING_BOSS) {
            autoHeal.tick(healThreshold.getValue());
        }

        switch (state) {
            case KILLING_MOBS -> tickKillingMobs(client);
            case BOSS_DETECTED -> tickBossDetected(client);
            case FIGHTING_BOSS -> tickFightingBoss(client);
            case BOSS_DEAD -> tickBossDead(client);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  KILLING_MOBS — find and kill crypt ghouls (ZombieEntity)
    // ════════════════════════════════════════════════════════════

    private void tickKillingMobs(MinecraftClient client) {
        switch (mobSubState) {
            case SEARCHING -> tickMobSearching(client);
            case PATHING -> tickMobPathing(client);
            case WALKING -> tickMobWalking(client);
            case ATTACKING -> tickMobAttacking(client);
        }
    }

    private void tickMobSearching(MinecraftClient client) {
        LivingEntity best = findBestMob(client);
        if (best == null) return;

        mobTarget = best;
        softAimPos = null;
        pathFailures = 0;
        walkFailureTicks = 0;
        lastWalkDist = Double.MAX_VALUE;

        double dist = client.player.distanceTo(best);
        if (dist <= attackReach.getValue()) {
            mobSubState = MobSubState.ATTACKING;
        } else {
            startPathToMob();
        }
    }

    private void tickMobPathing(MinecraftClient client) {
        if (mobTarget == null || !mobTarget.isAlive()) {
            stopPathing();
            mobTarget = null;
            mobSubState = MobSubState.SEARCHING;
            return;
        }

        double dist = client.player.distanceTo(mobTarget);
        if (dist <= attackReach.getValue()) {
            stopPathing();
            mobSubState = MobSubState.ATTACKING;
            return;
        }

        if (sprint.getValue()) client.player.setSprinting(true);

        // Soft-aim while pathing (clamped pitch like MobKiller)
        updateSoftAim(mobTarget, SOFT_AIM_LERP_APPROACH);
        Vec3d clampedAim = clampPitchForPathing(client.player, softAimPos);
        float[] pathAngles = RotationUtil.getRotations(clampedAim);
        RotationUtil.setTarget(pathAngles[0], pathAngles[1], 0.4f);

        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null && !pf.isNavigating() && repathCooldown <= 0) {
            pathFailures++;
            if (pathFailures >= MAX_PATH_FAILURES) {
                mobSubState = MobSubState.WALKING;
                walkFailureTicks = 0;
                lastWalkDist = dist;
                return;
            }
            repathCooldown = REPATH_COOLDOWN_TICKS;
            pf.startPathTo(mobTarget.getBlockPos(), () -> {
                if (state == State.KILLING_MOBS && mobSubState == MobSubState.PATHING) {
                    mobSubState = MobSubState.ATTACKING;
                }
            });
        }
    }

    private void tickMobWalking(MinecraftClient client) {
        if (mobTarget == null || !mobTarget.isAlive()) {
            releaseMovement(client);
            mobTarget = null;
            mobSubState = MobSubState.SEARCHING;
            return;
        }

        double dist = client.player.distanceTo(mobTarget);
        if (dist <= attackReach.getValue()) {
            releaseMovement(client);
            mobSubState = MobSubState.ATTACKING;
            return;
        }

        walkFailureTicks++;
        if (walkFailureTicks % 40 == 0) {
            if (dist >= lastWalkDist - 1.0) {
                releaseMovement(client);
                skippedEntities.add(mobTarget.getId());
                mobTarget = null;
                mobSubState = MobSubState.SEARCHING;
                return;
            }
            lastWalkDist = dist;
        }

        updateSoftAim(mobTarget, SOFT_AIM_LERP_COMBAT);
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

    private void tickMobAttacking(MinecraftClient client) {
        if (mobTarget == null || !mobTarget.isAlive()) {
            releaseAttack(client);
            mobTarget = null;
            mobSubState = MobSubState.SEARCHING;
            return;
        }

        double dist = client.player.distanceTo(mobTarget);
        if (dist > attackReach.getValue()) {
            releaseAttack(client);
            if (pathFailures >= MAX_PATH_FAILURES) {
                mobSubState = MobSubState.WALKING;
                walkFailureTicks = 0;
                lastWalkDist = dist;
            } else {
                startPathToMob();
            }
            return;
        }

        updateSoftAim(mobTarget, SOFT_AIM_LERP_COMBAT);
        float[] atkAngles = RotationUtil.getRotations(softAimPos);
        RotationUtil.setTarget(atkAngles[0], atkAngles[1], 0.4f);

        float yawDiff = Math.abs(MathHelper.wrapDegrees(atkAngles[0] - client.player.getYaw()));
        float pitchDiff = Math.abs(atkAngles[1] - client.player.getPitch());

        if (yawDiff < 15f && pitchDiff < 15f) {
            client.options.attackKey.setPressed(true);
            client.interactionManager.attackEntity(client.player, mobTarget);
            client.player.swingHand(Hand.MAIN_HAND);
        } else {
            client.options.attackKey.setPressed(false);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  BOSS_DETECTED — path to the boss
    // ════════════════════════════════════════════════════════════

    private void tickBossDetected(MinecraftClient client) {
        if (bossTarget == null || !bossTarget.isAlive()) {
            stopPathing();
            state = State.BOSS_DEAD;
            bossDeadCooldown = BOSS_DEAD_COOLDOWN_TICKS;
            return;
        }

        double dist = client.player.distanceTo(bossTarget);
        if (dist <= attackReach.getValue()) {
            stopPathing();
            softAimPos = null; // reset so aim starts fresh at actual boss position
            state = State.FIGHTING_BOSS;
            return;
        }

        if (sprint.getValue()) client.player.setSprinting(true);

        // Soft-aim at boss while pathing
        updateSoftAim(bossTarget, SOFT_AIM_LERP_APPROACH);
        Vec3d clampedAim = clampPitchForPathing(client.player, softAimPos);
        float[] bossPathAngles = RotationUtil.getRotations(clampedAim);
        RotationUtil.setTarget(bossPathAngles[0], bossPathAngles[1], 0.4f);

        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null && !pf.isNavigating() && repathCooldown <= 0) {
            repathCooldown = REPATH_COOLDOWN_TICKS;
            pf.startPathTo(bossTarget.getBlockPos(), () -> {
                if (state == State.BOSS_DETECTED) {
                    softAimPos = null; // reset for fresh aim at boss
                    state = State.FIGHTING_BOSS;
                }
            });
        }
    }

    // ════════════════════════════════════════════════════════════
    //  FIGHTING_BOSS — left-click attack, T5 TNT avoidance
    // ════════════════════════════════════════════════════════════

    private void tickFightingBoss(MinecraftClient client) {
        if (bossTarget == null || !bossTarget.isAlive()) {
            releaseAllControls(client);
            state = State.BOSS_DEAD;
            bossDeadCooldown = BOSS_DEAD_COOLDOWN_TICKS;
            dangerZones.clear();
            TobaChat.send("Boss defeated!");
            return;
        }

        // T5 TNT avoidance
        tntEvading = false;
        if (isT5Boss) {
            tickTNTAvoidance(client);
        }

        double dist = client.player.distanceTo(bossTarget);

        // If too far from boss, path back
        if (dist > attackReach.getValue() + 2.0 && !tntEvading) {
            PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
            if (pf != null && !pf.isNavigating() && repathCooldown <= 0) {
                repathCooldown = REPATH_COOLDOWN_TICKS;
                pf.startPathTo(bossTarget.getBlockPos(), () -> {});
            }

            // Walk toward boss directly
            updateSoftAim(bossTarget, SOFT_AIM_LERP_COMBAT);
            float[] bossWalkAngles = RotationUtil.getRotations(softAimPos);
            RotationUtil.setTarget(bossWalkAngles[0], bossWalkAngles[1], 0.4f);

            float yawDiff = Math.abs(MathHelper.wrapDegrees(bossWalkAngles[0] - client.player.getYaw()));
            if (yawDiff < 45f) {
                client.options.forwardKey.setPressed(true);
                client.options.sprintKey.setPressed(sprint.getValue());
            }
            return;
        }

        // In range — aim and left-click attack (unless evading TNT)
        if (!tntEvading) {
            updateSoftAim(bossTarget, SOFT_AIM_LERP_COMBAT);
            float[] bossFightAngles = RotationUtil.getRotations(softAimPos);
            RotationUtil.setTarget(bossFightAngles[0], bossFightAngles[1], 0.4f);

            float yawDiff = Math.abs(MathHelper.wrapDegrees(bossFightAngles[0] - client.player.getYaw()));
            float pitchDiff = Math.abs(bossFightAngles[1] - client.player.getPitch());

            if (yawDiff < 15f && pitchDiff < 15f) {
                client.options.attackKey.setPressed(true);
                client.interactionManager.attackEntity(client.player, bossTarget);
                client.player.swingHand(Hand.MAIN_HAND);
            } else {
                client.options.attackKey.setPressed(false);
            }

            // Random strafing movement — circle around the boss to dodge hits
            tickCombatMovement(client);
        }
    }

    /**
     * Random combat movement — constantly strafe, move forward/back to make
     * the player harder to hit and look more natural during boss fights.
     */
    private void tickCombatMovement(MinecraftClient client) {
        combatMoveTimer--;
        if (combatMoveTimer <= 0) {
            combatMoveTimer = 8 + jitterRng.nextInt(15); // 8-22 ticks per direction
            combatMoveDirection = jitterRng.nextInt(6);
        }

        // 0=forward, 1=back, 2=left, 3=right, 4=forward-left, 5=forward-right
        boolean fwd = combatMoveDirection == 0 || combatMoveDirection == 4 || combatMoveDirection == 5;
        boolean back = combatMoveDirection == 1;
        boolean left = combatMoveDirection == 2 || combatMoveDirection == 4;
        boolean right = combatMoveDirection == 3 || combatMoveDirection == 5;

        client.options.forwardKey.setPressed(fwd);
        client.options.backKey.setPressed(back);
        client.options.leftKey.setPressed(left);
        client.options.rightKey.setPressed(right);
        client.options.sprintKey.setPressed(fwd && sprint.getValue());
    }

    // ════════════════════════════════════════════════════════════
    //  BOSS_DEAD — short cooldown then restart
    // ════════════════════════════════════════════════════════════

    private void tickBossDead(MinecraftClient client) {
        bossDeadCooldown--;
        if (bossDeadCooldown <= 0) {
            bossTarget = null;
            mobTarget = null;
            softAimPos = null;
            mobSubState = MobSubState.SEARCHING;
            skippedEntities.clear();
            state = State.KILLING_MOBS;
            TobaChat.send("Resuming mob killing...");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  T5 TNT Avoidance
    // ════════════════════════════════════════════════════════════

    private void tickTNTAvoidance(MinecraftClient client) {
        // Scan for RED_TERRACOTTA periodically
        if (dangerScanCooldown <= 0) {
            scanDangerZones(client);
            dangerScanCooldown = DANGER_SCAN_INTERVAL;
        }
        dangerScanCooldown--;

        if (dangerZones.isEmpty()) return;

        ClientPlayerEntity player = client.player;
        BlockPos playerBlock = player.getBlockPos();

        // Check if player is standing on a danger zone
        boolean inDanger = dangerZones.contains(playerBlock)
                || dangerZones.contains(playerBlock.down());

        if (!inDanger) return;

        // Calculate escape direction: move away from centroid of nearby danger blocks
        Vec3d playerPos = player.getEntityPos();
        double dangerCenterX = 0, dangerCenterZ = 0;
        int count = 0;

        for (BlockPos dangerPos : dangerZones) {
            double dx = dangerPos.getX() + 0.5 - playerPos.x;
            double dz = dangerPos.getZ() + 0.5 - playerPos.z;
            if (dx * dx + dz * dz < 16.0) { // within 4 blocks
                dangerCenterX += dangerPos.getX() + 0.5;
                dangerCenterZ += dangerPos.getZ() + 0.5;
                count++;
            }
        }
        if (count == 0) return;

        dangerCenterX /= count;
        dangerCenterZ /= count;

        // Direction AWAY from danger centroid
        double escapeX = playerPos.x - dangerCenterX;
        double escapeZ = playerPos.z - dangerCenterZ;
        double len = Math.sqrt(escapeX * escapeX + escapeZ * escapeZ);
        if (len < 0.01) { escapeX = 1.0; escapeZ = 0.0; }
        else { escapeX /= len; escapeZ /= len; }

        // Convert escape direction to movement keys relative to player yaw
        float yaw = player.getYaw();
        double sinYaw = Math.sin(Math.toRadians(yaw));
        double cosYaw = Math.cos(Math.toRadians(yaw));
        double forwardDot = escapeX * (-sinYaw) + escapeZ * cosYaw;
        double rightDot = escapeX * cosYaw + escapeZ * sinYaw;

        client.options.forwardKey.setPressed(forwardDot > 0.3);
        client.options.backKey.setPressed(forwardDot < -0.3);
        client.options.rightKey.setPressed(rightDot > 0.3);
        client.options.leftKey.setPressed(rightDot < -0.3);
        client.options.sprintKey.setPressed(true);

        tntEvading = true;
    }

    private void scanDangerZones(MinecraftClient client) {
        dangerZones.clear();
        if (bossTarget == null || !bossTarget.isAlive()) return;

        BlockPos bossPos = bossTarget.getBlockPos();
        Set<BlockPos> rawDanger = new HashSet<>();

        // Find all RED_TERRACOTTA blocks near the boss
        for (BlockPos pos : BlockPos.iterate(
                bossPos.add(-DANGER_SCAN_RADIUS, -3, -DANGER_SCAN_RADIUS),
                bossPos.add(DANGER_SCAN_RADIUS, 3, DANGER_SCAN_RADIUS))) {
            if (client.world.getBlockState(pos).isOf(Blocks.RED_TERRACOTTA)) {
                rawDanger.add(pos.toImmutable());
            }
        }

        // Expand each danger block by 1 in all directions
        for (BlockPos pos : rawDanger) {
            dangerZones.add(pos);
            dangerZones.add(pos.north());
            dangerZones.add(pos.south());
            dangerZones.add(pos.east());
            dangerZones.add(pos.west());
            dangerZones.add(pos.north().east());
            dangerZones.add(pos.north().west());
            dangerZones.add(pos.south().east());
            dangerZones.add(pos.south().west());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Boss & mob detection
    // ════════════════════════════════════════════════════════════

    private LivingEntity findBoss(MinecraftClient client) {
        double radius = searchRadius.getValue();
        double radiusSq = radius * radius;
        ClientPlayerEntity player = client.player;

        // Auto-detect: check for both "Atoned Horror" (T5) and "Revenant Horror" (T1-T4)
        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (!living.isAlive()) continue;
            if (!entity.hasCustomName() || entity.getCustomName() == null) continue;

            String name = entity.getCustomName().getString();
            boolean isAtoned = name.contains("Atoned Horror");
            boolean isRevenant = name.contains("Revenant Horror");
            if (!isAtoned && !isRevenant) continue;

            double dx = player.getX() - entity.getX();
            double dz = player.getZ() - entity.getZ();
            if (dx * dx + dz * dz > radiusSq) continue;

            isT5Boss = isAtoned;
            return living;
        }
        return null;
    }

    private boolean isBossEntity(Entity entity) {
        if (!entity.hasCustomName() || entity.getCustomName() == null) return false;
        String name = entity.getCustomName().getString();
        return name.contains("Revenant Horror") || name.contains("Atoned Horror");
    }

    private LivingEntity findBestMob(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        double radius = searchRadius.getValue();
        double radiusSq = radius * radius;
        float yDiffLimit = maxYDiff.getValue();

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : client.world.getEntities()) {
            if (!(entity instanceof ZombieEntity zombie)) continue;
            if (!zombie.isAlive()) continue;
            if (entity instanceof PlayerEntity) continue;
            if (skippedEntities.contains(entity.getId())) continue;
            // Don't target the boss itself
            if (isBossEntity(entity)) continue;

            double dx = player.getX() - zombie.getX();
            double dy = player.getY() - zombie.getY();
            double dz = player.getZ() - zombie.getZ();
            double horizDistSq = dx * dx + dz * dz;
            if (horizDistSq > radiusSq) continue;
            if (Math.abs(dy) > yDiffLimit) continue;

            double score = Math.sqrt(horizDistSq) + Math.abs(dy) * 3.0;
            if (score < bestScore) {
                best = zombie;
                bestScore = score;
            }
        }
        return best;
    }

    // ════════════════════════════════════════════════════════════
    //  Soft-aim & jitter (same pattern as MobKillerModule)
    // ════════════════════════════════════════════════════════════

    private void updateSoftAim(LivingEntity entity, double lerpFactor) {
        Vec3d realPos = entity.getSyncedPos().add(
                jitterOffsetX,
                entity.getHeight() * 0.35 + jitterOffsetY,
                jitterOffsetZ
        );
        if (softAimPos == null) softAimPos = realPos;
        softAimPos = new Vec3d(
                softAimPos.x + (realPos.x - softAimPos.x) * lerpFactor,
                softAimPos.y + (realPos.y - softAimPos.y) * lerpFactor,
                softAimPos.z + (realPos.z - softAimPos.z) * lerpFactor
        );
    }

    private void updateJitter() {
        jitterUpdateTicks++;
        if (jitterUpdateTicks >= 8 + jitterRng.nextInt(8)) {
            jitterUpdateTicks = 0;
            double targetX = (jitterRng.nextDouble() - 0.5) * 0.3;
            double targetY = (jitterRng.nextDouble() - 0.5) * 0.25;
            double targetZ = (jitterRng.nextDouble() - 0.5) * 0.3;
            jitterOffsetX += (targetX - jitterOffsetX) * 0.4;
            jitterOffsetY += (targetY - jitterOffsetY) * 0.4;
            jitterOffsetZ += (targetZ - jitterOffsetZ) * 0.4;
        }
    }

    /**
     * Clamp aim pitch during pathing to prevent looking straight up/down,
     * which breaks pathfollowing.
     */
    private Vec3d clampPitchForPathing(ClientPlayerEntity player, Vec3d aim) {
        Vec3d eyePos = player.getEyePos();
        double aimDy = aim.y - eyePos.y;
        double aimHDist = Math.sqrt(
                (aim.x - eyePos.x) * (aim.x - eyePos.x) +
                (aim.z - eyePos.z) * (aim.z - eyePos.z));
        double maxPitchTan = 0.577; // ~30 degrees
        double clampedY;
        if (aimHDist > 0.5) {
            double maxDy = aimHDist * maxPitchTan;
            clampedY = eyePos.y + MathHelper.clamp((float) aimDy, (float) -maxDy, (float) maxDy);
        } else {
            clampedY = eyePos.y;
        }
        return new Vec3d(aim.x, clampedY, aim.z);
    }

    // ════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════

    private void startPathToMob() {
        if (mobTarget == null) return;
        mobSubState = MobSubState.PATHING;
        repathCooldown = REPATH_COOLDOWN_TICKS;
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) {
            pf.startPathTo(mobTarget.getBlockPos(), () -> {
                if (state == State.KILLING_MOBS && mobSubState == MobSubState.PATHING) {
                    pathFailures = 0;
                    mobSubState = MobSubState.ATTACKING;
                }
            });
        }
    }

    private void startPathToBoss() {
        if (bossTarget == null) return;
        repathCooldown = REPATH_COOLDOWN_TICKS;
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) {
            pf.startPathTo(bossTarget.getBlockPos(), () -> {
                if (state == State.BOSS_DETECTED) {
                    softAimPos = null; // reset for fresh aim at boss
                    state = State.FIGHTING_BOSS;
                }
            });
        }
    }

    private void stopPathing() {
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) pf.stop();
    }

    private void stopMobKilling(MinecraftClient client) {
        stopPathing();
        releaseAttack(client);
        releaseMovement(client);
        mobTarget = null;
        mobSubState = MobSubState.SEARCHING;
    }

    private void releaseMovement(MinecraftClient client) {
        client.options.forwardKey.setPressed(false);
        client.options.backKey.setPressed(false);
        client.options.sprintKey.setPressed(false);
        client.options.jumpKey.setPressed(false);
        client.options.leftKey.setPressed(false);
        client.options.rightKey.setPressed(false);
    }

    private void releaseAttack(MinecraftClient client) {
        client.options.attackKey.setPressed(false);
        RotationUtil.clearTarget();
    }

    private void releaseAllControls(MinecraftClient client) {
        releaseAttack(client);
        releaseMovement(client);
    }

    @Override
    protected void onEnable() {
        state = State.KILLING_MOBS;
        mobSubState = MobSubState.SEARCHING;
        TobaChat.send("Revenant Slayer started (auto-detect tier)");
    }

    @Override
    protected void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            releaseAllControls(client);
        }
        stopPathing();
        mobTarget = null;
        bossTarget = null;
        softAimPos = null;
        state = State.KILLING_MOBS;
        mobSubState = MobSubState.SEARCHING;
        repathCooldown = 0;
        pathFailures = 0;
        walkFailureTicks = 0;
        lastWalkDist = Double.MAX_VALUE;
        skippedEntities.clear();
        dangerZones.clear();
        bossDeadCooldown = 0;
        tntEvading = false;
        isT5Boss = false;
        combatMoveTimer = 0;
        combatMoveDirection = 0;
        autoHeal.reset();
        RotationUtil.clearTarget();
        TobaChat.send("Revenant Slayer stopped.");
    }

    private static float[] argbToFloats(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }
}
