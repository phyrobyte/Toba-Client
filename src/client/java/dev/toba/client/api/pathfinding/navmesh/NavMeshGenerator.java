package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

import java.util.*;

/**
 * Generates a NavMesh by scanning the Minecraft world for walkable terrain,
 * merging contiguous walkable blocks into rectangular polygons (greedy meshing),
 * and computing adjacency (shared edges/portals) between polygons.
 */
public class NavMeshGenerator {

    private static final int PADDING = 24;
    // Maximum polygon dimension (width or depth) in blocks.
    // Smaller polygons produce more portals/edges, giving the funnel algorithm
    // more waypoints for accurate navigation in tight corridors and mazes.
    // 6 blocks balances corridor resolution with polygon count — reduces
    // total polygons ~2.25x vs 4 (fewer O(n²) adjacency comparisons).
    private static final int MAX_POLY_SIZE = 6;

    // Movement parameters - can be modified for special abilities like God Potion
    private static volatile int maxJumpHeight = 1;    // Default: 1 block step-up
    private static volatile int maxFallHeight = 3;    // Default: 3 block safe fall

    /**
     * Configure movement parameters for enhanced abilities (e.g., God Potion).
     * @param jumpHeight Maximum jump/step-up height in blocks
     * @param fallHeight Maximum safe fall height in blocks
     */
    public static void setMovementParams(int jumpHeight, int fallHeight) {
        maxJumpHeight = jumpHeight;
        maxFallHeight = fallHeight;
    }

    /**
     * Reset movement parameters to default vanilla values.
     */
    public static void resetMovementParams() {
        maxJumpHeight = 2;
        maxFallHeight = 10;
    }

    /**
     * Get the current max jump height.
     */
    public static int getMaxJumpHeight() {
        return maxJumpHeight;
    }

    /**
     * Get the current max fall height.
     */
    public static int getMaxFallHeight() {
        return maxFallHeight;
    }

    /**
     * Generate a NavMesh for the region between start and goal.
     *
     * @param world    The client world to scan
     * @param start    Start position (world coords)
     * @param goal     Goal position (world coords)
     * @param maxRange Maximum pathfinding range
     * @return A NavMesh with walkable polygons and adjacency edges
     */
    public NavMesh generate(ClientWorld world, Vec3d start, Vec3d goal, int maxRange) {
        NavPoly.resetIdCounter();

        // Step 1: Compute scan bounds.
        //
        // When start and goal share the same XZ (e.g., top and bottom of a spiral cave),
        // the standard bounding box is a single point. The base PADDING=24 only adds
        // 24 blocks around that point — not enough for a large spiral that winds
        // 30+ blocks from its vertical axis. Scale the horizontal padding with the
        // Y distance so large vertical descents capture the full spiral footprint.
        // Cap at 64 to avoid excessive scans that would freeze the client thread.
        int yDiff = (int) Math.abs(start.y - goal.y);
        int scanPad = Math.min(Math.max(PADDING, PADDING + yDiff / 3), 64);
        // Respect the maxRange setting (don't scan more than the user configured)
        scanPad = Math.min(scanPad, maxRange);

        int scanMinX = (int) Math.floor(Math.min(start.x, goal.x)) - scanPad;
        int scanMaxX = (int) Math.ceil(Math.max(start.x, goal.x)) + scanPad;
        int scanMinZ = (int) Math.floor(Math.min(start.z, goal.z)) - scanPad;
        int scanMaxZ = (int) Math.ceil(Math.max(start.z, goal.z)) + scanPad;
        int scanMinY = (int) Math.floor(Math.min(start.y, goal.y)) - 20;
        int scanMaxY = (int) Math.ceil(Math.max(start.y, goal.y)) + 20;

        // Clamp range
        int centerX = (scanMinX + scanMaxX) / 2;
        int centerZ = (scanMinZ + scanMaxZ) / 2;
        scanMinX = Math.max(scanMinX, centerX - maxRange);
        scanMaxX = Math.min(scanMaxX, centerX + maxRange);
        scanMinZ = Math.max(scanMinZ, centerZ - maxRange);
        scanMaxZ = Math.min(scanMaxZ, centerZ + maxRange);

        int sizeX = scanMaxX - scanMinX + 1;
        int sizeZ = scanMaxZ - scanMinZ + 1;

        // Step 2: Scan all walkable surfaces with fractional Y (slabs, stairs, full blocks).
        // For each XZ column, find every walkable surface between scanMinY and scanMaxY.
        // A walkable surface exists where:
        //   - A block below has a collision shape top at some Y height
        //   - The space above (player height = 2 blocks) is clear
        // This produces WalkableSurface entries with fractional Y for slabs/stairs.
        Map<Long, List<WalkableSurface>> surfacesByY = new HashMap<>();

        for (int lx = 0; lx < sizeX; lx++) {
            for (int lz = 0; lz < sizeZ; lz++) {
                int wx = scanMinX + lx;
                int wz = scanMinZ + lz;

                for (int by = scanMinY - 1; by <= scanMaxY; by++) {
                    double surfaceY = getWalkableSurfaceY(new BlockPos(wx, by, wz), world);
                    if (Double.isNaN(surfaceY)) continue;

                    // surfaceY is the feet position (top of the ground block)
                    if (surfaceY < scanMinY || surfaceY > scanMaxY + 1) continue;

                    // Quantize Y to avoid floating point noise — round to nearest 0.0625 (1/16 block)
                    long quantizedY = Math.round(surfaceY * 16.0);

                    surfacesByY.computeIfAbsent(quantizedY, k -> new ArrayList<>())
                            .add(new WalkableSurface(lx, lz, surfaceY));
                }
            }
        }

        // Step 3: Greedy merge per quantized Y layer
        List<NavPoly> allPolys = new ArrayList<>();

        for (Map.Entry<Long, List<WalkableSurface>> entry : surfacesByY.entrySet()) {
            List<WalkableSurface> surfaces = entry.getValue();
            double layerY = surfaces.get(0).y; // all surfaces in this group have ~same Y

            // Build walkable grid for this Y layer
            boolean[][] walkable = new boolean[sizeX][sizeZ];
            for (WalkableSurface s : surfaces) {
                walkable[s.lx][s.lz] = true;
            }

            List<NavPoly> layerPolys = greedyMerge(walkable, scanMinX, scanMinZ, sizeX, sizeZ, layerY);
            allPolys.addAll(layerPolys);
        }

        if (allPolys.isEmpty()) {
            return new NavMesh(Collections.emptyList());
        }

        // Step 3.5: Compute wall clearance per polygon via BFS distance transform.
        // Used by A* to prefer center-of-corridor paths over wall-hugging ones.
        computeWallClearance(allPolys, surfacesByY, sizeX, sizeZ, scanMinX, scanMinZ);

        // Step 4: Find adjacencies (same Y layer — within 0.001 tolerance)
        findSameLayerAdjacencies(allPolys);

        // Step 5: Find Y-transition adjacencies (handles fractional height differences)
        findYTransitionAdjacencies(allPolys, world);

        return new NavMesh(allPolys);
    }

    /**
     * Get the Y coordinate where a player would stand if walking on the block at the given position.
     * Returns NaN if the position is not walkable.
     * <p>
     * The walking surface Y = blockY + top of collision shape of the ground block.
     * For full blocks this is blockY + 1.0, for bottom slabs it's blockY + 0.5, etc.
     */
    private double getWalkableSurfaceY(BlockPos groundPos, ClientWorld world) {
        if (!world.isChunkLoaded(groundPos.getX() >> 4, groundPos.getZ() >> 4)) return Double.NaN;

        BlockState groundState = world.getBlockState(groundPos);

        // Ground must have a collision shape
        VoxelShape groundShape = groundState.getCollisionShape(world, groundPos);
        if (groundShape.isEmpty()) return Double.NaN;

        // Non-solid obstacles and hazards can't be stood on
        if (isNonSolidObstacle(groundState)) return Double.NaN;
        if (isHazardous(groundState)) return Double.NaN;

        // Get the top of the ground block's collision shape
        double shapeTop = groundShape.getMax(Direction.Axis.Y);
        double surfaceY = groundPos.getY() + shapeTop;

        // Feet position is at surfaceY. Check clearance for player body (2 blocks tall).
        // We need to check all blocks that the player's body would occupy.
        // Player feet are at surfaceY, head top is at surfaceY + 1.8.
        // Check blocks from feet level up to head level.
        int feetBlockY = (int) Math.floor(surfaceY);
        int headTopBlockY = (int) Math.floor(surfaceY + 1.8);

        for (int checkY = feetBlockY; checkY <= headTopBlockY; checkY++) {
            BlockPos checkPos = new BlockPos(groundPos.getX(), checkY, groundPos.getZ());
            // Don't check the ground block itself (the player stands ON it, not IN it)
            if (checkY == groundPos.getY()) {
                // The player's feet might be inside the ground block's space for non-full blocks
                // (e.g., standing on a slab at Y=64, feet are at Y=64.5, which is in block Y=64)
                // This is fine — the collision shape defines where the player stands
                continue;
            }
            BlockState checkState = world.getBlockState(checkPos);
            VoxelShape checkShape = checkState.getCollisionShape(world, checkPos);

            if (!checkShape.isEmpty()) {
                // There's a collision above — check if it actually blocks the player
                double blockBottom = checkPos.getY() + checkShape.getMin(Direction.Axis.Y);
                double blockTop = checkPos.getY() + checkShape.getMax(Direction.Axis.Y);

                // Player body extends from surfaceY to surfaceY + 1.8
                // If the collision's top is above the feet level and its bottom is below the head, it blocks
                if (blockTop > surfaceY + 0.1 && blockBottom < surfaceY + 1.8) {
                    return Double.NaN; // Not enough headroom
                }
            }

            // Non-solid obstacles and hazards block even without collision shape
            if (isNonSolidObstacle(checkState)) return Double.NaN;
            if (isHazardous(checkState)) return Double.NaN;
        }

        return surfaceY;
    }

    /**
     * Small data class for a walkable surface at a specific grid position.
     */
    private static class WalkableSurface {
        final int lx, lz;
        final double y;
        WalkableSurface(int lx, int lz, double y) {
            this.lx = lx;
            this.lz = lz;
            this.y = y;
        }
    }

    /**
     * Greedy rectangle merging: merge contiguous walkable cells into maximal rectangles.
     */
    private List<NavPoly> greedyMerge(boolean[][] walkable, int originX, int originZ,
                                       int sizeX, int sizeZ, double y) {
        List<NavPoly> polys = new ArrayList<>();
        boolean[][] visited = new boolean[sizeX][sizeZ];

        for (int lx = 0; lx < sizeX; lx++) {
            for (int lz = 0; lz < sizeZ; lz++) {
                if (!walkable[lx][lz] || visited[lx][lz]) continue;

                // Expand right (capped by MAX_POLY_SIZE)
                int endX = lx;
                while (endX + 1 < sizeX && (endX - lx + 1) < MAX_POLY_SIZE
                        && walkable[endX + 1][lz] && !visited[endX + 1][lz]) {
                    endX++;
                }

                // Expand down (capped by MAX_POLY_SIZE)
                int endZ = lz;
                boolean canExpand = true;
                while (canExpand && endZ + 1 < sizeZ && (endZ - lz + 1) < MAX_POLY_SIZE) {
                    for (int cx = lx; cx <= endX; cx++) {
                        if (!walkable[cx][endZ + 1] || visited[cx][endZ + 1]) {
                            canExpand = false;
                            break;
                        }
                    }
                    if (canExpand) endZ++;
                }

                // Mark visited
                for (int cx = lx; cx <= endX; cx++) {
                    for (int cz = lz; cz <= endZ; cz++) {
                        visited[cx][cz] = true;
                    }
                }

                // Create polygon with world coordinates
                polys.add(new NavPoly(
                        originX + lx, originZ + lz,
                        originX + endX, originZ + endZ,
                        y
                ));
            }
        }
        return polys;
    }

    /**
     * Find adjacencies between polygons on the same Y layer.
     * Two polygons are adjacent if they share a contiguous edge.
     * Uses spatial hashing to avoid O(n²) all-pairs comparison — only
     * checks polygons that could possibly be adjacent (within 1 block).
     */
    private void findSameLayerAdjacencies(List<NavPoly> polys) {
        // Group polygons by quantized Y layer
        Map<Long, List<NavPoly>> byY = new HashMap<>();
        for (NavPoly poly : polys) {
            long qy = Math.round(poly.getY() * 16.0);
            byY.computeIfAbsent(qy, k -> new ArrayList<>()).add(poly);
        }

        for (List<NavPoly> layerPolys : byY.values()) {
            // Spatial hash: key = "x,z" of each block cell, value = polygon.
            // Insert each polygon's border cells (edges ±1) into the hash so
            // only spatially close polygons are compared.
            Map<Long, List<NavPoly>> spatialHash = new HashMap<>();
            for (NavPoly poly : layerPolys) {
                // Insert border cells: the edges and 1 block outside each edge
                for (int x = poly.getMinX() - 1; x <= poly.getMaxX() + 1; x++) {
                    insertSpatialHash(spatialHash, x, poly.getMinZ() - 1, poly);
                    insertSpatialHash(spatialHash, x, poly.getMaxZ() + 1, poly);
                }
                for (int z = poly.getMinZ(); z <= poly.getMaxZ(); z++) {
                    insertSpatialHash(spatialHash, poly.getMinX() - 1, z, poly);
                    insertSpatialHash(spatialHash, poly.getMaxX() + 1, z, poly);
                }
            }

            // Check only pairs that share a spatial hash bucket
            Set<Long> checkedPairs = new HashSet<>();
            for (List<NavPoly> bucket : spatialHash.values()) {
                for (int i = 0; i < bucket.size(); i++) {
                    for (int j = i + 1; j < bucket.size(); j++) {
                        NavPoly a = bucket.get(i);
                        NavPoly b = bucket.get(j);
                        // Deduplicate pair checks using Cantor pairing
                        long pairKey = a.getId() < b.getId()
                                ? ((long) a.getId() << 32) | b.getId()
                                : ((long) b.getId() << 32) | a.getId();
                        if (checkedPairs.add(pairKey)) {
                            tryCreateEdge(a, b, false);
                        }
                    }
                }
            }
        }
    }

    private static void insertSpatialHash(Map<Long, List<NavPoly>> hash, int x, int z, NavPoly poly) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        hash.computeIfAbsent(key, k -> new ArrayList<>(2)).add(poly);
    }

    /**
     * Try to create an edge between two polygons if they share an adjacent boundary.
     */
    private void tryCreateEdge(NavPoly a, NavPoly b, boolean yTransition) {
        // Check if A's right side touches B's left side (A.maxX + 1 == B.minX)
        if (a.getMaxX() + 1 == b.getMinX()) {
            int overlapMinZ = Math.max(a.getMinZ(), b.getMinZ());
            int overlapMaxZ = Math.min(a.getMaxZ(), b.getMaxZ());
            if (overlapMinZ <= overlapMaxZ) {
                double edgeX = b.getMinX(); // world X at the boundary
                double yA = a.getY();
                double yB = b.getY();
                Vec3d left = new Vec3d(edgeX, yTransition ? yB : yA, overlapMinZ);
                Vec3d right = new Vec3d(edgeX, yTransition ? yB : yA, overlapMaxZ + 1);
                NavEdge edge = new NavEdge(a, b, left, right, yTransition);
                a.addEdge(edge);
                b.addEdge(edge);
                return;
            }
        }

        // Check B's right side touches A's left side
        if (b.getMaxX() + 1 == a.getMinX()) {
            int overlapMinZ = Math.max(a.getMinZ(), b.getMinZ());
            int overlapMaxZ = Math.min(a.getMaxZ(), b.getMaxZ());
            if (overlapMinZ <= overlapMaxZ) {
                double edgeX = a.getMinX();
                double yA = a.getY();
                double yB = b.getY();
                Vec3d left = new Vec3d(edgeX, yTransition ? yB : yA, overlapMinZ);
                Vec3d right = new Vec3d(edgeX, yTransition ? yB : yA, overlapMaxZ + 1);
                NavEdge edge = new NavEdge(a, b, left, right, yTransition);
                a.addEdge(edge);
                b.addEdge(edge);
                return;
            }
        }

        // Check A's bottom side touches B's top side (A.maxZ + 1 == B.minZ)
        if (a.getMaxZ() + 1 == b.getMinZ()) {
            int overlapMinX = Math.max(a.getMinX(), b.getMinX());
            int overlapMaxX = Math.min(a.getMaxX(), b.getMaxX());
            if (overlapMinX <= overlapMaxX) {
                double edgeZ = b.getMinZ();
                double yA = a.getY();
                double yB = b.getY();
                Vec3d left = new Vec3d(overlapMinX, yTransition ? yB : yA, edgeZ);
                Vec3d right = new Vec3d(overlapMaxX + 1, yTransition ? yB : yA, edgeZ);
                NavEdge edge = new NavEdge(a, b, left, right, yTransition);
                a.addEdge(edge);
                b.addEdge(edge);
                return;
            }
        }

        // Check B's bottom side touches A's top side
        if (b.getMaxZ() + 1 == a.getMinZ()) {
            int overlapMinX = Math.max(a.getMinX(), b.getMinX());
            int overlapMaxX = Math.min(a.getMaxX(), b.getMaxX());
            if (overlapMinX <= overlapMaxX) {
                double edgeZ = a.getMinZ();
                double yA = a.getY();
                double yB = b.getY();
                Vec3d left = new Vec3d(overlapMinX, yTransition ? yB : yA, edgeZ);
                Vec3d right = new Vec3d(overlapMaxX + 1, yTransition ? yB : yA, edgeZ);
                NavEdge edge = new NavEdge(a, b, left, right, yTransition);
                a.addEdge(edge);
                b.addEdge(edge);
            }
        }
    }

    /**
     * Find adjacencies between polygons at different Y layers (step up/down).
     * Handles fractional Y differences (e.g., full block → slab = 0.5 block step).
     */
    private void findYTransitionAdjacencies(List<NavPoly> polys, ClientWorld world) {
        // Compare all pairs of polys at different Y levels.
        // Group by quantized Y to avoid redundant same-layer checks.
        Map<Long, List<NavPoly>> byQY = new HashMap<>();
        for (NavPoly poly : polys) {
            long qy = Math.round(poly.getY() * 16.0);
            byQY.computeIfAbsent(qy, k -> new ArrayList<>()).add(poly);
        }

        List<Long> yLevels = new ArrayList<>(byQY.keySet());
        Collections.sort(yLevels);

        for (int i = 0; i < yLevels.size(); i++) {
            long qyLow = yLevels.get(i);
            double yLow = qyLow / 16.0;

            for (int j = i + 1; j < yLevels.size(); j++) {
                long qyHigh = yLevels.get(j);
                double yHigh = qyHigh / 16.0;
                double heightDiff = yHigh - yLow;

                // Step-up: height difference must be within jump height
                if (heightDiff > 0 && heightDiff <= maxJumpHeight) {
                    for (NavPoly lowPoly : byQY.get(qyLow)) {
                        for (NavPoly highPoly : byQY.get(qyHigh)) {
                            tryCreateYTransitionEdge(lowPoly, highPoly, heightDiff, world);
                        }
                    }
                }

                // Step-down: height difference must be within fall height
                if (heightDiff > 0 && heightDiff <= maxFallHeight) {
                    for (NavPoly highPoly : byQY.get(qyHigh)) {
                        for (NavPoly lowPoly : byQY.get(qyLow)) {
                            tryCreateFallEdge(highPoly, lowPoly, heightDiff, world);
                        }
                    }
                }

                // Don't check pairs that are too far apart vertically
                if (heightDiff > maxFallHeight) break;
            }
        }
    }

    /**
     * Try to create a step-up edge between a low poly and high poly.
     * Supports fractional heights (slabs/stairs) and multi-block jumps (God Potion).
     * Requires that the polys are horizontally adjacent and clearance exists.
     */
    private void tryCreateYTransitionEdge(NavPoly lowPoly, NavPoly highPoly, double heightDiff, ClientWorld world) {
        // Slab/stair steps (≤0.5625 blocks) are within auto-step height — mark as
        // non-Y-transition so A* treats them identically to flat traversal. This
        // dramatically reduces branching on diagonal slab/stair chains where each
        // tiny polygon would otherwise generate a penalized Y-transition edge.
        boolean isYTransition = heightDiff > 0.5625;

        // Check all 4 directions for adjacency
        if (lowPoly.getMaxX() + 1 == highPoly.getMinX()) {
            int overlapMinZ = Math.max(lowPoly.getMinZ(), highPoly.getMinZ());
            int overlapMaxZ = Math.min(lowPoly.getMaxZ(), highPoly.getMaxZ());
            if (overlapMinZ <= overlapMaxZ && hasJumpClearance(lowPoly, highPoly, world)) {
                createYEdge(lowPoly, highPoly, lowPoly.getMaxX() + 1, overlapMinZ, overlapMaxZ, isYTransition);
            }
        }
        if (highPoly.getMaxX() + 1 == lowPoly.getMinX()) {
            int overlapMinZ = Math.max(lowPoly.getMinZ(), highPoly.getMinZ());
            int overlapMaxZ = Math.min(lowPoly.getMaxZ(), highPoly.getMaxZ());
            if (overlapMinZ <= overlapMaxZ && hasJumpClearance(lowPoly, highPoly, world)) {
                createYEdge(lowPoly, highPoly, lowPoly.getMinX(), overlapMinZ, overlapMaxZ, isYTransition);
            }
        }
        if (lowPoly.getMaxZ() + 1 == highPoly.getMinZ()) {
            int overlapMinX = Math.max(lowPoly.getMinX(), highPoly.getMinX());
            int overlapMaxX = Math.min(lowPoly.getMaxX(), highPoly.getMaxX());
            if (overlapMinX <= overlapMaxX && hasJumpClearance(lowPoly, highPoly, world)) {
                createYEdgeZ(lowPoly, highPoly, lowPoly.getMaxZ() + 1, overlapMinX, overlapMaxX, isYTransition);
            }
        }
        if (highPoly.getMaxZ() + 1 == lowPoly.getMinZ()) {
            int overlapMinX = Math.max(lowPoly.getMinX(), highPoly.getMinX());
            int overlapMaxX = Math.min(lowPoly.getMaxX(), highPoly.getMaxX());
            if (overlapMinX <= overlapMaxX && hasJumpClearance(lowPoly, highPoly, world)) {
                createYEdgeZ(lowPoly, highPoly, lowPoly.getMinZ(), overlapMinX, overlapMaxX, isYTransition);
            }
        }
    }

    /**
     * Check if there's clearance for a step/jump between two polys.
     * Uses the actual fractional Y values of both polys.
     */
    private boolean hasJumpClearance(NavPoly lowPoly, NavPoly highPoly, ClientWorld world) {
        double lowY = lowPoly.getY();
        double highY = highPoly.getY();

        // Pick a representative XZ point near the boundary
        int checkX = (Math.max(lowPoly.getMinX(), highPoly.getMinX()) +
                       Math.min(lowPoly.getMaxX(), highPoly.getMaxX())) / 2;
        int checkZ = (Math.max(lowPoly.getMinZ(), highPoly.getMinZ()) +
                       Math.min(lowPoly.getMaxZ(), highPoly.getMaxZ())) / 2;

        // Check clearance at every block Y level from low feet to high feet + 2 (head)
        int startY = (int) Math.floor(lowY);
        int endY = (int) Math.floor(highY) + 2;

        for (int y = startY; y <= endY; y++) {
            BlockPos checkPos = new BlockPos(checkX, y, checkZ);
            // Don't fail on blocks that the player stands ON (ground blocks)
            // The ground block at lowPoly or highPoly is expected to have collision
            if (y == (int) Math.floor(lowY) - 1 || y == (int) Math.floor(highY) - 1) continue;

            BlockState state = world.getBlockState(checkPos);
            VoxelShape shape = state.getCollisionShape(world, checkPos);
            if (!shape.isEmpty()) {
                // Check if this collision actually intersects the player's travel path
                double blockBottom = y + shape.getMin(Direction.Axis.Y);
                double blockTop = y + shape.getMax(Direction.Axis.Y);

                // Player body sweeps from lowY to highY + 1.8
                // If the collision is within this range and not the ground blocks, it blocks
                if (blockTop > lowY + 0.01 && blockBottom < highY + 1.8) {
                    // Exception: this might be the ground block of one of the polys
                    boolean isLowGround = Math.abs(blockTop - lowY) < 0.01;
                    boolean isHighGround = Math.abs(blockTop - highY) < 0.01;
                    if (!isLowGround && !isHighGround) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void createYEdge(NavPoly a, NavPoly b, int edgeX, int overlapMinZ, int overlapMaxZ, boolean isYTransition) {
        double y = b.getY(); // destination Y
        Vec3d left = new Vec3d(edgeX, y, overlapMinZ);
        Vec3d right = new Vec3d(edgeX, y, overlapMaxZ + 1);
        NavEdge edge = new NavEdge(a, b, left, right, isYTransition);
        a.addEdge(edge);
        b.addEdge(edge);
    }

    private void createYEdgeZ(NavPoly a, NavPoly b, int edgeZ, int overlapMinX, int overlapMaxX, boolean isYTransition) {
        double y = b.getY();
        Vec3d left = new Vec3d(overlapMinX, y, edgeZ);
        Vec3d right = new Vec3d(overlapMaxX + 1, y, edgeZ);
        NavEdge edge = new NavEdge(a, b, left, right, isYTransition);
        a.addEdge(edge);
        b.addEdge(edge);
    }

    /**
     * Try to create a fall edge between a high poly and a low poly.
     * Handles fractional height differences.
     */
    private void tryCreateFallEdge(NavPoly highPoly, NavPoly lowPoly, double drop, ClientWorld world) {
        // Small drops (≤0.5625, slab height) are within auto-step — treat as flat traversal
        boolean isYTransition = drop > 0.5625;

        // Check X-axis adjacency
        if (highPoly.getMaxX() + 1 == lowPoly.getMinX() || lowPoly.getMaxX() + 1 == highPoly.getMinX()) {
            int overlapMinZ = Math.max(highPoly.getMinZ(), lowPoly.getMinZ());
            int overlapMaxZ = Math.min(highPoly.getMaxZ(), lowPoly.getMaxZ());
            if (overlapMinZ <= overlapMaxZ && hasClearFallPath(highPoly, lowPoly, world)) {
                int edgeX = highPoly.getMaxX() + 1 == lowPoly.getMinX()
                        ? lowPoly.getMinX() : highPoly.getMinX();
                double y = lowPoly.getY();
                Vec3d left = new Vec3d(edgeX, y, overlapMinZ);
                Vec3d right = new Vec3d(edgeX, y, overlapMaxZ + 1);
                NavEdge edge = new NavEdge(highPoly, lowPoly, left, right, isYTransition);
                highPoly.addEdge(edge);
                lowPoly.addEdge(edge);
            }
        }

        // Check Z-axis adjacency
        if (highPoly.getMaxZ() + 1 == lowPoly.getMinZ() || lowPoly.getMaxZ() + 1 == highPoly.getMinZ()) {
            int overlapMinX = Math.max(highPoly.getMinX(), lowPoly.getMinX());
            int overlapMaxX = Math.min(highPoly.getMaxX(), lowPoly.getMaxX());
            if (overlapMinX <= overlapMaxX && hasClearFallPath(highPoly, lowPoly, world)) {
                int edgeZ = highPoly.getMaxZ() + 1 == lowPoly.getMinZ()
                        ? lowPoly.getMinZ() : highPoly.getMinZ();
                double y = lowPoly.getY();
                Vec3d left = new Vec3d(overlapMinX, y, edgeZ);
                Vec3d right = new Vec3d(overlapMaxX + 1, y, edgeZ);
                NavEdge edge = new NavEdge(highPoly, lowPoly, left, right, isYTransition);
                highPoly.addEdge(edge);
                lowPoly.addEdge(edge);
            }
        }
    }

    private boolean hasClearFallPath(NavPoly highPoly, NavPoly lowPoly, ClientWorld world) {
        int midX = (highPoly.getMinX() + highPoly.getMaxX()) / 2;
        int midZ = (highPoly.getMinZ() + highPoly.getMaxZ()) / 2;
        double highY = highPoly.getY();
        double lowY = lowPoly.getY();

        // Check the fall path is clear (player body must fit through)
        int startBlockY = (int) Math.floor(lowY);
        int endBlockY = (int) Math.floor(highY) + 1;

        for (int y = startBlockY; y <= endBlockY; y++) {
            BlockPos pos = new BlockPos(midX, y, midZ);
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (!shape.isEmpty()) {
                double blockTop = y + shape.getMax(Direction.Axis.Y);
                double blockBottom = y + shape.getMin(Direction.Axis.Y);
                // Collision within the fall corridor (between lowY and highY+1.8) blocks the path
                // Exception: the ground blocks themselves
                boolean isHighGround = Math.abs(blockTop - highY) < 0.01;
                boolean isLowGround = Math.abs(blockTop - lowY) < 0.01;
                if (!isHighGround && !isLowGround && blockTop > lowY + 0.01 && blockBottom < highY + 1.8) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get the actual walking surface Y for a given feet position.
     * Checks the block below feetPos for its collision shape top.
     * Returns NaN if the position is not walkable.
     * <p>
     * This is the public API for finding fractional surface heights (slabs, stairs).
     */
    public static double getSurfaceY(BlockPos feetPos, ClientWorld world) {
        BlockPos groundPos = feetPos.down();
        if (!world.isChunkLoaded(groundPos.getX() >> 4, groundPos.getZ() >> 4)) return Double.NaN;

        BlockState groundState = world.getBlockState(groundPos);
        VoxelShape groundShape = groundState.getCollisionShape(world, groundPos);
        if (groundShape.isEmpty()) return Double.NaN;
        if (isNonSolidObstacle(groundState) || isHazardous(groundState)) return Double.NaN;

        double shapeTop = groundShape.getMax(Direction.Axis.Y);
        double surfaceY = groundPos.getY() + shapeTop;

        // Check clearance for player body
        int feetBlockY = (int) Math.floor(surfaceY);
        int headTopBlockY = (int) Math.floor(surfaceY + 1.8);

        for (int checkY = feetBlockY; checkY <= headTopBlockY; checkY++) {
            BlockPos checkPos = new BlockPos(feetPos.getX(), checkY, feetPos.getZ());
            if (checkY == groundPos.getY()) continue;

            BlockState checkState = world.getBlockState(checkPos);
            VoxelShape checkShape = checkState.getCollisionShape(world, checkPos);
            if (!checkShape.isEmpty()) {
                double blockBottom = checkPos.getY() + checkShape.getMin(Direction.Axis.Y);
                double blockTop = checkPos.getY() + checkShape.getMax(Direction.Axis.Y);

                if (blockTop > surfaceY + 0.1 && blockBottom < surfaceY + 1.8) {
                    return Double.NaN;
                }
            }
            if (isNonSolidObstacle(checkState) || isHazardous(checkState)) return Double.NaN;
        }

        return surfaceY;
    }

    // ──────────────────────────────────────────────────────────────
    //  Wall clearance computation (BFS distance transform)
    // ──────────────────────────────────────────────────────────────

    /**
     * For each polygon, compute the minimum distance from any of its cells
     * to a non-walkable cell in the same Y layer. Polygons in the center
     * of wide corridors get high clearance; those against walls get low.
     * <p>
     * Uses a BFS distance transform per Y layer, capped at 4 blocks
     * (beyond that, clearance doesn't affect A* cost).
     */
    private void computeWallClearance(List<NavPoly> allPolys,
                                       Map<Long, List<WalkableSurface>> surfacesByY,
                                       int sizeX, int sizeZ, int scanMinX, int scanMinZ) {
        int CAP = 4;

        // Build walkable grids and BFS distance grids per Y layer
        Map<Long, int[][]> distanceGrids = new HashMap<>();

        for (Map.Entry<Long, List<WalkableSurface>> entry : surfacesByY.entrySet()) {
            boolean[][] grid = new boolean[sizeX][sizeZ];
            for (WalkableSurface s : entry.getValue()) {
                grid[s.lx][s.lz] = true;
            }

            int[][] dist = new int[sizeX][sizeZ];
            Queue<int[]> queue = new ArrayDeque<>();

            // Seed: all non-walkable cells and scan-area edges get distance 0
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    if (!grid[x][z]) {
                        dist[x][z] = 0;
                        queue.add(new int[]{x, z});
                    } else {
                        dist[x][z] = CAP;
                        // Edges of scan area are implicit walls
                        if (x == 0 || x == sizeX - 1 || z == 0 || z == sizeZ - 1) {
                            dist[x][z] = 0;
                            queue.add(new int[]{x, z});
                        }
                    }
                }
            }

            // BFS flood fill from walls inward
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] cell = queue.poll();
                int cd = dist[cell[0]][cell[1]];
                if (cd >= CAP) continue;
                for (int[] d : dirs) {
                    int nx = cell[0] + d[0], nz = cell[1] + d[1];
                    if (nx < 0 || nx >= sizeX || nz < 0 || nz >= sizeZ) continue;
                    if (dist[nx][nz] > cd + 1) {
                        dist[nx][nz] = cd + 1;
                        queue.add(new int[]{nx, nz});
                    }
                }
            }

            distanceGrids.put(entry.getKey(), dist);
        }

        // Assign clearance to each polygon: minimum distance of any cell in the poly
        for (NavPoly poly : allPolys) {
            long qy = Math.round(poly.getY() * 16.0);
            int[][] dist = distanceGrids.get(qy);
            if (dist == null) continue;

            int minClearance = CAP;
            for (int x = poly.getMinX(); x <= poly.getMaxX(); x++) {
                for (int z = poly.getMinZ(); z <= poly.getMaxZ(); z++) {
                    int lx = x - scanMinX;
                    int lz = z - scanMinZ;
                    if (lx >= 0 && lx < sizeX && lz >= 0 && lz < sizeZ) {
                        minClearance = Math.min(minClearance, dist[lx][lz]);
                    }
                }
            }
            poly.setClearance(minClearance);
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Walkability checks (simple and working)
    // ──────────────────────────────────────────────────────────────

    /**
     * Check if a position is walkable by the player.
     * Ground must be solid, feet and head level must be clear.
     */
    public static boolean isWalkable(BlockPos feetPos, ClientWorld world) {
        BlockPos below = feetPos.down();
        BlockPos head = feetPos.up();

        if (!world.isChunkLoaded(feetPos.getX() >> 4, feetPos.getZ() >> 4)) return false;

        BlockState belowState = world.getBlockState(below);
        BlockState feetState = world.getBlockState(feetPos);
        BlockState headState = world.getBlockState(head);

        // Ground must have a collision shape the player can stand on.
        // isSolidBlock() only returns true for full cubes, missing slabs, stairs,
        // fences, walls, trapdoors, etc. Instead check if the block has any
        // collision shape at all — if it does, the player can stand on it.
        if (belowState.getCollisionShape(world, below).isEmpty()) return false;
        if (isNonSolidObstacle(belowState)) return false;

        // Feet and head must have no collision (player body fits through).
        // Use collision shape check instead of isSolidBlock so that slabs/stairs
        // at feet or head level are correctly detected as blocking.
        if (!feetState.getCollisionShape(world, feetPos).isEmpty()) return false;
        if (!headState.getCollisionShape(world, head).isEmpty()) return false;

        // Feet and head must not be non-solid obstacles (leaves block movement)
        if (isNonSolidObstacle(feetState)) return false;
        if (isNonSolidObstacle(headState)) return false;

        // Avoid hazardous blocks
        if (isHazardous(feetState) || isHazardous(headState)) return false;
        if (isHazardous(belowState)) return false;

        return true;
    }

    /**
     * Check if a position is passable (player can move through it).
     */
    public static boolean isPassable(BlockPos pos, ClientWorld world) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        BlockState state = world.getBlockState(pos);

        // Blocks with collision shapes are not passable (includes slabs, stairs, etc.)
        if (!state.getCollisionShape(world, pos).isEmpty()) return false;

        // Non-solid obstacles (leaves) are not passable
        if (isNonSolidObstacle(state)) return false;

        // Hazardous blocks are not passable
        if (isHazardous(state)) return false;

        return true;
    }

    /**
     * Check if a block state is hazardous (water, lava, fire, etc.)
     * and should be avoided by the pathfinder.
     */
    public static boolean isHazardous(BlockState state) {
        // Lava
        if (state.isOf(Blocks.LAVA)) return true;
        // Fire / soul fire
        if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) return true;
        // Cactus (damages on contact)
        if (state.isOf(Blocks.CACTUS)) return true;
        // Sweet berry bush (damages and slows)
        if (state.isOf(Blocks.SWEET_BERRY_BUSH)) return true;
        // Magma block (damages when standing)
        if (state.isOf(Blocks.MAGMA_BLOCK)) return true;
        // Cobweb (gets player stuck)
        if (state.isOf(Blocks.COBWEB)) return true;
        // Powder snow (player sinks in)
        if (state.isOf(Blocks.POWDER_SNOW)) return true;
        return false;
    }

    /**
     * Check if a block is non-solid but still blocks pathfinding.
     * These are blocks that have no collision but can't be walked through
     * or stood on (like leaves, scaffolding without sneaking, etc.)
     */
    public static boolean isNonSolidObstacle(BlockState state) {
        // Leaves - player can't walk through them properly
        if (state.isIn(BlockTags.LEAVES)) return true;
        // Scaffolding - player falls through without sneaking
        if (state.isOf(Blocks.SCAFFOLDING)) return true;
        return false;
    }
}
