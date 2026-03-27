package dev.toba.client.features.impl.module.macro.misc;

import dev.toba.client.api.pathfinding.navmesh.NavMeshGenerator;
import dev.toba.client.api.pathfinding.navmesh.NavMeshPathfinder;
import dev.toba.client.features.settings.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.api.pathfinding.PathfindingService;
import dev.toba.client.api.pathfinding.navmesh.NavMesh;
import dev.toba.client.api.pathfinding.navmesh.NavMeshPath;
import dev.toba.client.api.pathfinding.navmesh.NavPoly;
import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.render.PathRenderer;
import dev.toba.client.api.utils.RotationUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pathfinder module — walks the player along a NavMesh-smoothed path with
 * diagonal shortcuts, enhanced stuck detection, and centralized rotation.
 * <p>
 * Usable by other modules via startPathTo() and stop().
 */
public class PathfinderModule extends Module {

    private final Setting<Integer> pathColor;
    private final Setting<Boolean> sprint;
    private final Setting<Float> rotationSpeed;
    private final Setting<Boolean> showPathESP;
    private final Setting<Integer> maxRange;
    private final Setting<Boolean> debugNavMesh;

    // Advanced tuning settings
    private final Setting<Integer> strafeThreshold;
    private final Setting<Integer> stuckThreshold;
    private final Setting<Float> waypointReachDistance;
    private final Setting<Float> antiSpinThreshold;
    private final Setting<Float> walkYawTolerance;
    private final Setting<Float> flyYawTolerance;
    private final Setting<Float> driftRepathDistance;
    private final Setting<Float> blacklistRadius;
    private final Setting<Integer> blacklistDuration;
    private final Setting<Boolean> preferFlatGround;
    private final Setting<Boolean> enablePathCoarsening;
    private final Setting<Boolean> enableSpeedAdaptation;
    private final Setting<Boolean> enableSegmentedPaths;
    private final Setting<Integer> segmentSizeSetting;

    // NavMesh path (Vec3d waypoints with diagonal shortcuts)
    private List<Vec3d> currentPath = null;
    private NavMeshPath navMeshPath = null;
    private int pathIndex = 0;
    private boolean navigating = false;
    // True while a path is being computed on the background thread (before navigating becomes true).
    // isNavigating() returns true for both navigating and pathfindingInProgress so callers
    // (e.g. EndermanKillerModule) don't count a path failure while computation is still running.
    private volatile boolean pathfindingInProgress = false;
    private BlockPos targetPos = null;
    private boolean repathScheduled = false;

    // Flying mode
    private boolean flyMode = false;

    // Stuck detection with recovery phases — based on waypoint progress AND position.
    // Uses both: pathIndex must not advance AND position must barely change.
    // This avoids false positives on long waypoint segments where the player IS
    // moving but hasn't reached the next waypoint yet.
    private int lastProgressIndex = -1;        // pathIndex at last progress check
    private int noProgressTicks = 0;           // ticks since pathIndex last advanced
    private Vec3d lastStuckCheckPos = null;    // position at last stuck check
    private int positionStuckTicks = 0;        // ticks where position barely changed
    // STRAFE_THRESHOLD and STUCK_THRESHOLD are now configurable settings
    private boolean strafingToRecover = false;
    private boolean strafeDirectionRight = false;     // false = left (A), true = right (D)
    private int strafeToggleTicks = 0;                // ticks spent strafing in current direction

    private boolean repathingFromStuck = false;

    // Airborne state: tracked so look-ahead and waypoint advancement can pause
    // while the player is briefly in the air (walking off ledges, knockback).
    // Rotation is NOT locked during airborne — the player keeps turning smoothly.
    private boolean airborne = false;

    // Movement randomization for anti-cheat
    private final Random random = new Random();
    private int pauseTicks = 0;           // random micro-pauses
    private float speedVariation = 1.0f;  // sprint/walk variation
    private int nextPauseIn = 0;          // ticks until next micro-pause
    private static final int MIN_PAUSE_INTERVAL = 60;  // ~3 sec minimum between pauses
    private static final int MAX_PAUSE_INTERVAL = 200; // ~10 sec maximum between pauses
    private static final int MAX_PAUSE_DURATION = 4;   // max pause length in ticks

    // Segmented pathfinding state
    private boolean segmentedMode = false;
    private BlockPos finalGoal = null;
    private boolean nextSegmentRequested = false;

    // Dynamic repathing — monitor world for block changes near path
    private int dynamicRepathCooldown = 0;
    private static final int DYNAMIC_REPATH_INTERVAL = 40; // check every 2 seconds

    // Look-ahead — skip waypoints when player can see further ahead
    private int lookAheadCooldown = 0;
    private static final int LOOK_AHEAD_INTERVAL = 10; // check every 0.5 seconds

    // Callback for other modules (e.g., TreeCutter) to know when pathing completes
    private Runnable onArrivalCallback = null;

    public PathfinderModule() {
        super("Pathfinder", "Auto-walk to destinations via NavMesh", Category.MISC);
        setSettingsOnly(true);

        pathColor = addSetting(new Setting<>("Path Color", 0xFF00FFFF, Setting.SettingType.COLOR));
        sprint = addSetting(new Setting<>("Sprint", false, Setting.SettingType.BOOLEAN));
        rotationSpeed = addSetting(new Setting<>("Rotation Speed", 8.0f, Setting.SettingType.FLOAT));
        rotationSpeed.range(1.0, 15.0);
        showPathESP = addSetting(new Setting<>("Highlight Path Nodes", true, Setting.SettingType.BOOLEAN));
        maxRange = addSetting(new Setting<>("Max Range", 1000, Setting.SettingType.INTEGER));
        maxRange.range(50, 5000);
        debugNavMesh = addSetting(new Setting<>("Debug NavMesh", false, Setting.SettingType.BOOLEAN));

        // Advanced tuning (SUB_CONFIG)
        Setting<Boolean> advanced = addSetting(new Setting<>("Advanced", false, Setting.SettingType.SUB_CONFIG));

        strafeThreshold = new Setting<>("Strafe Threshold", 40, Setting.SettingType.INTEGER);
        strafeThreshold.range(10, 100);
        advanced.child(strafeThreshold);

        stuckThreshold = new Setting<>("Stuck Threshold", 70, Setting.SettingType.INTEGER);
        stuckThreshold.range(20, 200);
        advanced.child(stuckThreshold);

        waypointReachDistance = new Setting<>("Waypoint Reach", 0.8f, Setting.SettingType.FLOAT);
        waypointReachDistance.range(0.3, 3.0);
        advanced.child(waypointReachDistance);

        antiSpinThreshold = new Setting<>("Anti-Spin Threshold", 0.4f, Setting.SettingType.FLOAT);
        antiSpinThreshold.range(0.1, 1.5);
        advanced.child(antiSpinThreshold);

        walkYawTolerance = new Setting<>("Walk Yaw Tolerance", 50.0f, Setting.SettingType.FLOAT);
        walkYawTolerance.range(10.0, 90.0);
        advanced.child(walkYawTolerance);

        flyYawTolerance = new Setting<>("Fly Yaw Tolerance", 60.0f, Setting.SettingType.FLOAT);
        flyYawTolerance.range(10.0, 90.0);
        advanced.child(flyYawTolerance);

        driftRepathDistance = new Setting<>("Drift Repath Distance", 3.0f, Setting.SettingType.FLOAT);
        driftRepathDistance.range(1.0, 10.0);
        advanced.child(driftRepathDistance);

        blacklistRadius = new Setting<>("Blacklist Radius", 2.0f, Setting.SettingType.FLOAT);
        blacklistRadius.range(0.5, 5.0);
        advanced.child(blacklistRadius);

        blacklistDuration = new Setting<>("Blacklist Duration", 3000, Setting.SettingType.INTEGER);
        blacklistDuration.range(500, 12000);
        advanced.child(blacklistDuration);

        preferFlatGround = new Setting<>("Prefer Flat Ground", true, Setting.SettingType.BOOLEAN);
        advanced.child(preferFlatGround);

        enablePathCoarsening = new Setting<>("Path Coarsening", true, Setting.SettingType.BOOLEAN);
        advanced.child(enablePathCoarsening);

        enableSpeedAdaptation = new Setting<>("Speed Adaptation", true, Setting.SettingType.BOOLEAN);
        advanced.child(enableSpeedAdaptation);

        enableSegmentedPaths = new Setting<>("Segmented Paths", true, Setting.SettingType.BOOLEAN);
        advanced.child(enableSegmentedPaths);

        segmentSizeSetting = new Setting<>("Segment Size", 100, Setting.SettingType.INTEGER);
        segmentSizeSetting.range(50, 200);
        advanced.child(segmentSizeSetting);
    }

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Start ground pathfinding to target. Can be called from any module or command.
     */
    public void startPathTo(BlockPos target) {
        startPathTo(target, null);
    }

    /**
     * Start hybrid pathfinding to target — tries ground, falls back to fly.
     * This is the primary API used by /goto and other modules.
     */
    public void startPathTo(BlockPos target, Runnable onArrival) {
        startHybridTo(target, onArrival);
    }

    /**
     * Start 3D flight pathfinding to target. Uses voxel A* through open air.
     */
    public void startFlyTo(BlockPos target) {
        startFlyTo(target, null);
    }

    /**
     * Start 3D flight pathfinding to target with an optional callback when arrived.
     */
    public void startFlyTo(BlockPos target, Runnable onArrival) {
        resetState(target, onArrival, true);
        pathfindingInProgress = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        Vec3d startVec = client.player.getSyncedPos();
        Vec3d goalVec = new Vec3d(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        TobaChat.send("Flying to (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")...");

        PathfindingService.getInstance().findFlyPath(startVec, goalVec, client.world, maxRange.getValue())
                .thenAccept(path -> handlePathResult(path, client));
    }

    /**
     * Start hybrid pathfinding — tries ground first, falls back to fly if ground fails.
     * This is the recommended API for general-purpose pathfinding that works regardless
     * of whether the player is on the ground or flying.
     */
    public void startHybridTo(BlockPos target) {
        startHybridTo(target, null);
    }

    /**
     * Start hybrid pathfinding with an optional callback when arrived.
     */
    public void startHybridTo(BlockPos target, Runnable onArrival) {
        // Reset to ground mode initially — will be overridden if fly is needed
        resetState(target, onArrival, false);
        pathfindingInProgress = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // God Potion: 6 block jump height, longer safe falls
        NavMeshGenerator.setMovementParams(6, 10);
        // RotationUtil uses GCD-based smoothing without velocity-based pitch drift,
        // so no vertical pitch compensation is needed during high jumps.

        // Push settings to NavMeshPathfinder before async pathfinding
        NavMeshPathfinder.setPreferFlatGround(preferFlatGround.getValue());
        NavMeshPathfinder.setEnablePathCoarsening(enablePathCoarsening.getValue());

        Vec3d startVec = client.player.getSyncedPos();
        Vec3d goalVec = new Vec3d(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        double distToGoal = startVec.distanceTo(goalVec);
        int segSize = segmentSizeSetting.getValue();

        if (enableSegmentedPaths.getValue() && distToGoal > segSize * 1.3) {
            // Segmented mode: compute first segment only
            segmentedMode = true;
            finalGoal = target;

            Vec3d intermediateGoal = computeIntermediateGoal(startVec, goalVec, segSize);

            TobaChat.send("Segmented pathfinding to (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")...");

            if (canPlayerFly()) {
                PathfindingService.getInstance().findHybridPath(startVec, intermediateGoal, client.world, segSize + 20)
                        .thenAccept(result -> handleHybridPathResult(result, client));
            } else {
                PathfindingService.getInstance().findNavMeshPath(startVec, intermediateGoal, client.world, segSize + 20)
                        .thenAccept(path -> handlePathResult(path, client));
            }
        } else {
            // Normal non-segmented mode
            segmentedMode = false;
            finalGoal = null;

            TobaChat.send("Pathfinding to (" + target.getX() + ", " + target.getY() + ", " + target.getZ() + ")...");

            if (canPlayerFly()) {
                PathfindingService.getInstance().findHybridPath(startVec, goalVec, client.world, maxRange.getValue())
                        .thenAccept(result -> handleHybridPathResult(result, client));
            } else {
                PathfindingService.getInstance().findNavMeshPath(startVec, goalVec, client.world, maxRange.getValue())
                        .thenAccept(path -> handlePathResult(path, client));
            }
        }
    }

    /**
     * Stop pathfinding and release all resources.
     */
    public void stop() {
        releaseKeys();
        navigating = false;
        pathfindingInProgress = false;
        currentPath = null;
        navMeshPath = null;
        pathIndex = 0;
        targetPos = null;
        lastProgressIndex = -1;
        noProgressTicks = 0;
        positionStuckTicks = 0;
        lastStuckCheckPos = null;
        strafingToRecover = false;
        repathScheduled = false;
        repathingFromStuck = false;
        flyMode = false;
        airborne = false;
        pauseTicks = 0;
        dynamicRepathCooldown = 0;
        lookAheadCooldown = 0;
        segmentedMode = false;
        finalGoal = null;
        nextSegmentRequested = false;
        PathRenderer.getInstance().clearPath();
        ESPRenderer.getInstance().beginPathScan(); // clear path ESP
        RotationUtil.clearTarget();

        Runnable callback = onArrivalCallback;
        onArrivalCallback = null;
        // Don't call callback on stop() - only on arrival
    }

    // ──────────────────────────────────────────────────────────────
    //  Tick (called each game tick from TobaClient)
    // ──────────────────────────────────────────────────────────────

    public void onTick() {
        if (!isEnabled() || !navigating || currentPath == null) return;

        // Compute smoothing from rotationSpeed setting for RotationUtil.
        // Lower speed → slower/smoother rotation, higher speed → faster convergence.
        float pathfinderSmoothing = MathHelper.clamp(rotationSpeed.getValue() * 0.012f, 0.05f, 0.3f);

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // Bounds check
        if (pathIndex >= currentPath.size()) {
            arrive();
            return;
        }

        // Path ESP - highlight waypoint nodes
        updatePathESP();

        Vec3d playerPos = player.getSyncedPos();

        // Advance past any waypoints the player is already close to
        boolean advanced = advanceWaypoints(playerPos);

        if (advanced) {
            // noProgressTicks resets automatically in handleStuckDetection when pathIndex changes

            if (pathIndex >= currentPath.size()) {
                arrive();
                return;
            }

            // Update the active index — the renderer keeps the full path and
            // shows from (activeIndex - 1) onward, so the segment the player
            // is currently on stays visible until the next waypoint is reached.
            PathRenderer.getInstance().setActiveIndex(pathIndex);
        }

        // Segmented pathfinding: trigger next segment computation when
        // the player has progressed through ~70% of the current waypoints.
        if (segmentedMode && !nextSegmentRequested && finalGoal != null && currentPath != null) {
            double progress = (double) pathIndex / currentPath.size();
            if (progress >= 0.70) {
                requestNextSegment(client);
            }
        }

        // Look-ahead: periodically check if we can skip to a further waypoint
        performLookAhead(playerPos, client);

        // Dynamic repathing: check if blocks near our path have changed
        performDynamicRepath(playerPos, client);

        Vec3d nextWaypoint = currentPath.get(pathIndex);
        double dx = nextWaypoint.x - playerPos.x;
        double dz = nextWaypoint.z - playerPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Stuck detection — if stuck, immediately blacklist and repath
        handleStuckDetection(player, playerPos, client);
        if (repathingFromStuck) return;

        // Randomized micro-pauses: briefly stop for 1-4 ticks at random intervals
        if (handleMicroPause()) return;

        // Dynamic mode switching: if the player is creative-flying but we're in
        // ground mode, switch to fly movement so vertical controls (jump/sneak) work.
        // If the player lands while in fly mode and is on the ground, switch back to walk.
        if (!flyMode && player.getAbilities().flying) {
            flyMode = true;
        } else if (flyMode && player.isOnGround() && !player.getAbilities().flying) {
            flyMode = false;
        }

        if (flyMode) {
            tickFlyMovement(player, client, nextWaypoint, playerPos);
        } else {
            tickWalkMovement(player, client, nextWaypoint, playerPos, dx, dz, horizontalDist);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Walk Movement (ground-based, keyboard input simulation)
    // ──────────────────────────────────────────────────────────────

    private void tickWalkMovement(ClientPlayerEntity player, MinecraftClient client,
                                  Vec3d nextWaypoint, Vec3d playerPos,
                                  double dx, double dz, double horizontalDist) {
        // Jumping policy: jump when terrain ahead is too high for auto-step (0.6 blocks).
        //
        // Two detection methods work together:
        //
        // Method 1: Waypoint Y diff — checks consecutive waypoint heights. Good for
        //   planned jumps where the A* path explicitly goes up a full block.
        //
        // Method 2: Terrain probing — checks the ACTUAL block in front of the player
        //   in the movement direction. This catches cases where path simplification
        //   merged waypoints across rugged terrain, hiding step-ups that the player
        //   needs to jump over. Without this, the player walks into a wall and gets stuck.
        double waypointDy = 0;
        if (currentPath != null && pathIndex > 0) {
            waypointDy = nextWaypoint.y - currentPath.get(pathIndex - 1).y;
        } else {
            waypointDy = nextWaypoint.y - playerPos.y;
        }
        boolean needsJumpForWaypoint = waypointDy > 0.9 && waypointDy <= NavMeshGenerator.getMaxJumpHeight()
                && horizontalDist < 1.5 && (nextWaypoint.y - playerPos.y) > 0.5;

        // Terrain probe: check ahead in the movement direction for obstacles that
        // require jumping. Probes at multiple distances and checks several Y levels
        // to handle snow layers, slabs, block ledges, carpet, and other terrain with
        // partial collision shapes.
        boolean needsJumpForTerrain = false;
        if (player.isOnGround() && client.world != null && horizontalDist > 0.3) {
            double normDx = dx / horizontalDist;
            double normDz = dz / horizontalDist;
            double feetY = playerPos.y;
            double maxJump = NavMeshGenerator.getMaxJumpHeight();

            // Probe at multiple distances to catch obstacles at varying ranges.
            // Close probes (0.4) catch immediate step-ups; far probes (1.3) let us
            // pre-jump for obstacles we're about to walk into.
            double[] probeDists = {0.4, 0.8, 1.3};

            for (double probeDist : probeDists) {
                if (needsJumpForTerrain) break;

                double probeX = playerPos.x + normDx * probeDist;
                double probeZ = playerPos.z + normDz * probeDist;

                // Scan a vertical column at the probe point: from 1 block below feet
                // to 2 blocks above. This catches:
                //   - Snow/carpet/slabs on the ground (at or slightly above feetY)
                //   - Full blocks at feet level
                //   - Blocks at body/head level that block forward movement
                //   - Ledge patterns where ground drops then rises
                int scanMinY = (int) Math.floor(feetY - 0.5);
                int scanMaxY = (int) Math.floor(feetY + 1.8);

                for (int blockY = scanMinY; blockY <= scanMaxY; blockY++) {
                    BlockPos checkPos = BlockPos.ofFloored(probeX, blockY, probeZ);
                    if (!client.world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) continue;

                    net.minecraft.block.BlockState state = client.world.getBlockState(checkPos);
                    net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(client.world, checkPos);
                    if (shape.isEmpty()) continue;

                    double shapeTop = checkPos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                    double shapeBottom = checkPos.getY() + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                    double stepHeight = shapeTop - feetY;

                    // Case 1: Block whose top is above auto-step height (0.6) but
                    // within jump range — classic step-up (full blocks, snow layers,
                    // slabs, stairs, etc.)
                    if (stepHeight > 0.6 && stepHeight <= maxJump && shapeTop > feetY + 0.01) {
                        needsJumpForTerrain = true;
                        break;
                    }

                    // Case 2: Block at body/head level that blocks forward movement.
                    // The bottom of the collision is above feet but below head clearance.
                    // This catches overhangs, half-doors, trapdoors, etc.
                    if (shapeBottom > feetY + 0.01 && shapeBottom < feetY + 1.8
                            && shapeTop > feetY + 0.6) {
                        needsJumpForTerrain = true;
                        break;
                    }
                }
            }

            // Additional check: "ledge then step-up" pattern.
            // The ground drops away ahead (e.g., walking off a slab) and there's a
            // higher surface right after. The player needs to jump to bridge the gap.
            // Probe 1.5 blocks ahead: if the ground there is higher than current feetY
            // by more than auto-step but within jump range, and there's no ground at
            // the intermediate position, we need to jump.
            if (!needsJumpForTerrain && horizontalDist > 0.8) {
                double farProbeX = playerPos.x + normDx * 1.5;
                double farProbeZ = playerPos.z + normDz * 1.5;
                double midProbeX = playerPos.x + normDx * 0.7;
                double midProbeZ = playerPos.z + normDz * 0.7;

                // Find ground height at far probe
                double farGroundTop = Double.NaN;
                for (int yOff = 1; yOff >= -2; yOff--) {
                    BlockPos gp = BlockPos.ofFloored(farProbeX, feetY + yOff, farProbeZ);
                    if (!client.world.isChunkLoaded(gp.getX() >> 4, gp.getZ() >> 4)) break;
                    net.minecraft.block.BlockState gs = client.world.getBlockState(gp);
                    net.minecraft.util.shape.VoxelShape gShape = gs.getCollisionShape(client.world, gp);
                    if (!gShape.isEmpty()) {
                        double top = gp.getY() + gShape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                        if (top >= feetY - 1.5 && top <= feetY + maxJump) {
                            farGroundTop = top;
                            break;
                        }
                    }
                }

                // Find ground height at mid probe
                boolean midHasGround = false;
                for (int yOff = 0; yOff >= -1; yOff--) {
                    BlockPos gp = BlockPos.ofFloored(midProbeX, feetY + yOff, midProbeZ);
                    if (!client.world.isChunkLoaded(gp.getX() >> 4, gp.getZ() >> 4)) break;
                    net.minecraft.block.BlockState gs = client.world.getBlockState(gp);
                    net.minecraft.util.shape.VoxelShape gShape = gs.getCollisionShape(client.world, gp);
                    if (!gShape.isEmpty()) {
                        double top = gp.getY() + gShape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                        if (top >= feetY - 0.5 && top <= feetY + 0.6) {
                            midHasGround = true;
                            break;
                        }
                    }
                }

                // If far ground is higher than auto-step and mid has no ground at our level,
                // we need to jump across the gap
                if (!Double.isNaN(farGroundTop) && !midHasGround) {
                    double farStep = farGroundTop - feetY;
                    if (farStep > 0.6 && farStep <= maxJump) {
                        needsJumpForTerrain = true;
                    }
                }
            }
        }

        if (strafingToRecover && player.isOnGround()) {
            setKeyPressed(client.options.jumpKey, true);
        } else if ((needsJumpForWaypoint || needsJumpForTerrain) && player.isOnGround()) {
            setKeyPressed(client.options.jumpKey, true);
        } else {
            setKeyPressed(client.options.jumpKey, false);
        }

        boolean shouldSprint = sprint.getValue() && speedVariation > 0.5f;

        // Track airborne state (used by look-ahead and waypoint advancement guards)
        // but don't lock rotation — the player should keep turning smoothly even
        // during brief airborne moments from walking off small ledges.
        airborne = !player.isOnGround();

        // ANTI-SPIN: When very close to the waypoint horizontally, don't update
        // rotation — atan2 is unstable at tiny distances causing wild spinning.
        // Just hold forward and let momentum carry us through the waypoint.
        float spinThresh = antiSpinThreshold.getValue();
        if (horizontalDist < spinThresh) {
            if (!strafingToRecover) {
                setKeyPressed(client.options.leftKey, false);
                setKeyPressed(client.options.rightKey, false);
            }
            setKeyPressed(client.options.forwardKey, true);
            setKeyPressed(client.options.sprintKey, false);
            // Don't request new rotation — keep whatever yaw we already have
            return;
        }

        // ── Tangent-based rotation with strafe-through-turns ──
        //
        // Instead of aiming at waypoint POSITIONS (which jitter on rugged terrain),
        // compute the path's TRAVEL DIRECTION (tangent) from upcoming segment vectors.
        // A human doesn't stare at spots on the ground — they look in the direction
        // they're walking. On a roughly straight path with slightly offset nodes,
        // the tangent stays stable while player-to-node vectors would oscillate.
        //
        // The tangent is a weighted average of the direction vectors of the next
        // few path segments (waypoint[i+1] - waypoint[i]), not the vectors from
        // the player to each waypoint. This is the key difference.

        boolean hasNextWp = currentPath != null && pathIndex + 1 < currentPath.size();

        // Compute the smoothed path tangent from upcoming segments.
        // Each segment's direction vector is weighted by its length (longer segments
        // contribute more) and by recency (closer segments matter more).
        double tangentDx = dx; // fallback: direction to current waypoint
        double tangentDz = dz;

        if (currentPath != null && currentPath.size() > 1) {
            double tDx = 0, tDz = 0, tWeight = 0;

            // Include the current segment (player → current waypoint) with dominant weight
            // so the player primarily looks where they're going NOW, not far ahead.
            double curLen = horizontalDist;
            if (curLen > 0.01) {
                double w = curLen * 2.0; // moderate weight for immediate direction
                tDx += (dx / curLen) * w;
                tDz += (dz / curLen) * w;
                tWeight += w;
            }

            // Average direction of upcoming path segments, limited by WALKING DISTANCE
            // not index count. This prevents pre-aiming turns that are far away.
            double maxTangentDist = 7.0; // consider path within 7 blocks of travel
            double accDist = 0;
            int maxSegs = Math.min(10, currentPath.size() - pathIndex - 1);
            for (int i = 0; i < maxSegs; i++) {
                Vec3d segStart = currentPath.get(pathIndex + i);
                Vec3d segEnd = currentPath.get(pathIndex + i + 1);
                double sDx = segEnd.x - segStart.x;
                double sDz = segEnd.z - segStart.z;
                double sLen = Math.sqrt(sDx * sDx + sDz * sDz);
                if (sLen < 0.01) continue;

                accDist += sLen;
                if (accDist > maxTangentDist) break;

                // Weight: distance-based linear decay (nearby segments matter most)
                double distFactor = 1.0 - (accDist / maxTangentDist);
                double w = sLen * distFactor;
                tDx += (sDx / sLen) * w; // normalized direction * weight
                tDz += (sDz / sLen) * w;
                tWeight += w;
            }

            if (tWeight > 0.01) {
                tangentDx = tDx / tWeight;
                tangentDz = tDz / tWeight;
                // Normalize and scale to a reasonable look distance
                double tLen = Math.sqrt(tangentDx * tangentDx + tangentDz * tangentDz);
                if (tLen > 0.01) {
                    // Look 5-10 blocks ahead along the tangent direction
                    double lookDist = Math.max(5.0, horizontalDist);
                    tangentDx = (tangentDx / tLen) * lookDist;
                    tangentDz = (tangentDz / tLen) * lookDist;
                }
            }
        }

        double lookDx = tangentDx;
        double lookDz = tangentDz;

        // ── Pre-rotation: very subtle look-ahead at the next turn ──
        // When approaching a turn (>25°), blend a tiny amount (max 3%) of
        // the next segment's direction. This avoids the "aimbot" look while
        // still preventing harsh stop-turn-go jerks.
        if (currentPath != null && pathIndex + 1 < currentPath.size()) {
            Vec3d nextWp = currentPath.get(pathIndex + 1);
            double nextDx = nextWp.x - nextWaypoint.x;
            double nextDz = nextWp.z - nextWaypoint.z;
            double nextLen = Math.sqrt(nextDx * nextDx + nextDz * nextDz);

            if (nextLen > 0.5) {
                float curYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float nextYaw = (float) Math.toDegrees(Math.atan2(-nextDx, nextDz));
                float turnAngle = Math.abs(MathHelper.wrapDegrees(nextYaw - curYaw));

                if (turnAngle > 25f) {
                    double blendRange = 4.0;
                    double blend = 1.0 - MathHelper.clamp(
                            (float) ((horizontalDist - spinThresh) / (blendRange - spinThresh)),
                            0f, 1f);
                    blend = MathHelper.clamp((float) blend, 0f, 0.08f);

                    if (blend > 0.005) {
                        double tLen = Math.sqrt(lookDx * lookDx + lookDz * lookDz);
                        if (tLen > 0.01) {
                            double curDirX = lookDx / tLen;
                            double curDirZ = lookDz / tLen;
                            double nDirX = nextDx / nextLen;
                            double nDirZ = nextDz / nextLen;
                            lookDx = (curDirX + blend * (nDirX - curDirX)) * tLen;
                            lookDz = (curDirZ + blend * (nDirZ - curDirZ)) * tLen;
                        }
                    }
                }
            }
        }

        // ── Vertical look direction based on path SLOPE profile ──
        //
        // Instead of raw height deltas (which aim to the peak of a staircase
        // even when flat ground follows), compute the weighted average SLOPE
        // of upcoming segments. This way:
        //   - Staircase (slope≈1) → flat (slope=0): moderate upward look
        //   - Continuous climb: strong upward look
        //   - Descent → flat: moderate downward look, then levels out
        //
        // The slope naturally captures transitions because flat segments after
        // a climb pull the average slope toward zero, preventing the player
        // from staring at the top of the staircase.
        double pitchOffset = 0;
        if (currentPath != null && pathIndex + 1 < currentPath.size()) {
            double totalSlope = 0;
            double totalSlopeWeight = 0;
            double maxPitchDist = 12.0; // only consider slopes within 12 blocks of travel
            double pitchAccDist = 0;

            int pitchMaxSegs = Math.min(12, currentPath.size() - pathIndex - 1);
            for (int i = 0; i < pitchMaxSegs; i++) {
                Vec3d segStart = currentPath.get(pathIndex + i);
                Vec3d segEnd = currentPath.get(pathIndex + i + 1);

                double segDx = segEnd.x - segStart.x;
                double segDz = segEnd.z - segStart.z;
                double segHDist = Math.sqrt(segDx * segDx + segDz * segDz);
                double segDy = segEnd.y - segStart.y;

                if (segHDist < 0.01) continue;

                pitchAccDist += segHDist;
                if (pitchAccDist > maxPitchDist) break;

                double slope = segDy / segHDist; // rise/run
                // Weight: longer segments contribute more, linear decay with distance
                double distFactor = 1.0 - (pitchAccDist / maxPitchDist);
                double w = segHDist * distFactor;

                totalSlope += slope * w;
                totalSlopeWeight += w;
            }

            if (totalSlopeWeight > 0.01) {
                double avgSlope = totalSlope / totalSlopeWeight;
                // Convert slope to vertical offset:
                // Scale by look distance so the pitch feels proportional.
                // A slope of 1.0 (45° staircase) at 6 blocks forward ≈ 2.4 blocks up.
                double hLookDist = Math.sqrt(lookDx * lookDx + lookDz * lookDz);
                pitchOffset = avgSlope * Math.min(hLookDist, 8.0) * 0.4;
                // Clamp to ±3 blocks to prevent extreme pitch angles
                pitchOffset = MathHelper.clamp((float) pitchOffset, -3.0f, 3.0f);
            }
        }
        double lookY = player.getEyePos().y + pitchOffset;

        // Request smooth rotation toward the tangent look direction.
        Vec3d lookTarget = new Vec3d(
                playerPos.x + lookDx,
                lookY,
                playerPos.z + lookDz
        );
        float[] lookAngles = RotationUtil.getRotations(lookTarget);
        float walkSmoothing = MathHelper.clamp(rotationSpeed.getValue() * 0.012f, 0.05f, 0.3f);
        RotationUtil.setTarget(lookAngles[0], lookAngles[1], walkSmoothing);

        // Calculate the yaw we NEED to face for movement (toward current waypoint)
        // and the yaw we're ACTUALLY facing (player.getYaw()).
        // The difference determines whether we need strafing to compensate.
        float movementYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float currentYaw = player.getYaw();
        float yawDiffToMovement = MathHelper.wrapDegrees(movementYaw - currentYaw);

        // ── Strafe-through-turns ──
        // When the player is pre-aimed toward the next segment but still needs to
        // move toward the current waypoint, the yaw difference between facing and
        // movement directions tells us which strafe key to press:
        //
        //   yawDiffToMovement > 0  → waypoint is to our RIGHT → press D to strafe right
        //   yawDiffToMovement < 0  → waypoint is to our LEFT  → press A to strafe left
        //
        // Strafe strength: we only strafe when the yaw diff is significant (>15°).
        // Below 15° the player is facing close enough to the movement direction
        // that forward alone suffices. Above 120° is too extreme (facing backwards).
        //
        // When stuck recovery is active, don't override its strafe keys.
        float absYawDiff = Math.abs(yawDiffToMovement);

        if (!strafingToRecover) {
            if (absYawDiff > 145f) {
                // Waypoint is BEHIND the player — walk backwards instead of turning 180°.
                // This handles cases where the player overshoots or the path doubles back.
                setKeyPressed(client.options.forwardKey, false);
                setKeyPressed(client.options.backKey, true);
                setKeyPressed(client.options.sprintKey, false);
                // Strafe to help angle toward the waypoint
                if (absYawDiff < 170f) {
                    boolean strafeRight = yawDiffToMovement > 0;
                    setKeyPressed(client.options.leftKey, !strafeRight);
                    setKeyPressed(client.options.rightKey, strafeRight);
                } else {
                    setKeyPressed(client.options.leftKey, false);
                    setKeyPressed(client.options.rightKey, false);
                }
                return;
            } else if (absYawDiff > 15f) {
                // Strafe toward the movement direction while looking ahead
                boolean strafeRight = yawDiffToMovement > 0;
                setKeyPressed(client.options.leftKey, !strafeRight);
                setKeyPressed(client.options.rightKey, strafeRight);
            } else {
                // Facing close enough to movement direction — no strafe needed
                setKeyPressed(client.options.leftKey, false);
                setKeyPressed(client.options.rightKey, false);
            }
        }

        // Walking forward. The combination of W + A/D creates the
        // smooth curved trajectory through turns, just like a human player.
        setKeyPressed(client.options.backKey, false);

        if (enableSpeedAdaptation.getValue()) {
            // Graduated speed adaptation with predictive deceleration.
            // Look ahead at the upcoming turn angle and blend it into the
            // effective yaw diff when close to the current waypoint.
            float upcomingTurnAngle = 0;
            if (currentPath != null && pathIndex + 1 < currentPath.size()) {
                Vec3d nextWp = currentPath.get(Math.min(pathIndex + 1, currentPath.size() - 1));
                double nextSegDx = nextWp.x - nextWaypoint.x;
                double nextSegDz = nextWp.z - nextWaypoint.z;
                double nextSegLen = Math.sqrt(nextSegDx * nextSegDx + nextSegDz * nextSegDz);
                if (nextSegLen > 0.3) {
                    float curSegYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float nextSegYaw = (float) Math.toDegrees(Math.atan2(-nextSegDx, nextSegDz));
                    upcomingTurnAngle = Math.abs(MathHelper.wrapDegrees(nextSegYaw - curSegYaw));
                }
            }

            // Blend upcoming turn into effective yaw diff when within 4 blocks of waypoint
            float effectiveYawDiff = absYawDiff;
            if (horizontalDist < 4.0 && upcomingTurnAngle > effectiveYawDiff) {
                float blendFactor = (float) (1.0 - (horizontalDist / 4.0));
                blendFactor = MathHelper.clamp(blendFactor, 0.0f, 0.6f);
                effectiveYawDiff = effectiveYawDiff + blendFactor * (upcomingTurnAngle - effectiveYawDiff);
            }

            // 4 speed tiers based on effective yaw difference
            if (effectiveYawDiff < 25f) {
                // Full sprint: straight ahead
                setKeyPressed(client.options.forwardKey, true);
                setKeyPressed(client.options.sprintKey, shouldSprint);
            } else if (effectiveYawDiff < 55f) {
                // Jog: forward without sprint (tighter turn radius)
                setKeyPressed(client.options.forwardKey, true);
                setKeyPressed(client.options.sprintKey, false);
            } else if (effectiveYawDiff < 90f) {
                // Walk: forward without sprint
                setKeyPressed(client.options.forwardKey, true);
                setKeyPressed(client.options.sprintKey, false);
            } else {
                // Stop-and-turn: release forward to let rotation catch up
                setKeyPressed(client.options.forwardKey, false);
                setKeyPressed(client.options.sprintKey, false);
            }
        } else {
            // Legacy behavior: binary sprint
            setKeyPressed(client.options.forwardKey, true);
            setKeyPressed(client.options.sprintKey, shouldSprint && absYawDiff < 60.0f);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Fly Movement (3D flight via keyboard input)
    // ──────────────────────────────────────────────────────────────

    private void tickFlyMovement(ClientPlayerEntity player, MinecraftClient client,
                                 Vec3d nextWaypoint, Vec3d playerPos) {
        // Clear strafe keys from stuck recovery
        if (!strafingToRecover) {
            setKeyPressed(client.options.leftKey, false);
            setKeyPressed(client.options.rightKey, false);
        }

        double dx = nextWaypoint.x - playerPos.x;
        double dy = nextWaypoint.y - playerPos.y;
        double dz = nextWaypoint.z - playerPos.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        double totalDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Vertical movement: jump key to go up, sneak key to go down
        if (dy > 0.5) {
            setKeyPressed(client.options.jumpKey, true);
            setKeyPressed(client.options.sneakKey, false);
        } else if (dy < -0.5) {
            setKeyPressed(client.options.jumpKey, false);
            setKeyPressed(client.options.sneakKey, true);
        } else {
            setKeyPressed(client.options.jumpKey, false);
            setKeyPressed(client.options.sneakKey, false);
        }

        // ANTI-SPIN: When very close horizontally, skip rotation update to avoid
        // atan2 instability. Just use vertical movement to reach the waypoint.
        if (horizontalDist < antiSpinThreshold.getValue()) {
            // If we're mostly moving vertically, just hold forward with current yaw
            if (Math.abs(dy) > 0.3) {
                setKeyPressed(client.options.forwardKey, true);
            } else {
                setKeyPressed(client.options.forwardKey, false);
            }
            setKeyPressed(client.options.sprintKey, false);
            return;
        }

        // Rotate toward waypoint including pitch for vertical aim
        float[] flyAngles = RotationUtil.getRotations(nextWaypoint);
        float flySmoothing = MathHelper.clamp(rotationSpeed.getValue() * 0.012f, 0.05f, 0.3f);
        RotationUtil.setTarget(flyAngles[0], flyAngles[1], flySmoothing);

        float targetYaw = (float) Math.toDegrees(Math.atan2(-(dx), dz));
        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());

        // Forward movement — only when facing roughly the right direction
        if (totalDist > 0.3 && Math.abs(yawDiff) < flyYawTolerance.getValue()) {
            setKeyPressed(client.options.forwardKey, true);
            // Sprint in flight mode for speed
            setKeyPressed(client.options.sprintKey, sprint.getValue());
        } else {
            setKeyPressed(client.options.forwardKey, false);
            setKeyPressed(client.options.sprintKey, false);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Movement Randomization
    // ──────────────────────────────────────────────────────────────

    /**
     * Handle randomized micro-pauses that make movement look more human.
     * Returns true if currently pausing (caller should skip movement).
     */
    private boolean handleMicroPause() {
        // Never pause during stuck recovery — strafe/jump must keep going
        if (strafingToRecover) return false;

        // Currently in a pause
        if (pauseTicks > 0) {
            pauseTicks--;
            releaseKeys();
            return true;
        }

        // Count down to next pause
        nextPauseIn--;
        if (nextPauseIn <= 0) {
            // Trigger a micro-pause
            pauseTicks = 1 + random.nextInt(MAX_PAUSE_DURATION);
            nextPauseIn = MIN_PAUSE_INTERVAL + random.nextInt(MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL);

            // Randomize sprint variation for the next segment
            speedVariation = 0.6f + random.nextFloat() * 0.4f; // 0.6 to 1.0
        }

        return false;
    }

    // ──────────────────────────────────────────────────────────────
    //  Look-Ahead (skip to further visible waypoints)
    // ──────────────────────────────────────────────────────────────

    /**
     * Periodically check if the player can see waypoints further ahead
     * along the path and skip intermediate ones. This handles cases where
     * the player drifted slightly off-path but can still reach a later waypoint.
     */
    private void performLookAhead(Vec3d playerPos, MinecraftClient client) {
        if (airborne) return; // no look-ahead while mid-air
        lookAheadCooldown--;
        if (lookAheadCooldown > 0 || currentPath == null || client.world == null) return;
        lookAheadCooldown = LOOK_AHEAD_INTERVAL;

        int lookAheadCount = 15;
        double maxRangeSq = 900; // 30 blocks

        int maxLookAhead = Math.min(pathIndex + lookAheadCount, currentPath.size() - 1);
        for (int i = maxLookAhead; i > pathIndex; i--) {
            Vec3d waypoint = currentPath.get(i);
            double distSq = playerPos.squaredDistanceTo(waypoint);

            // Only skip to waypoints within reasonable range
            if (distSq > maxRangeSq) continue;

            // Only reject shortcuts that would require a full-block jump (>1.0 up).
            // Auto-step handles up to 0.6 blocks, and the LOS check (hasQuickLOS)
            // verifies ground continuity — so on rugged slab/stair terrain, shortcuts
            // with gradual height changes are perfectly safe to take.
            double upwardDiff = waypoint.y - playerPos.y;
            if (upwardDiff > 1.0) continue;

            // Check if we can reach it directly (includes ground check)
            if (hasQuickLOS(playerPos, waypoint, client)) {
                pathIndex = i;
                PathRenderer.getInstance().setActiveIndex(pathIndex);
                break;
            }
        }
    }

    /**
     * Quick line-of-sight check with player-width, diagonal corner detection, and ground check.
     * Verifies at each step that:
     *   1. The player's body fits through (no blocking collision shapes)
     *   2. There is solid ground beneath the player's feet (prevents ledge falls)
     * Handles fractional Y (slabs/stairs) by checking collision shapes.
     */
    private boolean hasQuickLOS(Vec3d from, Vec3d to, MinecraftClient client) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5) return true;

        // Player hitbox half-width (0.6 wide total, 0.3 each side)
        double halfWidth = 0.3;
        double perpX = -dz / dist;
        double perpZ = dx / dist;

        double step = 0.3;
        int steps = (int) Math.ceil(dist / step);
        double stepX = dx / steps;
        double stepZ = dz / steps;
        double stepY = dy / steps;

        // Check center + both edges of hitbox
        double[] offsets = {0.0, -halfWidth, halfWidth};

        int prevBlockX = (int) Math.floor(from.x);
        int prevBlockZ = (int) Math.floor(from.z);

        for (int i = 1; i < steps; i++) {
            double cx = from.x + stepX * i;
            double cy = from.y + stepY * i; // interpolated feet Y (estimate only)
            double cz = from.z + stepZ * i;

            // === Ground check on center line ===
            // Find the actual walkable ground surface. Use it for body clearance
            // instead of the interpolated cy, which diverges on rugged terrain.
            double actualFeetY = cy;
            boolean hasGround = false;
            for (int yOff = 1; yOff >= -1 && !hasGround; yOff--) {
                BlockPos gp = BlockPos.ofFloored(cx, cy - 0.05 + yOff, cz);
                if (!client.world.isChunkLoaded(gp.getX() >> 4, gp.getZ() >> 4)) return false;
                net.minecraft.block.BlockState gs = client.world.getBlockState(gp);
                net.minecraft.util.shape.VoxelShape gShape = gs.getCollisionShape(client.world, gp);
                if (!gShape.isEmpty()) {
                    double groundTop = gp.getY() + gShape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                    if (groundTop >= cy - 1.5 && groundTop <= cy + 1.0) {
                        hasGround = true;
                        actualFeetY = groundTop;
                    }
                }
            }
            if (!hasGround) return false;

            // === Body clearance check (using actual ground Y) ===
            for (double offset : offsets) {
                double x = cx + perpX * offset;
                double z = cz + perpZ * offset;

                int minBlockY = (int) Math.floor(actualFeetY);
                int maxBlockY = (int) Math.floor(actualFeetY + 1.8);

                for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
                    BlockPos checkPos = BlockPos.ofFloored(x, blockY, z);
                    if (!client.world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) return false;

                    net.minecraft.block.BlockState state = client.world.getBlockState(checkPos);
                    net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(client.world, checkPos);

                    if (!shape.isEmpty()) {
                        double shapeBottom = blockY + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                        double shapeTop = blockY + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);

                        if (shapeTop > actualFeetY + 0.01 && shapeBottom < actualFeetY + 1.8) {
                            return false;
                        }
                    }

                    if (NavMeshGenerator.isNonSolidObstacle(state) || NavMeshGenerator.isHazardous(state)) {
                        return false;
                    }
                }
            }

            // Diagonal corner check on center line (also use actualFeetY)
            int blockX = (int) Math.floor(cx);
            int blockZ = (int) Math.floor(cz);
            if (blockX != prevBlockX && blockZ != prevBlockZ) {
                int iy = (int) Math.floor(actualFeetY);
                BlockPos[] corners = {
                        new BlockPos(prevBlockX, iy, blockZ),
                        new BlockPos(blockX, iy, prevBlockZ)
                };
                for (BlockPos corner : corners) {
                    for (int yOff = 0; yOff <= 1; yOff++) {
                        BlockPos checkPos = yOff == 0 ? corner : corner.up();
                        net.minecraft.block.BlockState state = client.world.getBlockState(checkPos);
                        net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(client.world, checkPos);
                        if (!shape.isEmpty()) {
                            double shapeBottom = checkPos.getY() + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                            double shapeTop = checkPos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                            if (shapeTop > actualFeetY + 0.01 && shapeBottom < actualFeetY + 1.8) {
                                return false;
                            }
                        }
                    }
                }
            }
            prevBlockX = blockX;
            prevBlockZ = blockZ;
        }
        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  Dynamic Repathing (world change detection)
    // ──────────────────────────────────────────────────────────────

    /**
     * Periodically check if blocks near upcoming path segments have changed,
     * triggering a repath if the path is now obstructed.
     */
    private void performDynamicRepath(Vec3d playerPos, MinecraftClient client) {
        dynamicRepathCooldown--;
        if (dynamicRepathCooldown > 0 || currentPath == null || client.world == null) return;
        dynamicRepathCooldown = DYNAMIC_REPATH_INTERVAL;

        // Check the next few waypoints' segments for obstructions
        int checkEnd = Math.min(pathIndex + 4, currentPath.size() - 1);
        for (int i = pathIndex; i < checkEnd; i++) {
            Vec3d from = (i == pathIndex) ? playerPos : currentPath.get(i);
            Vec3d to = currentPath.get(i + 1);

            if (!isSegmentClear(from, to, client)) {
                // Path is obstructed — trigger repath
                TobaChat.send("Path obstructed, recalculating...");
                triggerRepath(client);
                return;
            }
        }
    }

    /**
     * Check if a path segment is still clear of solid blocks.
     * Uses collision shape checks to handle fractional Y (slabs/stairs).
     */
    private boolean isSegmentClear(Vec3d from, Vec3d to, MinecraftClient client) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5) return true;

        int steps = Math.max(2, (int) Math.ceil(dist / 0.8));
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double cx = from.x + dx * t;
            double cy = from.y + dy * t; // feet Y (fractional for slabs)
            double cz = from.z + dz * t;

            int minBlockY = (int) Math.floor(cy);
            int maxBlockY = (int) Math.floor(cy + 1.8);

            for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
                BlockPos checkPos = BlockPos.ofFloored(cx, blockY, cz);
                if (!client.world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) return false;

                net.minecraft.block.BlockState state = client.world.getBlockState(checkPos);
                net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(client.world, checkPos);

                if (!shape.isEmpty()) {
                    double shapeBottom = blockY + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                    double shapeTop = blockY + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                    if (shapeTop > cy + 0.01 && shapeBottom < cy + 1.8) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Trigger a repath from current position to the target without blacklisting.
     */
    private void triggerRepath(MinecraftClient client) {
        if (repathScheduled || targetPos == null || client.world == null) return;

        repathScheduled = true;
        Vec3d startVec = client.player.getSyncedPos();
        Vec3d goalVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

        // Only use hybrid (fly fallback) if the player can actually fly
        if (canPlayerFly()) {
            PathfindingService.getInstance().findHybridPath(startVec, goalVec, client.world, maxRange.getValue())
                    .thenAccept(result -> handleHybridRepathResult(result, client));
        } else {
            PathfindingService.getInstance().findNavMeshPath(startVec, goalVec, client.world, maxRange.getValue())
                    .thenAccept(path -> {
                        client.execute(() -> {
                            repathScheduled = false;
                            if (!path.isFound() || path.getWaypoints().isEmpty()) return;
                            navMeshPath = path;
                            currentPath = path.getWaypoints();
                            pathIndex = 0;
                            lastProgressIndex = -1;
                            positionStuckTicks = 0;
                            lastStuckCheckPos = null;
                            float[] c = argbToFloats(pathColor.getValue());
                            PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
                            PathRenderer.getInstance().setPathVec3d(currentPath);
                            PathRenderer.getInstance().setActiveIndex(pathIndex);
                        });
                    });
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Waypoint Advancement
    // ──────────────────────────────────────────────────────────────

    /**
     * Advance past waypoints the player is already close to or has passed.
     * Uses a "crossing plane" approach: once the player crosses the perpendicular
     * plane at a waypoint (facing the direction of travel), that waypoint and all
     * previous ones are considered passed. This prevents backtracking.
     */
    private boolean advanceWaypoints(Vec3d playerPos) {
        boolean advanced = false;

        // Check waypoints using the "crossing plane" method
        // For each waypoint, we create an imaginary plane perpendicular to the path direction.
        // Once the player is "past" this plane (on the side toward the next waypoint),
        // the waypoint is considered passed.
        while (pathIndex < currentPath.size()) {
            Vec3d waypoint = currentPath.get(pathIndex);

            // Get direction vector for this segment
            Vec3d direction;
            if (pathIndex + 1 < currentPath.size()) {
                // Direction from current waypoint toward next waypoint
                Vec3d nextWp = currentPath.get(pathIndex + 1);
                direction = new Vec3d(
                        nextWp.x - waypoint.x,
                        0, // Ignore Y for horizontal plane check
                        nextWp.z - waypoint.z
                );
            } else if (pathIndex > 0) {
                // Last waypoint: use direction from previous waypoint
                Vec3d prevWp = currentPath.get(pathIndex - 1);
                direction = new Vec3d(
                        waypoint.x - prevWp.x,
                        0,
                        waypoint.z - prevWp.z
                );
            } else {
                // Single waypoint: use distance-based check only
                direction = null;
            }

            // Calculate if player has crossed the plane at this waypoint
            boolean crossedPlane = false;
            if (direction != null && (direction.x != 0 || direction.z != 0)) {
                // Normalize direction (horizontal only)
                double len = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
                double dirX = direction.x / len;
                double dirZ = direction.z / len;

                // Vector from waypoint to player
                double toPlayerX = playerPos.x - waypoint.x;
                double toPlayerZ = playerPos.z - waypoint.z;

                // Dot product: positive means player is "ahead" of the waypoint plane
                double dot = toPlayerX * dirX + toPlayerZ * dirZ;

                // Perpendicular (lateral) distance from the player to the path line
                // through this waypoint. If this is too large, the player "missed" the
                // waypoint and advancing via plane crossing would cause an unnecessary
                // rotation snap toward the next waypoint.
                double lateralDist = Math.abs(toPlayerX * (-dirZ) + toPlayerZ * dirX);

                // Player has crossed if they're past the plane AND within reasonable
                // lateral distance. Max lateral distance scales with segment length
                // (longer segments tolerate more drift). Minimum 1.5 blocks, max 4.0.
                double segmentLen = len; // length of the direction vector before normalization
                double maxLateral = MathHelper.clamp(segmentLen * 0.4, 1.5, 4.0);
                crossedPlane = dot > -0.1 && lateralDist < maxLateral;
            }

            // Also check distance-based reach (for cases where plane check isn't applicable)
            double dx = waypoint.x - playerPos.x;
            double dz = waypoint.z - playerPos.z;
            double dy = waypoint.y - playerPos.y;
            double hDist = Math.sqrt(dx * dx + dz * dz);

            boolean reachedByDistance;
            float wpReach = waypointReachDistance.getValue();
            if (flyMode) {
                double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                reachedByDistance = dist3d < wpReach;
            } else {
                reachedByDistance = hDist < wpReach && Math.abs(dy) < 2.0;
            }

            // Waypoint is passed if EITHER:
            // 1. Player crossed the perpendicular plane at this waypoint, OR
            // 2. Player is within reach distance of the waypoint
            if (crossedPlane || reachedByDistance) {
                pathIndex++;
                advanced = true;
            } else {
                break;
            }
        }

        return advanced;
    }

    /**
     * Squared horizontal distance (XZ plane only) for fast comparison.
     */
    private double horizontalDistanceSq(Vec3d a, Vec3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    // ──────────────────────────────────────────────────────────────
    //  Stuck Detection & Recovery
    // ──────────────────────────────────────────────────────────────

    private void handleStuckDetection(ClientPlayerEntity player, Vec3d playerPos, MinecraftClient client) {
        // Track waypoint progress
        if (pathIndex != lastProgressIndex) {
            // Waypoint advanced — clear everything
            lastProgressIndex = pathIndex;
            noProgressTicks = 0;
            positionStuckTicks = 0;
            lastStuckCheckPos = null;
            if (strafingToRecover) {
                strafingToRecover = false;
                setKeyPressed(client.options.leftKey, false);
                setKeyPressed(client.options.rightKey, false);
                setKeyPressed(client.options.backKey, false);
            }
            return; // definitely not stuck
        }

        noProgressTicks++;

        // Also track if the player's position is actually changing.
        // If the player is moving toward the waypoint (even slowly), they're not stuck.
        // Only count as stuck when BOTH pathIndex hasn't advanced AND position barely moves.
        if (lastStuckCheckPos != null) {
            double movedSq = playerPos.squaredDistanceTo(lastStuckCheckPos);
            // Threshold: less than 0.04 blocks moved (0.2 block distance) per tick
            // This catches jumping in place (Y changes but XZ doesn't advance toward goal)
            // and strafing against walls, while NOT triggering on slow but real movement.
            if (movedSq < 0.04) {
                positionStuckTicks++;
            } else {
                positionStuckTicks = Math.max(0, positionStuckTicks - 2); // decay slowly
            }
        }
        lastStuckCheckPos = playerPos;

        // Require BOTH: no waypoint progress for a while AND position barely changing.
        // This prevents false positives on long straight segments where the player IS
        // moving but just hasn't crossed the next waypoint plane yet.
        int strafeThresh = strafeThreshold.getValue();
        int stuckThresh = stuckThreshold.getValue();
        boolean isStuck = noProgressTicks >= strafeThresh && positionStuckTicks >= strafeThresh / 2;
        boolean isVeryStuck = noProgressTicks >= stuckThresh && positionStuckTicks >= stuckThresh / 2;

        // Phase 1: Try strafing (A/D) to nudge free before escalating
        if (isStuck && !isVeryStuck && !strafingToRecover && !repathingFromStuck) {
            strafingToRecover = true;
            strafeDirectionRight = random.nextBoolean();
            strafeToggleTicks = 0;
        }

        // Apply recovery input while in recovery phase.
        // Phase A (first 15 ticks): walk BACKWARDS + jump to disengage from corner.
        // Phase B (remaining): strafe side-to-side + forward + jump to wiggle free.
        if (strafingToRecover && !repathingFromStuck) {
            strafeToggleTicks++;

            if (strafeToggleTicks <= 15) {
                // Phase A: back up to clear the obstacle
                setKeyPressed(client.options.forwardKey, false);
                setKeyPressed(client.options.backKey, true);
                setKeyPressed(client.options.leftKey, false);
                setKeyPressed(client.options.rightKey, false);
                if (player.isOnGround()) {
                    setKeyPressed(client.options.jumpKey, true);
                }
            } else {
                // Phase B: strafe wiggle (toggle every 10 ticks)
                setKeyPressed(client.options.backKey, false);
                int wigglePhase = (strafeToggleTicks - 15);
                if (wigglePhase % 10 == 0) {
                    strafeDirectionRight = !strafeDirectionRight;
                }
                setKeyPressed(client.options.leftKey, !strafeDirectionRight);
                setKeyPressed(client.options.rightKey, strafeDirectionRight);
                setKeyPressed(client.options.forwardKey, true);
                if (player.isOnGround()) {
                    setKeyPressed(client.options.jumpKey, true);
                }
            }
        }

        // Phase 2: Strafing didn't work — blacklist and repath
        if (isVeryStuck && !repathingFromStuck) {
            // Stop strafing
            strafingToRecover = false;
            setKeyPressed(client.options.leftKey, false);
            setKeyPressed(client.options.rightKey, false);
            setKeyPressed(client.options.backKey, false);

            if (client.world == null || targetPos == null) {
                stop();
                return;
            }

            repathingFromStuck = true;

            // Blacklist the stuck position and the waypoint we couldn't reach.
            Vec3d stuckPos = player.getSyncedPos();
            float blRadius = blacklistRadius.getValue();
            int blDuration = blacklistDuration.getValue();
            PathfindingService.getInstance().blacklistArea(stuckPos, blRadius, blDuration);

            if (pathIndex < currentPath.size()) {
                Vec3d stuckWaypoint = currentPath.get(pathIndex);
                PathfindingService.getInstance().blacklistArea(stuckWaypoint, blRadius * 0.75, blDuration);
            }

            TobaChat.send("Stuck! Blacklisting area, finding alternate route...");

            noProgressTicks = 0;
            repathScheduled = true;

            Vec3d startVec = player.getSyncedPos();
            Vec3d goalVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);

            // Only fall back to fly if the player can actually fly
            if (canPlayerFly()) {
                PathfindingService.getInstance().findHybridPath(startVec, goalVec, client.world, maxRange.getValue())
                        .thenAccept(result -> handleStuckHybridRepathResult(result, client));
            } else {
                PathfindingService.getInstance().findNavMeshPath(startVec, goalVec, client.world, maxRange.getValue())
                        .thenAccept(path -> handleStuckRepathResult(path, client));
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Path Result Handlers
    // ──────────────────────────────────────────────────────────────

    private void handlePathResult(NavMeshPath path, MinecraftClient client) {
        client.execute(() -> {
            pathfindingInProgress = false;
            if (!path.isFound() || path.getWaypoints().isEmpty()) {
                TobaChat.send("No path found!");
                stop();
            } else {
                navMeshPath = path;
                currentPath = path.getWaypoints();
                pathIndex = 0;
                navigating = true;
                nextPauseIn = MIN_PAUSE_INTERVAL + random.nextInt(MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL);

                float[] c = argbToFloats(pathColor.getValue());
                PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
                PathRenderer.getInstance().setPathVec3d(currentPath);
                PathRenderer.getInstance().setActiveIndex(pathIndex);

                String mode = flyMode ? "Flight" : "Path";
                TobaChat.send(mode + " found! " + currentPath.size() + " waypoints, "
                        + String.format("%.1f", path.getLength()) + " blocks");
            }
        });
    }

    private void handleRepathResult(NavMeshPath path, MinecraftClient client) {
        client.execute(() -> {
            repathScheduled = false;
            if (!path.isFound() || path.getWaypoints().isEmpty()) {
                // Repath failed — keep current path
                return;
            }
            navMeshPath = path;
            currentPath = path.getWaypoints();
            pathIndex = 0;
            lastProgressIndex = -1;
            positionStuckTicks = 0;
            lastStuckCheckPos = null;
            float[] c = argbToFloats(pathColor.getValue());
            PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
            PathRenderer.getInstance().setPathVec3d(currentPath);
            PathRenderer.getInstance().setActiveIndex(pathIndex);
        });
    }

    private void handleHybridRepathResult(PathfindingService.HybridPathResult result, MinecraftClient client) {
        client.execute(() -> {
            repathScheduled = false;
            if (!result.isFound() || result.getPath().getWaypoints().isEmpty()) {
                // Repath failed — keep current path
                return;
            }
            flyMode = result.isUsedFly();
            navMeshPath = result.getPath();
            currentPath = result.getPath().getWaypoints();
            pathIndex = 0;
            lastProgressIndex = -1;
            positionStuckTicks = 0;
            lastStuckCheckPos = null;
            float[] c = argbToFloats(pathColor.getValue());
            PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
            PathRenderer.getInstance().setPathVec3d(currentPath);
            PathRenderer.getInstance().setActiveIndex(pathIndex);
        });
    }

    private void handleHybridPathResult(PathfindingService.HybridPathResult result, MinecraftClient client) {
        client.execute(() -> {
            pathfindingInProgress = false;
            if (!result.isFound() || result.getPath().getWaypoints().isEmpty()) {
                TobaChat.send("No path found (tried ground + fly)!");
                stop();
            } else {
                flyMode = result.isUsedFly();
                navMeshPath = result.getPath();
                currentPath = result.getPath().getWaypoints();
                pathIndex = 0;
                navigating = true;
                nextPauseIn = MIN_PAUSE_INTERVAL + random.nextInt(MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL);

                float[] c = argbToFloats(pathColor.getValue());
                PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
                PathRenderer.getInstance().setPathVec3d(currentPath);
                PathRenderer.getInstance().setActiveIndex(pathIndex);

                String mode = flyMode ? "Flight" : "Ground";
                TobaChat.send(mode + " path found! " + currentPath.size() + " waypoints, "
                        + String.format("%.1f", result.getPath().getLength()) + " blocks");
            }
        });
    }

    private void handleStuckRepathResult(NavMeshPath path, MinecraftClient client) {
        client.execute(() -> {
            repathScheduled = false;
            repathingFromStuck = false;
            if (!path.isFound() || path.getWaypoints().isEmpty()) {
                TobaChat.send("No alternate path found!");
                stop();
            } else {
                navMeshPath = path;
                currentPath = path.getWaypoints();
                pathIndex = 0;
                lastProgressIndex = -1;
                positionStuckTicks = 0;
                lastStuckCheckPos = null;
                float[] c = argbToFloats(pathColor.getValue());
                PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
                PathRenderer.getInstance().setPathVec3d(currentPath);
                PathRenderer.getInstance().setActiveIndex(pathIndex);
                TobaChat.send("Alternate path found! " + currentPath.size() + " waypoints");
            }
        });
    }

    private void handleStuckHybridRepathResult(PathfindingService.HybridPathResult result, MinecraftClient client) {
        client.execute(() -> {
            repathScheduled = false;
            repathingFromStuck = false;
            if (!result.isFound() || result.getPath().getWaypoints().isEmpty()) {
                TobaChat.send("No alternate path found (tried ground + fly)!");
                stop();
            } else {
                flyMode = result.isUsedFly();
                navMeshPath = result.getPath();
                currentPath = result.getPath().getWaypoints();
                pathIndex = 0;
                lastProgressIndex = -1;
                positionStuckTicks = 0;
                lastStuckCheckPos = null;
                float[] c = argbToFloats(pathColor.getValue());
                PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
                PathRenderer.getInstance().setPathVec3d(currentPath);
                PathRenderer.getInstance().setActiveIndex(pathIndex);
                String mode = flyMode ? "Flight" : "Ground";
                TobaChat.send(mode + " alternate path found! " + currentPath.size() + " waypoints");
            }
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  ESP Visualization
    // ──────────────────────────────────────────────────────────────

    private void updatePathESP() {
        if (!showPathESP.getValue() || currentPath == null) return;

        ESPRenderer espRenderer = ESPRenderer.getInstance();
        espRenderer.beginPathScan();
        float[] c = argbToFloats(pathColor.getValue());

        // Start from one before pathIndex so the current segment's markers stay visible
        int espStart = Math.max(0, pathIndex - 1);
        for (int i = espStart; i < currentPath.size(); i++) {
            Vec3d waypoint = currentPath.get(i);

            // Small marker at the waypoint node
            espRenderer.addPathWaypoint(waypoint, c[0], c[1], c[2], 0.6f);

            // Highlight the ground block below
            BlockPos ground = BlockPos.ofFloored(waypoint.x, waypoint.y, waypoint.z).down();
            espRenderer.addPathBlock(ground, c[0], c[1], c[2], 0.3f);

            // Line rendering is handled by PathRenderer (thin quads) —
            // no addPathLine here to avoid doubling up with thick ESP lines
        }

        // Optional: render NavMesh polygon outlines for debugging
        if (debugNavMesh.getValue() && !flyMode) {
            NavMesh mesh = PathfindingService.getInstance().getLastNavMesh();
            if (mesh != null && navMeshPath != null) {
                for (NavPoly poly : navMeshPath.getCorridor()) {
                    for (int x = poly.getMinX(); x <= poly.getMaxX(); x++) {
                        for (int z = poly.getMinZ(); z <= poly.getMaxZ(); z++) {
                            espRenderer.addPathBlock(
                                    new BlockPos(x, (int) Math.floor(poly.getY()) - 1, z),
                                    0.2f, 0.8f, 0.2f, 0.15f
                            );
                        }
                    }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Segmented Pathfinding
    // ──────────────────────────────────────────────────────────────

    /**
     * Compute an intermediate goal position along the line from start to final goal,
     * at approximately segmentSize blocks of horizontal distance.
     */
    private Vec3d computeIntermediateGoal(Vec3d start, Vec3d goal, int segmentSize) {
        double dx = goal.x - start.x;
        double dz = goal.z - start.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        if (hDist <= segmentSize) return goal;

        double ratio = (double) segmentSize / hDist;
        double dy = goal.y - start.y;

        return new Vec3d(
                start.x + dx * ratio,
                start.y + dy * ratio,
                start.z + dz * ratio
        );
    }

    /**
     * Request the next path segment asynchronously. Called when the player
     * has progressed through ~70% of the current segment.
     */
    private void requestNextSegment(MinecraftClient client) {
        if (nextSegmentRequested || finalGoal == null || client.world == null || currentPath == null) return;
        nextSegmentRequested = true;

        // Push settings before async call
        NavMeshPathfinder.setPreferFlatGround(preferFlatGround.getValue());
        NavMeshPathfinder.setEnablePathCoarsening(enablePathCoarsening.getValue());

        Vec3d segmentStart = currentPath.get(currentPath.size() - 1);
        Vec3d goalVec = new Vec3d(finalGoal.getX() + 0.5, finalGoal.getY(), finalGoal.getZ() + 0.5);

        double remainingDist = segmentStart.distanceTo(goalVec);
        int segSize = segmentSizeSetting.getValue();

        Vec3d segmentGoal;
        boolean isFinalSegment;
        if (remainingDist <= segSize * 1.3) {
            segmentGoal = goalVec;
            isFinalSegment = true;
        } else {
            segmentGoal = computeIntermediateGoal(segmentStart, goalVec, segSize);
            isFinalSegment = false;
        }

        boolean finalSeg = isFinalSegment;

        if (canPlayerFly()) {
            PathfindingService.getInstance().findHybridPath(segmentStart, segmentGoal, client.world, segSize + 20)
                    .thenAccept(result -> handleSegmentResult(result.getPath(), result.isUsedFly(), finalSeg, client));
        } else {
            PathfindingService.getInstance().findNavMeshPath(segmentStart, segmentGoal, client.world, segSize + 20)
                    .thenAccept(path -> handleSegmentResult(path, false, finalSeg, client));
        }
    }

    /**
     * Handle a completed segment result — append new waypoints to the current path.
     */
    private void handleSegmentResult(NavMeshPath newPath, boolean usedFly, boolean isFinalSegment, MinecraftClient client) {
        client.execute(() -> {
            nextSegmentRequested = false;

            if (!newPath.isFound() || newPath.getWaypoints().isEmpty()) {
                if (isFinalSegment) {
                    TobaChat.send("Segment failed near goal! Stopping.");
                    stop();
                } else {
                    // Mid-path segment failure — fall back to full non-segmented repath
                    TobaChat.send("Segment failed, attempting full repath...");
                    segmentedMode = false;
                    finalGoal = null;
                    repathScheduled = true;
                    if (targetPos != null) {
                        Vec3d startVec = client.player != null ? client.player.getSyncedPos() : currentPath.get(pathIndex);
                        Vec3d goalVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
                        if (canPlayerFly()) {
                            PathfindingService.getInstance().findHybridPath(startVec, goalVec, client.world, maxRange.getValue())
                                    .thenAccept(result -> handleHybridRepathResult(result, client));
                        } else {
                            PathfindingService.getInstance().findNavMeshPath(startVec, goalVec, client.world, maxRange.getValue())
                                    .thenAccept(path -> handleRepathResult(path, client));
                        }
                    }
                }
                return;
            }

            // Append new waypoints (skip first — overlaps with last of current segment)
            List<Vec3d> newWaypoints = newPath.getWaypoints();
            ArrayList<Vec3d> mergedPath = new ArrayList<>(currentPath);
            int skipFirst = (mergedPath.size() > 0 && newWaypoints.size() > 0) ? 1 : 0;
            for (int i = skipFirst; i < newWaypoints.size(); i++) {
                mergedPath.add(newWaypoints.get(i));
            }

            currentPath = mergedPath;
            navMeshPath = null; // corridor is no longer valid for the merged path

            if (usedFly) flyMode = true;
            if (isFinalSegment) {
                segmentedMode = false;
                finalGoal = null;
            }

            // Update renderer with the extended path
            float[] c = argbToFloats(pathColor.getValue());
            PathRenderer.getInstance().setColor(c[0], c[1], c[2], 0.8f);
            PathRenderer.getInstance().setPathVec3d(currentPath);
            PathRenderer.getInstance().setActiveIndex(pathIndex);

            TobaChat.send("Segment appended! " + currentPath.size() + " total waypoints");
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Arrival & State Management
    // ──────────────────────────────────────────────────────────────

    private void arrive() {
        if (segmentedMode && finalGoal != null) {
            // Reached end of current segment but not the final goal.
            // Request next segment if not already in flight.
            if (!nextSegmentRequested) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    requestNextSegment(client);
                }
            }
            // Wait for the next segment to arrive
            return;
        }

        TobaChat.send("Arrived at destination!");
        Runnable callback = onArrivalCallback;
        stop();
        if (callback != null) {
            callback.run();
        }
    }

    private void resetState(BlockPos target, Runnable onArrival, boolean fly) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        if (!isEnabled()) setEnabled(true);

        this.targetPos = target;
        this.navigating = false;
        this.currentPath = null;
        this.navMeshPath = null;
        this.pathIndex = 0;
        this.lastProgressIndex = -1;
        this.noProgressTicks = 0;
        this.positionStuckTicks = 0;
        this.lastStuckCheckPos = null;
        this.repathScheduled = false;
        this.repathingFromStuck = false;
        this.strafingToRecover = false;
        this.onArrivalCallback = onArrival;
        this.flyMode = fly;
        this.airborne = false;
        this.pauseTicks = 0;
        this.dynamicRepathCooldown = 0;
        this.lookAheadCooldown = 0;
        this.segmentedMode = false;
        this.finalGoal = null;
        this.nextSegmentRequested = false;
        this.nextPauseIn = MIN_PAUSE_INTERVAL + random.nextInt(MAX_PAUSE_INTERVAL - MIN_PAUSE_INTERVAL);
        this.speedVariation = 0.8f + random.nextFloat() * 0.2f;
    }

    // ──────────────────────────────────────────────────────────────
    //  Utility
    // ──────────────────────────────────────────────────────────────

    @Override
    protected void onDisable() {
        stop();
    }

    private void setKeyPressed(KeyBinding keyBinding, boolean pressed) {
        keyBinding.setPressed(pressed);
    }

    private void releaseKeys() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            setKeyPressed(client.options.forwardKey, false);
            setKeyPressed(client.options.backKey, false);
            setKeyPressed(client.options.sprintKey, false);
            setKeyPressed(client.options.jumpKey, false);
            setKeyPressed(client.options.sneakKey, false);
            setKeyPressed(client.options.leftKey, false);
            setKeyPressed(client.options.rightKey, false);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Accessors
    // ──────────────────────────────────────────────────────────────

    public boolean isNavigating() { return navigating || pathfindingInProgress; }
    public boolean isFlyMode() { return flyMode; }

    /**
     * Check if the player is actually allowed to fly (creative/spectator mode).
     * Servers can restrict flying even in survival, so we check the player's
     * abilities rather than assuming fly is always available as a fallback.
     */
    private boolean canPlayerFly() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        return client.player.getAbilities().allowFlying;
    }
    public BlockPos getTargetPos() { return targetPos; }
    public NavMeshPath getNavMeshPath() { return navMeshPath; }

    public static float[] argbToFloats(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return new float[]{r, g, b};
    }
}
