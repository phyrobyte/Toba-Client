package dev.toba.client.api.failsafes;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.screens.gui.failsafe.FailsafeOverlay;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Handles all player-alerting actions when a failsafe triggers.
 * <p>
 * The warning lasts for {@link #COUNTDOWN_SECONDS} seconds or until the player
 * presses ENTER, whichever comes first. All alerts (TTS, notifications, overlay)
 * stop when the warning ends.
 */
public class FailsafeWarning {

    private boolean active = false;
    private boolean playerResponded = false;
    private String failsafeName = "";
    private long triggerTimeMs = 0;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final String TAKEOVER_KEY = "B";

    // Debounce: ignore ENTER for the first N ms after trigger to avoid
    // the key press from /testfailsafe command immediately dismissing.
    private static final long KEY_DEBOUNCE_MS = 500;

    // Notification control
    private long lastNotificationMs = 0;
    private static final long NOTIFICATION_INTERVAL_MS = 1500;

    // Alert sound process (killed on dismiss)
    private Process alertSoundProcess = null;

    // ImGui overlay
    private final FailsafeOverlay overlay = new FailsafeOverlay();
    private boolean overlayRegistered = false;

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns true if the player actively pressed the takeover key.
     * Returns false if the countdown expired without response.
     */
    public boolean didPlayerRespond() {
        return playerResponded;
    }

    public void trigger(String failsafeName) {
        if (active) return;
        this.active = true;
        this.playerResponded = false;
        this.failsafeName = failsafeName;
        this.triggerTimeMs = System.currentTimeMillis();
        this.lastNotificationMs = 0;

        // Show ImGui overlay
        overlay.show(failsafeName, COUNTDOWN_SECONDS);
        if (!overlayRegistered) {
            ImGuiImpl.registerHudOverlay(overlay);
            overlayRegistered = true;
        }

        // Immediate actions
        focusWindow();
        playAlertSound();
        speakTTS("macro check. take over. macro check. take over.");
        sendToastNotification("FAILSAFE: " + failsafeName, "Macro check detected! Press " + TAKEOVER_KEY + " to take over!");
    }

    public void dismiss() {
        if (!active) return;
        active = false;
        overlay.hide();
        if (overlayRegistered) {
            ImGuiImpl.unregisterHudOverlay(overlay);
            overlayRegistered = false;
        }
        // Kill the looping alert sound immediately
        if (alertSoundProcess != null && alertSoundProcess.isAlive()) {
            alertSoundProcess.destroyForcibly();
            alertSoundProcess = null;
        }
    }

    public boolean isActive() {
        return active;
    }

    public String getFailsafeName() {
        return failsafeName;
    }

    public int getRemainingSeconds() {
        long elapsed = System.currentTimeMillis() - triggerTimeMs;
        int remaining = COUNTDOWN_SECONDS - (int) (elapsed / 1000);
        return Math.max(0, remaining);
    }

    public boolean isCountdownExpired() {
        return (System.currentTimeMillis() - triggerTimeMs) >= COUNTDOWN_SECONDS * 1000L;
    }

    // ──────────────────────────────────────────────────────────────
    //  Tick
    // ──────────────────────────────────────────────────────────────

    public void tick() {
        if (!active) return;

        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        long elapsed = now - triggerTimeMs;

        // Auto-dismiss after countdown expires (player did NOT respond)
        if (elapsed >= COUNTDOWN_SECONDS * 1000L) {
            playerResponded = false;
            dismiss();
            return;
        }

        // Check if player pressed B (with debounce to avoid chat submit carrying over)
        if (elapsed > KEY_DEBOUNCE_MS && client.getWindow() != null) {
            long windowHandle = client.getWindow().getHandle();
            if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_B) == GLFW.GLFW_PRESS) {
                playerResponded = true;
                dismiss();
                return;
            }
        }

        // Spam toast notifications
        if (now - lastNotificationMs >= NOTIFICATION_INTERVAL_MS) {
            lastNotificationMs = now;
            sendToastNotification("FAILSAFE: " + failsafeName, "Macro check detected! Press " + TAKEOVER_KEY + " to take over!");
        }

        // Keep flashing the taskbar
        if (client.getWindow() != null) {
            GLFW.glfwRequestWindowAttention(client.getWindow().getHandle());
        }
    }

    // ──────────────────────────────────────────────────────────────
    //  Window Focus
    // ──────────────────────────────────────────────────────────────

    private void focusWindow() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;
        long handle = client.getWindow().getHandle();
        GLFW.glfwFocusWindow(handle);
        GLFW.glfwRequestWindowAttention(handle);
    }

    // ──────────────────────────────────────────────────────────────
    //  Alert Sound — Windows exclamation only
    // ──────────────────────────────────────────────────────────────

    private void playAlertSound() {
        runAsync(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    // Loop the exclamation sound every 500ms for the entire countdown.
                    // The process is killed immediately when dismiss() is called.
                    alertSoundProcess = new ProcessBuilder("powershell", "-Command",
                            "for ($i = 0; $i -lt " + (COUNTDOWN_SECONDS * 2) + "; $i++) { " +
                            "[System.Media.SystemSounds]::Exclamation.Play(); " +
                            "Start-Sleep -Milliseconds 500 }"
                    ).redirectErrorStream(true).start();
                }
            } catch (Exception ignored) {}
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Windows Toast Notifications
    // ──────────────────────────────────────────────────────────────

    private void sendToastNotification(String title, String message) {
        runAsync(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                if (os.contains("win")) {
                    String ps = "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null; " +
                            "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom, ContentType = WindowsRuntime] | Out-Null; " +
                            "$template = '<toast><visual><binding template=\"ToastGeneric\">" +
                            "<text>" + escapeXml(title) + "</text>" +
                            "<text>" + escapeXml(message) + "</text>" +
                            "</binding></visual><audio src=\"ms-winsoundevent:Notification.Looping.Alarm\" loop=\"false\"/></toast>'; " +
                            "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument; " +
                            "$xml.LoadXml($template); " +
                            "$toast = [Windows.UI.Notifications.ToastNotification]::new($xml); " +
                            "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('Toba').Show($toast)";
                    new ProcessBuilder("powershell", "-Command", ps)
                            .redirectErrorStream(true).start();
                }
            } catch (Exception ignored) {}
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  TTS — fire once on trigger, runs on background thread
    // ──────────────────────────────────────────────────────────────

    private void speakTTS(String text) {
        // Sanitize text to prevent command injection — strip everything except
        // alphanumerics, spaces, and basic punctuation.
        String safe = text.replaceAll("[^a-zA-Z0-9 .,!?'-]", "");
        runAsync(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("powershell", "-Command",
                            "Add-Type -AssemblyName System.Speech; " +
                            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; " +
                            "$s.Rate = 3; " +
                            "$s.Speak('" + safe.replace("'", "''") + "')");
                } else if (os.contains("mac")) {
                    // ProcessBuilder passes args directly (no shell expansion)
                    pb = new ProcessBuilder("say", "-r", "250", safe);
                } else {
                    pb = new ProcessBuilder("espeak", "-s", "200", safe);
                }
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (Exception ignored) {}
        });
    }

    // ──────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void runAsync(Runnable task) {
        Thread t = new Thread(task, "Toba-Failsafe-Alert");
        t.setDaemon(true);
        t.start();
    }

    public void cleanup() {
        dismiss();
    }
}
