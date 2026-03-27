package dev.toba.client.api.failsafes;

import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central manager for all failsafe checks.
 * <p>
 * Failsafes are modular — each one is a {@link Failsafe} implementation that
 * checks a specific condition (cursor teleport, hotbar swap, farming interruption, etc.).
 * The manager ticks all registered failsafes while any script module is active,
 * and delegates alerting to {@link FailsafeWarning}.
 * <p>
 * Usage:
 * <pre>
 *   // Register a failsafe (typically during mod init)
 *   FailsafeManager.getInstance().register(new CursorTeleportFailsafe());
 *
 *   // The manager auto-ticks from TobaClient — no manual calls needed.
 * </pre>
 */
public class FailsafeManager {

    private static FailsafeManager instance;

    public static FailsafeManager getInstance() {
        if (instance == null) instance = new FailsafeManager();
        return instance;
    }

    // ──────────────────────────────────────────────────────────────
    //  Failsafe Interface
    // ──────────────────────────────────────────────────────────────

    /**
     * A single failsafe check. Implement this to detect a specific macro-check event.
     */
    public interface Failsafe {
        /** Short human-readable name, e.g. "Cursor Teleport" */
        String getName();

        /**
         * Called each tick while scripts are running.
         * Return {@code true} if this failsafe has been triggered.
         */
        boolean check();

        /**
         * Reset internal state. Called when the warning is dismissed or scripts stop.
         */
        void reset();

        /**
         * Whether this failsafe is currently enabled.
         * Failsafes can be toggled individually.
         */
        default boolean isEnabled() { return true; }
    }

    // ──────────────────────────────────────────────────────────────
    //  State
    // ──────────────────────────────────────────────────────────────

    private final List<Failsafe> failsafes = new CopyOnWriteArrayList<>();
    private final FailsafeWarning warning = new FailsafeWarning();
    private boolean enabled = true;

    // Cooldown to prevent rapid re-triggers after dismissal
    private long lastDismissMs = 0;
    private static final long RETRIGGER_COOLDOWN_MS = 5000;

    private FailsafeManager() {}

    // ──────────────────────────────────────────────────────────────
    //  Registration
    // ──────────────────────────────────────────────────────────────

    /**
     * Register a failsafe check. Can be called at any time.
     */
    public void register(Failsafe failsafe) {
        failsafes.add(failsafe);
    }

    /**
     * Unregister a failsafe by instance.
     */
    public void unregister(Failsafe failsafe) {
        failsafes.remove(failsafe);
    }

    /**
     * Get all registered failsafes (read-only view for UI/debug).
     */
    public List<Failsafe> getFailsafes() {
        return List.copyOf(failsafes);
    }

    // ──────────────────────────────────────────────────────────────
    //  Enable/Disable
    // ──────────────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            warning.dismiss();
            resetAll();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ──────────────────────────────────────────────────────────────
    //  Tick — called every client tick from TobaClient
    // ──────────────────────────────────────────────────────────────

    public void tick() {
        if (!enabled) return;

        // If warning is currently showing, tick it and check for dismissal
        if (warning.isActive()) {
            warning.tick();

            // If the warning was dismissed (player pressed ENTER)
            if (!warning.isActive()) {
                onWarningDismissed();
            }
            return; // Don't check for new triggers while warning is active
        }

        // Only check failsafes when a script module is actively running
        if (!isAnyScriptRunning()) {
            return;
        }

        // Cooldown after dismissal to prevent rapid re-trigger
        if (System.currentTimeMillis() - lastDismissMs < RETRIGGER_COOLDOWN_MS) {
            return;
        }

        // Check each failsafe
        for (Failsafe failsafe : failsafes) {
            if (!failsafe.isEnabled()) continue;

            try {
                if (failsafe.check()) {
                    triggerWarning(failsafe);
                    break; // Only one failsafe triggers at a time
                }
            } catch (Exception e) {
                System.err.println("[Toba Failsafe] Error checking " + failsafe.getName() + ": " + e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Manual Trigger (for testing)
    // ──────────────────────────────────────────────────────────────

    /**
     * Manually trigger a failsafe warning with a custom name.
     * Useful for testing the alert system.
     */
    public void triggerManual(String name) {
        warning.trigger(name);
        TobaChat.sendColored("FAILSAFE: " + name + " triggered!", Formatting.RED);
    }

    // ──────────────────────────────────────────────────────────────
    //  Internal
    // ──────────────────────────────────────────────────────────────

    private void triggerWarning(Failsafe failsafe) {
        warning.trigger(failsafe.getName());
        TobaChat.sendColored("FAILSAFE: " + failsafe.getName() + " triggered!", Formatting.RED);

        // Disable all running script modules immediately
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.isScript() && module.isEnabled()) {
                module.toggle();
            }
        }
    }

    private void onWarningDismissed() {
        lastDismissMs = System.currentTimeMillis();
        resetAll();

        if (warning.didPlayerRespond()) {
            TobaChat.sendColored("React normally, you are in a macro check right now. DO NOT START MACROING AGAIN", Formatting.YELLOW);
        } else {
            TobaChat.sendColored("Failsafe unanswered. You will most likely get banned soon.", Formatting.RED);
        }
    }

    private void resetAll() {
        for (Failsafe failsafe : failsafes) {
            try {
                failsafe.reset();
            } catch (Exception e) {
                System.err.println("[Toba Failsafe] Error resetting " + failsafe.getName() + ": " + e.getMessage());
            }
        }
    }

    private boolean isAnyScriptRunning() {
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.isScript() && module.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the warning instance for direct access if needed.
     */
    public FailsafeWarning getWarning() {
        return warning;
    }
}
