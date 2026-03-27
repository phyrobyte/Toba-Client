package dev.toba.client.api.pathfinding;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import dev.toba.client.api.pathfinding.navmesh.NavMeshGenerator;
import dev.toba.client.api.pathfinding.navmesh.NavMeshPath;
import dev.toba.client.api.pathfinding.navmesh.NavPoly;

import java.util.*;

/**
 * 3D voxel-grid A* pathfinder for flying (creative/elytra/spectator).
 * Unlike the NavMesh pathfinder which only handles ground-based movement,
 * this searches a full 3D grid of passable blocks.
 * <p>
 * Produces a smoothed path by greedily skipping waypoints where 3D line-of-sight
 * exists, resulting in diagonal flight paths through open air.
 */
public class FlyPathfinder {

    private static final int MAX_ITERATIONS = 200000;
    private static final double DIAGONAL_COST = 1.414;
    private static final double VERTICAL_COST = 1.0;

    // 26-connected neighbors (all directions including diagonals)
    private static final int[][] NEIGHBORS_26 = {
            // Cardinal XZ
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
            // Cardinal Y
            {0, 1, 0}, {0, -1, 0},
            // XZ diagonals
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            // XY diagonals
            {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0},
            // ZY diagonals
            {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1},
            // 3D diagonals
            {1, 1, 1}, {1, 1, -1}, {1, -1, 1}, {1, -1, -1},
            {-1, 1, 1}, {-1, 1, -1}, {-1, -1, 1}, {-1, -1, -1}
    };

    /**
     * Find a 3D flight path from start to goal through open air.
     * Returns a NavMeshPath with smoothed waypoints (no corridor info since it's grid-based).
     */
    public NavMeshPath findPath(Vec3d start, Vec3d goal, ClientWorld world, int maxRange) {
        BlockPos startBlock = BlockPos.ofFloored(start.x, start.y, start.z);
        BlockPos goalBlock = BlockPos.ofFloored(goal.x, goal.y, goal.z);

        // Ensure start and goal are passable
        if (!isPassable3D(startBlock, world) || !isPassable3D(goalBlock, world)) {
            return NavMeshPath.empty();
        }

        // A* on 3D voxel grid
        PriorityQueue<FlyNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<Long, FlyNode> allNodes = new HashMap<>();
        Set<Long> closedSet = new HashSet<>();

        long startKey = posKey(startBlock);
        long goalKey = posKey(goalBlock);

        FlyNode startNode = new FlyNode(startBlock, null, 0, heuristic(startBlock, goalBlock));
        openSet.add(startNode);
        allNodes.put(startKey, startNode);

        int iterations = 0;

        while (!openSet.isEmpty() && iterations++ < MAX_ITERATIONS) {
            FlyNode current = openSet.poll();
            long currentKey = posKey(current.pos);

            if (closedSet.contains(currentKey)) continue;
            closedSet.add(currentKey);

            // Reached goal
            if (currentKey == goalKey) {
                List<Vec3d> rawPath = reconstructPath(current);
                List<Vec3d> smoothed = smoothPath(rawPath, world);
                return new NavMeshPath(smoothed, Collections.emptyList(), true);
            }

            // Range check
            if (current.pos.getManhattanDistance(startBlock) > maxRange) continue;

            // Explore 26-connected neighbors
            for (int[] dir : NEIGHBORS_26) {
                BlockPos neighborPos = current.pos.add(dir[0], dir[1], dir[2]);
                long neighborKey = posKey(neighborPos);

                if (closedSet.contains(neighborKey)) continue;
                if (!isPassable3D(neighborPos, world)) continue;

                // Calculate movement cost
                int axes = (dir[0] != 0 ? 1 : 0) + (dir[1] != 0 ? 1 : 0) + (dir[2] != 0 ? 1 : 0);
                double moveCost;
                if (axes == 1) moveCost = 1.0;
                else if (axes == 2) moveCost = DIAGONAL_COST;
                else moveCost = 1.732; // sqrt(3)

                double tentativeG = current.gCost + moveCost;

                FlyNode neighborNode = allNodes.get(neighborKey);
                if (neighborNode == null) {
                    double h = heuristic(neighborPos, goalBlock);
                    neighborNode = new FlyNode(neighborPos, current, tentativeG, tentativeG + h);
                    allNodes.put(neighborKey, neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeG < neighborNode.gCost) {
                    neighborNode.parent = current;
                    neighborNode.gCost = tentativeG;
                    neighborNode.fCost = tentativeG + heuristic(neighborPos, goalBlock);
                    openSet.add(neighborNode);
                }
            }
        }

        return NavMeshPath.empty(); // No path found
    }

    /**
     * Check if a block is passable for flying (both feet and head level).
     */
    private boolean isPassable3D(BlockPos pos, ClientWorld world) {
        return NavMeshGenerator.isPassable(pos, world)
                && NavMeshGenerator.isPassable(pos.up(), world);
    }

    /**
     * Octile distance heuristic for 3D — admissible for 26-connected grid.
     */
    private double heuristic(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());

        // Sort so d1 >= d2 >= d3
        int d1 = Math.max(dx, Math.max(dy, dz));
        int d3 = Math.min(dx, Math.min(dy, dz));
        int d2 = dx + dy + dz - d1 - d3;

        // Cost: d3 * (sqrt3-sqrt2) + (d2-d3) * (sqrt2-1) + d1 * 1
        return d3 * 0.318 + (d2 - d3) * 0.414 + d1;
    }

    /**
     * Reconstruct the raw block-by-block path from goal to start.
     */
    private List<Vec3d> reconstructPath(FlyNode goalNode) {
        List<Vec3d> path = new ArrayList<>();
        FlyNode current = goalNode;
        while (current != null) {
            path.add(new Vec3d(current.pos.getX() + 0.5, current.pos.getY(), current.pos.getZ() + 0.5));
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Smooth the path by greedily skipping waypoints where 3D line-of-sight exists.
     */
    private List<Vec3d> smoothPath(List<Vec3d> rawPath, ClientWorld world) {
        if (rawPath.size() <= 2) return rawPath;

        List<Vec3d> smoothed = new ArrayList<>();
        smoothed.add(rawPath.get(0));

        int current = 0;
        while (current < rawPath.size() - 1) {
            int farthest = current + 1;
            for (int ahead = rawPath.size() - 1; ahead > current + 1; ahead--) {
                if (hasLineOfSight3D(rawPath.get(current), rawPath.get(ahead), world)) {
                    farthest = ahead;
                    break;
                }
            }
            smoothed.add(rawPath.get(farthest));
            current = farthest;
        }

        return smoothed;
    }

    /**
     * 3D line-of-sight check — ray-march through air checking both feet and head level.
     */
    private boolean hasLineOfSight3D(Vec3d from, Vec3d to, ClientWorld world) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist < 0.5) return true;

        double step = 0.4;
        int steps = (int) Math.ceil(dist / step);

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = from.x + dx * t;
            double y = from.y + dy * t;
            double z = from.z + dz * t;

            BlockPos feetPos = BlockPos.ofFloored(x, y, z);
            BlockPos headPos = feetPos.up();

            if (!NavMeshGenerator.isPassable(feetPos, world) ||
                !NavMeshGenerator.isPassable(headPos, world)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Pack a BlockPos into a long for fast HashMap keys.
     */
    private static long posKey(BlockPos pos) {
        return ((long) pos.getX() & 0x3FFFFFFL) << 38
                | ((long) pos.getY() & 0xFFFL) << 26
                | ((long) pos.getZ() & 0x3FFFFFFL);
    }

    /**
     * Node in the 3D A* search.
     */
    private static class FlyNode {
        final BlockPos pos;
        FlyNode parent;
        double gCost;
        double fCost;

        FlyNode(BlockPos pos, FlyNode parent, double gCost, double fCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }
}
