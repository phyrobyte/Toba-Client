package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.util.math.Vec3d;

/**
 * A shared edge (portal) between two adjacent navigation polygons.
 * The left/right vertices define the segment through which the player
 * can transition from one polygon to another.
 */
public class NavEdge {
    private final NavPoly polyA;
    private final NavPoly polyB;
    private final Vec3d leftVertex;
    private final Vec3d rightVertex;

    // If this edge connects polygons at different Y levels
    private final boolean yTransition;

    public NavEdge(NavPoly polyA, NavPoly polyB, Vec3d leftVertex, Vec3d rightVertex, boolean yTransition) {
        this.polyA = polyA;
        this.polyB = polyB;
        this.leftVertex = leftVertex;
        this.rightVertex = rightVertex;
        this.yTransition = yTransition;
    }

    /**
     * Get the polygon on the other side of this edge.
     */
    public NavPoly getOther(NavPoly from) {
        return from.equals(polyA) ? polyB : polyA;
    }

    /**
     * Get the midpoint of this portal edge.
     */
    public Vec3d getMidpoint() {
        return new Vec3d(
                (leftVertex.x + rightVertex.x) / 2.0,
                (leftVertex.y + rightVertex.y) / 2.0,
                (leftVertex.z + rightVertex.z) / 2.0
        );
    }

    /**
     * Get the width of this portal.
     */
    public double getWidth() {
        return leftVertex.distanceTo(rightVertex);
    }

    public NavPoly getPolyA() { return polyA; }
    public NavPoly getPolyB() { return polyB; }
    public Vec3d getLeftVertex() { return leftVertex; }
    public Vec3d getRightVertex() { return rightVertex; }
    public boolean isYTransition() { return yTransition; }

    @Override
    public String toString() {
        return "NavEdge{" + polyA.getId() + " <-> " + polyB.getId() +
                ", L=" + leftVertex + ", R=" + rightVertex +
                (yTransition ? " [Y-transition]" : "") + "}";
    }
}
