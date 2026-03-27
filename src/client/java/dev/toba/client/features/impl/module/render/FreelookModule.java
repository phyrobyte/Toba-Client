package dev.toba.client.features.impl.module.render;

import dev.toba.client.features.settings.Module;
import net.minecraft.client.MinecraftClient;

/**
 * Freelook module: toggle to rotate the camera independently of the player's facing direction.
 * Scroll wheel adjusts camera distance (third-person zoom).
 */
public class FreelookModule extends Module {

    private final Setting<Integer> distance;

    // Camera state
    private boolean active = false;
    private float cameraYaw;
    private float cameraPitch;
    private float cameraDistance = 4.0f;

    private static final float MIN_DISTANCE = 1.0f;
    private static final float MAX_DISTANCE = 20.0f;
    private static final float SCROLL_STEP = 0.5f;

    public FreelookModule() {
        super("Freelook", "Rotate camera independently of player facing", Category.RENDER);
        setKeyBind(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);

        distance = addSetting(new Setting<>("Default Distance", 4, Setting.SettingType.INTEGER));
        distance.range(1, 20);
    }

    @Override
    protected void onEnable() {
        cameraDistance = distance.getValue();
        activate();
    }

    @Override
    protected void onDisable() {
        active = false;
    }

    /**
     * Called from tick — just deactivate if player leaves world or opens a screen.
     */
    public void onTick() {
        if (!isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) {
            return;
        }
    }

    private void activate() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        active = true;
        cameraYaw = client.player.getYaw();
        cameraPitch = client.player.getPitch();
    }

    /**
     * Called from mouse mixin to intercept mouse movement while freelook is active.
     * Uses the same sensitivity formula as vanilla updateMouse.
     * Returns true if the movement was consumed (camera rotated instead of player).
     */
    public boolean onMouseMove(double deltaX, double deltaY) {
        if (!isEnabled() || !active) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;

        // Vanilla sensitivity formula: (sens * 0.6 + 0.2)^3 * 8.0 * 0.15
        double sensitivity = client.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;

        cameraYaw += (float) (deltaX * factor);
        cameraPitch += (float) (deltaY * factor);
        cameraPitch = Math.clamp(cameraPitch, -90.0f, 90.0f);

        return true;
    }

    /**
     * Called from mouse mixin to intercept scroll while freelook is active.
     * Returns true if scroll was consumed.
     */
    public boolean onScroll(double amount) {
        if (!isEnabled() || !active) return false;

        cameraDistance -= (float) amount * SCROLL_STEP;
        cameraDistance = Math.clamp(cameraDistance, MIN_DISTANCE, MAX_DISTANCE);
        return true;
    }

    public boolean isActive() {
        return isEnabled() && active;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }

    public float getCameraDistance() {
        return cameraDistance;
    }
}
