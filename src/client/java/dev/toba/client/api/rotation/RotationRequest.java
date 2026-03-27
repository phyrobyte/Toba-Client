package dev.toba.client.api.rotation;

import net.minecraft.util.math.Vec3d;

/**
 * Data object representing a rotation request from a module.
 */
public class RotationRequest {
    private final String moduleId;
    private float targetYaw;
    private float targetPitch;
    private final RotationManager.Style style;
    private final RotationManager.Priority priority;
    private final long tickCreated;

    // Optional: the actual world position being tracked (for continuous tracking)
    private Vec3d targetPosition;

    public RotationRequest(String moduleId, float targetYaw, float targetPitch,
                           RotationManager.Style style, RotationManager.Priority priority, long tickCreated) {
        this.moduleId = moduleId;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.style = style;
        this.priority = priority;
        this.tickCreated = tickCreated;
    }

    public String getModuleId() { return moduleId; }
    public float getTargetYaw() { return targetYaw; }
    public float getTargetPitch() { return targetPitch; }
    public RotationManager.Style getStyle() { return style; }
    public RotationManager.Priority getPriority() { return priority; }
    public long getTickCreated() { return tickCreated; }
    public Vec3d getTargetPosition() { return targetPosition; }

    public void setTargetYaw(float yaw) { this.targetYaw = yaw; }
    public void setTargetPitch(float pitch) { this.targetPitch = pitch; }
    public void setTargetPosition(Vec3d pos) { this.targetPosition = pos; }

    /**
     * Update the target angles (used when a module updates its aim each tick).
     */
    public void update(float yaw, float pitch) {
        this.targetYaw = yaw;
        this.targetPitch = pitch;
    }
}
