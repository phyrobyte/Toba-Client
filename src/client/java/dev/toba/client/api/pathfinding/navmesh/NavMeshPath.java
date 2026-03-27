package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.List;

/**
 * Result of a NavMesh pathfinding query. Contains the smooth waypoints
 * from the funnel algorithm and the polygon corridor for debug rendering.
 */
public class NavMeshPath {
    private final List<Vec3d> waypoints;
    private final List<NavPoly> corridor;
    private final boolean found;

    public NavMeshPath(List<Vec3d> waypoints, List<NavPoly> corridor, boolean found) {
        this.waypoints = waypoints != null ? waypoints : Collections.emptyList();
        this.corridor = corridor != null ? corridor : Collections.emptyList();
        this.found = found;
    }

    /**
     * Create a failed (no path found) result.
     */
    public static NavMeshPath empty() {
        return new NavMeshPath(Collections.emptyList(), Collections.emptyList(), false);
    }

    /**
     * Get the smooth waypoints (Vec3d positions the player should walk through).
     * These are the output of the funnel algorithm with diagonal shortcuts.
     */
    public List<Vec3d> getWaypoints() { return waypoints; }

    /**
     * Get the polygon corridor (ordered list of polygons traversed).
     * Useful for debug rendering.
     */
    public List<NavPoly> getCorridor() { return corridor; }

    /**
     * Whether a valid path was found.
     */
    public boolean isFound() { return found; }

    /**
     * Total path length (sum of segment distances).
     */
    public double getLength() {
        double total = 0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            total += waypoints.get(i).distanceTo(waypoints.get(i + 1));
        }
        return total;
    }

    /**
     * Number of waypoints.
     */
    public int getWaypointCount() { return waypoints.size(); }

    @Override
    public String toString() {
        return "NavMeshPath{found=" + found + ", waypoints=" + waypoints.size() + ", corridor=" + corridor.size() + "}";
    }
}
