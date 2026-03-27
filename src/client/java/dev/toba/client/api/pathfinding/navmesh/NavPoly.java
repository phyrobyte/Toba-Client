package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * A single convex polygon (axis-aligned rectangle) in the navigation mesh.
 * Represents a walkable area at a specific Y level.
 */
public class NavPoly {
    private static volatile int nextId = 0;

    private final int id;
    private final int minX, minZ, maxX, maxZ;
    private final double y; // walkable Y level (feet position) — fractional for slabs/stairs
    private final List<NavEdge> edges = new ArrayList<>();

    private double traversalCost = 1.0;
    private double clearance = 4.0; // distance to nearest non-walkable boundary (capped at 4)
    private boolean blacklisted = false;
    private long blacklistExpiry = 0;

    public NavPoly(int minX, int minZ, int maxX, int maxZ, double y) {
        this.id = nextId++;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.y = y;
    }

    /**
     * Reset the global ID counter (call before generating a new mesh).
     */
    public static void resetIdCounter() {
        nextId = 0;
    }

    /**
     * Check if a world XZ coordinate falls within this polygon.
     */
    public boolean containsXZ(double x, double z) {
        return x >= minX && x <= maxX + 1 && z >= minZ && z <= maxZ + 1;
    }

    /**
     * Check if a block position (integer) falls within this polygon.
     */
    public boolean containsBlock(int bx, int bz) {
        return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }

    /**
     * Get the world-space center of this polygon (at feet Y level).
     */
    public Vec3d getCenter() {
        return new Vec3d(
                (minX + maxX + 1) / 2.0,
                y,
                (minZ + maxZ + 1) / 2.0
        );
    }

    /**
     * Get the area in blocks (number of block cells).
     */
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public void addEdge(NavEdge edge) {
        edges.add(edge);
    }

    /**
     * Check if this polygon is currently blacklisted.
     */
    public boolean isBlacklisted(long currentTick) {
        if (!blacklisted) return false;
        if (currentTick >= blacklistExpiry) {
            blacklisted = false;
            return false;
        }
        return true;
    }

    /**
     * Blacklist this polygon for a duration.
     */
    public void blacklist(long durationTicks, long currentTick) {
        this.blacklisted = true;
        this.blacklistExpiry = currentTick + durationTicks;
    }

    public void resetBlacklist() {
        this.blacklisted = false;
        this.blacklistExpiry = 0;
    }

    /**
     * Check if this polygon overlaps with a circle (for blacklist area matching).
     */
    public boolean overlapsCircle(double cx, double cz, double radius) {
        // Find closest point in the rectangle to the circle center
        double closestX = Math.max(minX, Math.min(cx, maxX + 1));
        double closestZ = Math.max(minZ, Math.min(cz, maxZ + 1));
        double dx = cx - closestX;
        double dz = cz - closestZ;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    // Getters
    public int getId() { return id; }
    public int getMinX() { return minX; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxZ() { return maxZ; }
    public double getY() { return y; }
    public int getBlockY() { return (int) Math.floor(y); }
    public List<NavEdge> getEdges() { return edges; }
    public double getTraversalCost() { return traversalCost; }
    public void setTraversalCost(double cost) { this.traversalCost = cost; }
    public double getClearance() { return clearance; }
    public void setClearance(double clearance) { this.clearance = clearance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id == ((NavPoly) o).id;
    }

    @Override
    public int hashCode() { return id; }

    @Override
    public String toString() {
        return "NavPoly{id=" + id + ", [" + minX + "," + minZ + "]->[" + maxX + "," + maxZ + "], y=" + String.format("%.1f", y) + "}";
    }
}
