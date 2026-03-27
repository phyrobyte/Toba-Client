package dev.toba.client.api.pathfinding;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import dev.toba.client.api.pathfinding.navmesh.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Top-level pathfinding service. Delegates to NavMesh generation and pathfinding.
 * Manages a blacklist of problem areas that the pathfinder will avoid.
 * <p>
 * NavMesh generation and A* run on a dedicated background thread to avoid
 * blocking the render/game thread. Result callbacks are dispatched to the
 * caller via CompletableFuture; callers must use client.execute() for any
 * Minecraft state mutations inside thenAccept().
 */
public class PathfindingService {
    private static PathfindingService instance;

    // Single daemon thread for all pathfinding work — keeps tasks serialized so
    // NavPoly.nextId and the generator/pathfinder instances are not shared concurrently.
    private static final ExecutorService PATHFINDING_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "toba-pathfinder");
        t.setDaemon(true);
        return t;
    });

    private final NavMeshGenerator generator = new NavMeshGenerator();
    private final NavMeshPathfinder pathfinder = new NavMeshPathfinder();
    private final FlyPathfinder flyPathfinder = new FlyPathfinder();

    // Blacklist: areas where the player got stuck, temporarily penalized.
    // CopyOnWriteArrayList allows safe concurrent iteration from the pathfinding
    // thread while the main thread adds/removes entries.
    private final List<BlacklistEntry> blacklist = new CopyOnWriteArrayList<>();

    // Last results for debug/rendering access — volatile for cross-thread visibility
    // (written on pathfinding thread, read on main/render thread).
    private volatile NavMeshPath lastNavMeshPath = null;
    private volatile NavMesh lastNavMesh = null;
    private volatile List<BlockPos> lastBlockPath = null;

    // Tick counter for blacklist expiry — volatile so pathfinding thread sees current value
    private volatile long currentTick = 0;

    public static PathfindingService getInstance() {
        if (instance == null) instance = new PathfindingService();
        return instance;
    }

    /**
     * Increment the tick counter. Called from TobaClient each tick.
     */
    public void tick() {
        currentTick++;
        // Clean expired blacklist entries
        blacklist.removeIf(entry -> entry.isExpired(currentTick));
    }
   
    /**
     * Find a path using NavMesh pathfinding with funnel smoothing.
     * Returns smooth Vec3d waypoints with diagonal shortcuts.
     * Runs asynchronously on the pathfinding thread — use thenAccept + client.execute()
     * to handle the result on the main thread.
     */
    public CompletableFuture<NavMeshPath> findNavMeshPath(Vec3d start, Vec3d goal,
                                                          ClientWorld world, int maxRange) {
        return CompletableFuture.supplyAsync(
                () -> findNavMeshPathSync(start, goal, world, maxRange),
                PATHFINDING_EXECUTOR);
    }

    /**
     * Synchronous NavMesh pathfinding.
     */
    public NavMeshPath findNavMeshPathSync(Vec3d start, Vec3d goal, ClientWorld world, int maxRange) {
        // Find ground for start and goal
        Vec3d startGround = findGroundVec3d(start, world);
        Vec3d goalGround = findGroundVec3d(goal, world);

        if (startGround == null || goalGround == null) {
            return NavMeshPath.empty();
        }

        // Generate NavMesh for the region
        NavMesh mesh = generator.generate(world, startGround, goalGround, maxRange);
        lastNavMesh = mesh;

        if (mesh.getPolyCount() == 0) {
            return NavMeshPath.empty();
        }

        // Apply blacklist penalties to polygons
        applyBlacklistPenalties(mesh);

        // Find path through NavMesh (with line-of-sight validation)
        NavMeshPath result = pathfinder.findPath(mesh, startGround, goalGround, currentTick, world);
        lastNavMeshPath = result;

        return result;
    }

    // ──────────────────────────────────────────────────────────────
    //  3D Flying Pathfinding
    // ──────────────────────────────────────────────────────────────

    /**
     * Find a 3D flight path through open air. Uses voxel-grid A* with
     * 26-connected neighbors and 3D line-of-sight smoothing.
     * Runs asynchronously on the pathfinding thread.
     */
    public CompletableFuture<NavMeshPath> findFlyPath(Vec3d start, Vec3d goal,
                                                       ClientWorld world, int maxRange) {
        return CompletableFuture.supplyAsync(() -> {
            NavMeshPath result = flyPathfinder.findPath(start, goal, world, maxRange);
            lastNavMeshPath = result;
            return result;
        }, PATHFINDING_EXECUTOR);
    }

    // ──────────────────────────────────────────────────────────────
    //  Hybrid Pathfinding (ground + fly fallback)
    // ──────────────────────────────────────────────────────────────

    /**
     * Find a hybrid path: tries ground (NavMesh) first, falls back to fly if ground fails.
     * Returns a HybridPathResult indicating which mode was used and the path.
     * Runs asynchronously on the pathfinding thread.
     */
    public CompletableFuture<HybridPathResult> findHybridPath(Vec3d start, Vec3d goal,
                                                                ClientWorld world, int maxRange) {
        return CompletableFuture.supplyAsync(
                () -> findHybridPathSync(start, goal, world, maxRange),
                PATHFINDING_EXECUTOR);
    }

    /**
     * Synchronous hybrid pathfinding.
     */
    public HybridPathResult findHybridPathSync(Vec3d start, Vec3d goal,
                                                 ClientWorld world, int maxRange) {
        // Try ground pathfinding first
        NavMeshPath groundPath = findNavMeshPathSync(start, goal, world, maxRange);
        if (groundPath.isFound() && !groundPath.getWaypoints().isEmpty()) {
            return new HybridPathResult(groundPath, false);
        }

        // Ground failed — try fly pathfinding
        NavMeshPath flyPath = flyPathfinder.findPath(start, goal, world, maxRange);
        lastNavMeshPath = flyPath;
        if (flyPath.isFound() && !flyPath.getWaypoints().isEmpty()) {
            return new HybridPathResult(flyPath, true);
        }

        // Both failed
        return new HybridPathResult(NavMeshPath.empty(), false);
    }

    /**
     * Result of hybrid pathfinding — includes which mode was used.
     */
    public static class HybridPathResult {
        private final NavMeshPath path;
        private final boolean usedFly;

        public HybridPathResult(NavMeshPath path, boolean usedFly) {
            this.path = path;
            this.usedFly = usedFly;
        }

        public NavMeshPath getPath() { return path; }
        public boolean isUsedFly() { return usedFly; }
        public boolean isFound() { return path.isFound(); }
    }

    // ──────────────────────────────────────────────────────────────
    //  Legacy API (backward compatibility)
    // ──────────────────────────────────────────────────────────────

    /**
     * Legacy A* pathfinding that returns BlockPos list.
     * Internally uses NavMesh and converts waypoints to BlockPos.
     */
    public CompletableFuture<List<BlockPos>> findPathAsync(BlockPos start, BlockPos goal,
                                                           ClientWorld world, int maxRange) {
        Vec3d startVec = new Vec3d(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        Vec3d goalVec = new Vec3d(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);

        return CompletableFuture.supplyAsync(() -> {
            NavMeshPath navPath = findNavMeshPathSync(startVec, goalVec, world, maxRange);

            List<BlockPos> blockPath;
            if (navPath.isFound()) {
                blockPath = new ArrayList<>();
                for (Vec3d wp : navPath.getWaypoints()) {
                    blockPath.add(BlockPos.ofFloored(wp.x, wp.y, wp.z));
                }
            } else {
                blockPath = Collections.emptyList();
            }

            lastBlockPath = blockPath;
            return blockPath;
        }, PATHFINDING_EXECUTOR);
    }

    // ──────────────────────────────────────────────────────────────
    //  Blacklist System
    // ──────────────────────────────────────────────────────────────

    /**
     * Blacklist an area around a position. Polygons overlapping this area
     * will receive a heavy traversal cost penalty during pathfinding.
     *
     * @param center        Center of the blacklist area (world coords)
     * @param radius        Radius in blocks
     * @param durationTicks How long the blacklist lasts
     */
    public void blacklistArea(Vec3d center, double radius, long durationTicks) {
        blacklist.add(new BlacklistEntry(center, radius, currentTick + durationTicks));
    }

    /**
     * Clear all blacklisted areas.
     */
    public void clearBlacklist() {
        blacklist.clear();
    }

    /**
     * Get the number of active blacklist entries.
     */
    public int getBlacklistCount() {
        return blacklist.size();
    }

    /**
     * Apply blacklist penalties to polygons in the mesh.
     */
    private void applyBlacklistPenalties(NavMesh mesh) {
        if (blacklist.isEmpty()) return;

        for (NavPoly poly : mesh.getAllPolygons()) {
            for (BlacklistEntry entry : blacklist) {
                if (entry.isExpired(currentTick)) continue;
                Vec3d center = poly.getCenter();
                if (poly.overlapsCircle(entry.center.x, entry.center.z, entry.radius)) {
                    // Apply moderate cost penalty (10x base cost) to discourage A*
                    // from routing through the stuck area while keeping detours
                    // reasonable. 50x was too aggressive, pushing paths 5+ blocks away.
                    poly.setTraversalCost(poly.getTraversalCost() * 10.0);
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Ground finding
    // ──────────────────────────────────────────────────────────────

    /**
     * Find the ground position (walkable feet level) near a Vec3d position.
     * Returns the actual surface Y including fractional heights for slabs/stairs.
     * <p>
     * Searches outward from the given Y in both directions simultaneously,
     * checking the closest Y values first. Uses two strategies:
     * 1. getSurfaceY (checks block BELOW feetPos as ground)
     * 2. Direct collision check (checks block AT feetPos as ground, player stands on top)
     * This covers both cases: player standing one block above ground, and coordinates
     * that point directly at the ground block itself.
     */
    private Vec3d findGroundVec3d(Vec3d pos, ClientWorld world) {
        BlockPos blockPos = BlockPos.ofFloored(pos.x, pos.y, pos.z);

        // Search outward from the given Y: check dy=0, then alternating up/down.
        for (int dist = 0; dist <= 10; dist++) {
            for (int sign = 0; sign <= 1; sign++) {
                int dy = sign == 0 ? dist : -dist;
                if (dy < 0 && dist == 0) continue; // skip duplicate at 0

                BlockPos check = blockPos.add(0, dy, 0);

                // Strategy 1: standard getSurfaceY (checks feetPos.down() as ground)
                double surfaceY = NavMeshGenerator.getSurfaceY(check, world);
                if (!Double.isNaN(surfaceY)) {
                    return new Vec3d(pos.x, surfaceY, pos.z);
                }

                // Strategy 2: treat the block AT 'check' as ground itself
                // (covers cases where the Y coordinate points at the ground block,
                // not the air above it)
                if (world.isChunkLoaded(check.getX() >> 4, check.getZ() >> 4)) {
                    net.minecraft.block.BlockState state = world.getBlockState(check);
                    net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(world, check);
                    if (!shape.isEmpty() && !NavMeshGenerator.isNonSolidObstacle(state)
                            && !NavMeshGenerator.isHazardous(state)) {
                        double top = check.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                        // Verify clearance above: player needs 2 blocks of headroom
                        BlockPos feetPos = BlockPos.ofFloored(pos.x, top, pos.z);
                        double checkSurface = NavMeshGenerator.getSurfaceY(feetPos, world);
                        if (!Double.isNaN(checkSurface) && Math.abs(checkSurface - top) < 0.1) {
                            return new Vec3d(pos.x, top, pos.z);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the ground position (walkable feet level) near a BlockPos.
     */
    public BlockPos findGround(BlockPos pos, ClientWorld world) {
        // Search outward from the given Y (closest first)
        for (int dist = 0; dist <= 10; dist++) {
            if (dist >= 0) {
                BlockPos check = pos.add(0, dist, 0);
                if (NavMeshGenerator.isWalkable(check, world)) return check;
            }
            if (dist > 0) {
                BlockPos check = pos.add(0, -dist, 0);
                if (NavMeshGenerator.isWalkable(check, world)) return check;
            }
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    //  Accessors
    // ──────────────────────────────────────────────────────────────

    public NavMeshPath getLastNavMeshPath() { return lastNavMeshPath; }
    public NavMesh getLastNavMesh() { return lastNavMesh; }
    public List<BlockPos> getLastPath() { return lastBlockPath; }
    public long getCurrentTick() { return currentTick; }

    // ──────────────────────────────────────────────────────────────
    //  Blacklist Entry
    // ──────────────────────────────────────────────────────────────

    private static class BlacklistEntry {
        final Vec3d center;
        final double radius;
        final long expiryTick;

        BlacklistEntry(Vec3d center, double radius, long expiryTick) {
            this.center = center;
            this.radius = radius;
            this.expiryTick = expiryTick;
        }

        boolean isExpired(long currentTick) {
            return currentTick >= expiryTick;
        }
    }
}
