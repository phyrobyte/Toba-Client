package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete navigation mesh containing all walkable polygons and their adjacency graph.
 */
public class NavMesh {
    private final List<NavPoly> polygons;
    private final Map<Integer, NavPoly> polyById;

    public NavMesh(List<NavPoly> polygons) {
        this.polygons = new ArrayList<>(polygons);
        this.polyById = new HashMap<>();
        for (NavPoly poly : polygons) {
            polyById.put(poly.getId(), poly);
        }
    }

    /**
     * Find the polygon that contains a given world position.
     * Checks XZ containment and Y proximity (within 3 blocks).
     * The tolerance is generous to handle slabs/stairs where the ground-finder
     * may return a surface Y slightly different from the polygon's stored Y.
     */
    public NavPoly getPolyAt(Vec3d pos) {
        NavPoly best = null;
        double bestYDist = Double.MAX_VALUE;

        for (NavPoly poly : polygons) {
            if (poly.containsXZ(pos.x, pos.z)) {
                double yDist = Math.abs(pos.y - poly.getY());
                if (yDist < 3.0 && yDist < bestYDist) {
                    best = poly;
                    bestYDist = yDist;
                }
            }
        }
        return best;
    }

    /**
     * Find the polygon that contains a given block position.
     */
    public NavPoly getPolyAt(BlockPos pos) {
        return getPolyAt(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    /**
     * Find the nearest polygon to a given world position when exact containment fails.
     * Uses XZ distance to polygon center, limited by maxDistance and Y tolerance of 5 blocks.
     * This is the fallback when getPolyAt returns null — e.g., the player is standing
     * on a block edge between polygons or the ground Y doesn't exactly match.
     */
    public NavPoly getNearestPoly(Vec3d pos, double maxDistance) {
        NavPoly best = null;
        double bestDist = Double.MAX_VALUE;

        for (NavPoly poly : polygons) {
            double yDist = Math.abs(pos.y - poly.getY());
            if (yDist > 5.0) continue;

            Vec3d center = poly.getCenter();
            double dxSq = (pos.x - center.x) * (pos.x - center.x);
            double dzSq = (pos.z - center.z) * (pos.z - center.z);
            double xzDist = Math.sqrt(dxSq + dzSq);

            // Combine XZ distance with a Y penalty to prefer same-level polygons
            double totalDist = xzDist + yDist * 2.0;

            if (totalDist < bestDist && totalDist < maxDistance) {
                best = poly;
                bestDist = totalDist;
            }
        }
        return best;
    }

    /**
     * Get all polygons in this mesh.
     */
    public List<NavPoly> getAllPolygons() {
        return polygons;
    }

    /**
     * Get a polygon by its ID.
     */
    public NavPoly getPolyById(int id) {
        return polyById.get(id);
    }

    /**
     * Clear all blacklists on all polygons.
     */
    public void clearBlacklists() {
        for (NavPoly poly : polygons) {
            poly.resetBlacklist();
        }
    }

    /**
     * Get the number of polygons in this mesh.
     */
    public int getPolyCount() {
        return polygons.size();
    }

    /**
     * Get the count of currently blacklisted polygons.
     */
    public int getBlacklistedCount(long currentTick) {
        int count = 0;
        for (NavPoly poly : polygons) {
            if (poly.isBlacklisted(currentTick)) count++;
        }
        return count;
    }

    @Override
    public String toString() {
        return "NavMesh{polygons=" + polygons.size() + "}";
    }
}
