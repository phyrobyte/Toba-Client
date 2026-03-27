package dev.toba.client.api.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized rotation manager with smooth mouse movement.
 *
 * Simple and clean: just smooth interpolation with acceleration/deceleration.
 *
 * Usage:
 *   RotationManager.getInstance().request("mymodule", targetPos, Style.SMOOTH, Priority.NORMAL);
 *   RotationManager.getInstance().release("mymodule");
 */
public class RotationManager {
    private static RotationManager instance;

    /**
     * Priority levels for rotation requests. Higher value = more important.
     */
    public enum Priority {
        LOW(0),
        NORMAL(10),
        HIGH(20),
        CRITICAL(30);

        private final int value;
        Priority(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    /**
     * Rotation interpolation styles.
     */
    public enum Style {
        /** Smooth movement with acceleration/deceleration. */
        SMOOTH,
        /** Faster smooth movement for combat. */
        FAST,
        /** Set rotation immediately with no interpolation. */
        INSTANT
    }

    // All active rotation requests, keyed by module ID
    private final Map<String, RotationRequest> activeRequests = new HashMap<>();

    // The currently winning request (resolved each tick)
    private RotationRequest currentRequest = null;

    // Tick counter for request creation timestamps
    private long tickCounter = 0;

    // Current rotation velocity (for smooth acceleration/deceleration)
    private float yawVelocity = 1f;
    private float pitchVelocity = 1f;

    // Rotation averaging — keeps a circular buffer of recent target angles and
    // averages them to produce smoother, more human-like rotation. This eliminates
    // micro-jitter from rapidly changing targets and creates the "lazy eye" effect
    // that real players exhibit (slight delay in reacting to target changes).
    private static final int ROTATION_AVG_BUFFER_SIZE = 5;
    private final float[] yawBuffer = new float[ROTATION_AVG_BUFFER_SIZE];
    private final float[] pitchBuffer = new float[ROTATION_AVG_BUFFER_SIZE];
    private int bufferIndex = 0;
    private int bufferCount = 0; // how many entries have been written (up to BUFFER_SIZE)

    // Smoothed vertical pitch offset (for natural transitions)
    private float smoothedVerticalPitchOffset = 0f;
    private int landingHoldTicks = 0;

    // Flag to disable vertical pitch compensation (e.g., during God Potion high jumps)
    private boolean disableVerticalPitchCompensation = false;


    // ──────────────────────────────────────────────────────────────
    //  Tunable values - adjust these to change rotation behavior
    // ──────────────────────────────────────────────────────────────

    // SMOOTH style settings
    public static float SMOOTH_ACCELERATION = 0.18f;   // How fast to accelerate (higher = more responsive turns)
    public static float SMOOTH_FRICTION = 0.50f;       // Friction/damping (higher = faster settling, less sluggish)
    public static float SMOOTH_MAX_SPEED = 14f;        // Maximum rotation speed in degrees/frame

    // FAST style settings
    public static float FAST_ACCELERATION = 0.22f;
    public static float FAST_FRICTION = 0.58f;
    public static float FAST_MAX_SPEED = 18f;

    // Distance scaling - rotation is faster for larger distances
    public static float DISTANCE_SCALE_MIN = 0.7f;     // Minimum speed multiplier (for small rotations)
    public static float DISTANCE_SCALE_MAX = 2.0f;     // Maximum speed multiplier (for large rotations)
    public static float DISTANCE_SCALE_THRESHOLD = 45f; // Degrees at which max scaling is reached

    // Vertical motion pitch compensation - look up/down based on vertical movement
    public static float VERTICAL_PITCH_SCALE = 1f;      // Max degrees of pitch adjustment for vertical motion (subtle)
    public static float VERTICAL_VELOCITY_THRESHOLD = 3f; // Velocity at which max pitch adjustment is applied
    public static float VERTICAL_PITCH_SMOOTHING = 0.1f;    // How fast pitch offset changes (lower = smoother)
    public static int LANDING_HOLD_TICKS = 5;              // Ticks to hold pitch offset after landing (~0.5 sec)
    public static float VERTICAL_PITCH_MAX_DOWN = 4f;       // Max degrees to look DOWN when falling (positive offset)
    public static float VERTICAL_PITCH_MAX_UP = 1.5f;         // Max degrees to look UP when rising (negative offset)

    /**
     * Enable or disable vertical pitch compensation.
     * Disable this during high-jump scenarios (like God Potion) to prevent erratic looking.
     */
    public void setVerticalPitchCompensation(boolean enabled) {
        this.disableVerticalPitchCompensation = !enabled;
        if (!enabled) {
            // Reset the offset when disabling
            smoothedVerticalPitchOffset = 0f;
            landingHoldTicks = 0;
        }
    }

    public static RotationManager getInstance() {
        if (instance == null) instance = new RotationManager();
        return instance;
    }

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Request rotation toward a world position.
     */
    public void request(String moduleId, Vec3d target, Style style, Priority priority) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        Vec3d eyePos = player.getEyePos();
        float[] angles = calcAngles(eyePos, target);

        RotationRequest req = activeRequests.get(moduleId);
        if (req != null && req.getStyle() == style && req.getPriority() == priority) {
            req.update(angles[0], angles[1]);
            req.setTargetPosition(target);
        } else {
            req = new RotationRequest(moduleId, angles[0], angles[1], style, priority, tickCounter);
            req.setTargetPosition(target);
            activeRequests.put(moduleId, req);
        }
    }

    /**
     * Request rotation with explicit yaw and pitch angles.
     */
    public void request(String moduleId, float yaw, float pitch, Style style, Priority priority) {
        RotationRequest req = activeRequests.get(moduleId);
        if (req != null && req.getStyle() == style && req.getPriority() == priority) {
            req.update(yaw, pitch);
            req.setTargetPosition(null);
        } else {
            req = new RotationRequest(moduleId, yaw, pitch, style, priority, tickCounter);
            activeRequests.put(moduleId, req);
        }
    }

    /**
     * Release a module's rotation request.
     */
    public void release(String moduleId) {
        activeRequests.remove(moduleId);
        if (currentRequest != null && currentRequest.getModuleId().equals(moduleId)) {
            currentRequest = null;
            // Reset averaging buffer so stale data doesn't bleed into the next request
            bufferCount = 0;
            bufferIndex = 0;
        }
    }

    /**
     * Returns true if any rotation request is active.
     */
    public boolean isActive() {
        return currentRequest != null;
    }

    /**
     * Returns the module ID of the currently active rotation request, or null.
     */
    public String getActiveModuleId() {
        return currentRequest != null ? currentRequest.getModuleId() : null;
    }

    // ──────────────────────────────────────────────────────────────
    //  Tick / Render hooks
    // ──────────────────────────────────────────────────────────────

    /**
     * Called each game tick.
     */
    public void onTick() {
        tickCounter++;
        resolveActiveRequest();
        applyRotation();
    }

    /**
     * Called each render frame for smooth visual interpolation.
     */
    public void onRender() {
        applyRotation();
    }

    // ──────────────────────────────────────────────────────────────
    //  Internal
    // ──────────────────────────────────────────────────────────────

    private void resolveActiveRequest() {
        if (activeRequests.isEmpty()) {
            currentRequest = null;
            return;
        }

        RotationRequest best = null;
        for (RotationRequest req : activeRequests.values()) {
            if (best == null) {
                best = req;
            } else {
                int cmp = Integer.compare(req.getPriority().getValue(), best.getPriority().getValue());
                if (cmp > 0) {
                    best = req;
                } else if (cmp == 0 && req.getTickCreated() > best.getTickCreated()) {
                    best = req;
                }
            }
        }
        currentRequest = best;
    }

    private void applyRotation() {
        if (currentRequest == null) {
            // Decelerate to stop when no request
            yawVelocity *= 0.8f;
            pitchVelocity *= 0.8f;
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Get player velocity for vertical motion detection
        Vec3d velocity = player.getVelocity();

        // Detect if player is airborne (for normal mode pitch freezing)
        boolean isAirborne = !player.isOnGround() || Math.abs(velocity.y) > 0.08;

        // God Potion mode: completely freeze pitch while airborne
        // This prevents ALL pitch changes during high jumps
        boolean godPotionAirborne = disableVerticalPitchCompensation && isAirborne;

        // Recalculate target if tracking a position (only when grounded or in normal mode)
        if (currentRequest.getTargetPosition() != null && !isAirborne) {
            Vec3d eyePos = player.getEyePos();
            float[] angles = calcAngles(eyePos, currentRequest.getTargetPosition());
            currentRequest.update(angles[0], angles[1]);
        }

        float rawTargetYaw = currentRequest.getTargetYaw();
        float rawTargetPitch = currentRequest.getTargetPitch();

        // ── Rotation Averaging ──
        // Push the raw target into a circular buffer and average recent entries.
        // This smooths out jitter from rapidly changing waypoints and creates a
        // natural "lazy eye" delay that makes rotations look human.
        // For yaw averaging, we must handle angle wrapping: convert all entries
        // relative to the newest entry to avoid averaging across the -180/180 boundary.
        yawBuffer[bufferIndex] = rawTargetYaw;
        pitchBuffer[bufferIndex] = rawTargetPitch;
        bufferIndex = (bufferIndex + 1) % ROTATION_AVG_BUFFER_SIZE;
        if (bufferCount < ROTATION_AVG_BUFFER_SIZE) bufferCount++;

        float targetYaw;
        float targetPitch;
        if (currentRequest.getStyle() == Style.INSTANT || bufferCount <= 1) {
            // No averaging for INSTANT style or when buffer is too small
            targetYaw = rawTargetYaw;
            targetPitch = rawTargetPitch;
        } else {
            // Weighted average: recent entries get more weight than older ones.
            // Weights: newest=1.0, oldest=0.3, linearly interpolated.
            float yawSum = 0f;
            float pitchSum = 0f;
            float weightSum = 0f;
            for (int i = 0; i < bufferCount; i++) {
                // Walk backwards from newest to oldest
                int idx = (bufferIndex - 1 - i + ROTATION_AVG_BUFFER_SIZE) % ROTATION_AVG_BUFFER_SIZE;
                float age = (float) i / (ROTATION_AVG_BUFFER_SIZE - 1); // 0=newest, 1=oldest
                float weight = 1.0f - 0.7f * age; // 1.0 -> 0.3

                // Wrap yaw relative to the newest entry to handle -180/180 boundary
                float yawRelative = MathHelper.wrapDegrees(yawBuffer[idx] - rawTargetYaw);
                yawSum += yawRelative * weight;
                pitchSum += pitchBuffer[idx] * weight;
                weightSum += weight;
            }
            targetYaw = rawTargetYaw + yawSum / weightSum;
            targetPitch = pitchSum / weightSum;
        }

        // Calculate pitch compensation for vertical movement (smoothed for natural feel)
        // Skip this entirely if disabled (e.g., during God Potion high jumps)
        if (!disableVerticalPitchCompensation) {
            float targetVerticalPitchOffset = 0f;
            if (currentRequest.getTargetPosition() != null && Math.abs(velocity.y) > 0.08) {
                Vec3d eyePos = player.getEyePos();
                Vec3d targetPos = currentRequest.getTargetPosition();

                // Calculate horizontal distance to target
                double dx = targetPos.x - eyePos.x;
                double dz = targetPos.z - eyePos.z;
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);

                // Scale pitch adjustment: smaller for close targets, larger for distant ones
                // For 1 block away: small adjustment; for 8+ blocks: full adjustment
                float distanceFactor = MathHelper.clamp((float) horizontalDist / 8f, 0.3f, 1f);

                // Scale based on velocity magnitude (capped at threshold)
                float velocityFactor = MathHelper.clamp(
                    (float) Math.abs(velocity.y) / VERTICAL_VELOCITY_THRESHOLD,
                    0f, 1f
                );

                // Calculate pitch offset: negative velocity (falling) = positive pitch (look down)
                // Positive velocity (rising) = negative pitch (look up)
                targetVerticalPitchOffset = (float) -velocity.y * VERTICAL_PITCH_SCALE * distanceFactor * velocityFactor;

                // Clamp to max limits so player doesn't look straight down/up unnaturally
                if (targetVerticalPitchOffset > 0) {
                    // Looking down (falling)
                    targetVerticalPitchOffset = Math.min(targetVerticalPitchOffset, VERTICAL_PITCH_MAX_DOWN);
                } else {
                    // Looking up (rising)
                    targetVerticalPitchOffset = Math.max(targetVerticalPitchOffset, -VERTICAL_PITCH_MAX_UP);
                }

                // Reset hold timer while in motion
                landingHoldTicks = LANDING_HOLD_TICKS;
            }

            // Handle landing hold - keep the pitch offset for a bit after landing
            if (player.isOnGround() && Math.abs(velocity.y) < 0.08) {
                if (landingHoldTicks > 0) {
                    // Hold current offset, don't change target yet
                    landingHoldTicks--;
                } else {
                    // Hold expired, start returning to neutral
                    targetVerticalPitchOffset = 0f;
                }
            }

            // Smoothly interpolate toward target offset (prevents jerky movements)
            smoothedVerticalPitchOffset += (targetVerticalPitchOffset - smoothedVerticalPitchOffset) * VERTICAL_PITCH_SMOOTHING;
        } else {
            // God Potion mode: force everything to zero
            smoothedVerticalPitchOffset = 0f;
            landingHoldTicks = 0;
        }

        if (currentRequest.getStyle() == Style.INSTANT) {
            player.setYaw(targetYaw);
            // Only set pitch if not airborne in God Potion mode
            if (!godPotionAirborne) {
                player.setPitch(targetPitch);
            }
            yawVelocity = 0f;
            pitchVelocity = 0f;
            return;
        }

        // Calculate difference to target
        float yawDiff = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        // Get style-specific settings
        boolean fast = currentRequest.getStyle() == Style.FAST;
        float acceleration = fast ? FAST_ACCELERATION : SMOOTH_ACCELERATION;
        float friction = fast ? FAST_FRICTION : SMOOTH_FRICTION;
        float maxSpeed = fast ? FAST_MAX_SPEED : SMOOTH_MAX_SPEED;

        // Scale speed independently for yaw and pitch based on their own distances
        // This prevents large yaw rotations from causing erratic pitch movement
        float yawScale = MathHelper.clamp(
            DISTANCE_SCALE_MIN + (Math.abs(yawDiff) / DISTANCE_SCALE_THRESHOLD) * (DISTANCE_SCALE_MAX - DISTANCE_SCALE_MIN),
            DISTANCE_SCALE_MIN,
            DISTANCE_SCALE_MAX
        );
        float pitchScale = MathHelper.clamp(
            DISTANCE_SCALE_MIN + (Math.abs(pitchDiff) / DISTANCE_SCALE_THRESHOLD) * (DISTANCE_SCALE_MAX - DISTANCE_SCALE_MIN),
            DISTANCE_SCALE_MIN,
            DISTANCE_SCALE_MAX
        );

        // Accelerate toward target (each axis scaled independently)
        yawVelocity += yawDiff * acceleration * yawScale;

        // Pitch handling depends on mode
        if (godPotionAirborne) {
            // God Potion airborne: completely zero out pitch velocity
            pitchVelocity = 0f;
        } else if (!isAirborne) {
            // Grounded: normal pitch acceleration
            pitchVelocity += pitchDiff * acceleration * pitchScale;
        } else {
            // Normal mode airborne: dampen pitch physics to prevent drag
            pitchVelocity *= 0.1f;
        }

        // Apply friction (creates deceleration as we approach target)
        yawVelocity *= friction;
        if (!godPotionAirborne) {
            pitchVelocity *= friction;
        }

        // Clamp max speed (each axis scaled independently)
        float yawMaxSpeed = maxSpeed * yawScale;
        float pitchMaxSpeed = maxSpeed * pitchScale;
        yawVelocity = MathHelper.clamp(yawVelocity, -yawMaxSpeed, yawMaxSpeed);
        if (!godPotionAirborne) {
            pitchVelocity = MathHelper.clamp(pitchVelocity, -pitchMaxSpeed, pitchMaxSpeed);
        }

        // Apply velocity
        player.setYaw(player.getYaw() + yawVelocity);

        // Only apply pitch changes if NOT in God Potion airborne state
        if (!godPotionAirborne) {
            player.setPitch(MathHelper.clamp(player.getPitch() + pitchVelocity + smoothedVerticalPitchOffset, -90f, 90f));
        }
        // When godPotionAirborne is true, pitch stays exactly where it was - no changes at all

        // Snap to target if very close (and slow enough to avoid jitter)
        // Only snap pitch when not in God Potion airborne mode
        if (Math.abs(yawDiff) < 0.5f && Math.abs(yawVelocity) < 1f) {
            player.setYaw(targetYaw);
            yawVelocity = 0f;
        }
        if (!godPotionAirborne && Math.abs(pitchDiff) < 0.5f && Math.abs(pitchVelocity) < 1f) {
            player.setPitch(targetPitch);
            pitchVelocity = 0f;
        }
    }

    /**
     * Calculate yaw and pitch from a source position to a target position.
     */
    public static float[] calcAngles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Yaw: angle in the horizontal plane
        // In Minecraft, yaw 0 = +Z (south), yaw 90 = -X (west), yaw -90/270 = +X (east), yaw 180 = -Z (north)
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        // Pitch: angle from horizontal
        // In Minecraft, negative pitch = looking up, positive pitch = looking down
        // Use atan2 with proper sign: positive dy (target above) should give negative pitch (look up)
        float pitch;
        if (horizontalDist < 0.001) {
            // Target is directly above or below - look straight up or down
            pitch = dy > 0 ? -90f : 90f;
        } else {
            pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));
        }

        // Clamp pitch to valid Minecraft range
        pitch = MathHelper.clamp(pitch, -90f, 90f);

        return new float[]{ yaw, pitch };
    }
}
