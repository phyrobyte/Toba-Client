package dev.toba.client.api.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.toba.client.ProjectInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Scanner;

/**
 * Automatic update system for Toba Client.
 * <p>
 * Checks GitHub Releases for new versions, downloads the new JAR to the
 * mods folder, disables the old one, and prompts the user to restart.
 */
public class AutoUpdater {

    private static final String GITHUB_API_URL =
            "https://api.github.com/repos/Toba-Client/Toba/releases/latest";
    private static final String ALLOWED_DOWNLOAD_HOST = "github.com";
    private static final String ALLOWED_CDN_HOST = "objects.githubusercontent.com";

    /** Ensures the update check only runs once per game session. */
    private static volatile boolean checkedThisSession = false;

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Called from {@code onInitializeClient()} to delete leftover files from
     * a previous update (e.g. {@code .jar.disabled}, {@code .delete_me},
     * interrupted {@code .jar.tmp} downloads).
     */
    public static void cleanupOldJars() {
        try {
            Path modsDir = getModsDir();
            if (modsDir == null || !Files.isDirectory(modsDir)) return;

            // .jar.disabled — old JARs renamed by disableCurrentJar()
            deleteByGlob(modsDir, "Toba*.jar.disabled");

            // .delete_me markers left when renaming failed and shutdown hook
            // could not delete either
            try (DirectoryStream<Path> markers =
                         Files.newDirectoryStream(modsDir, "Toba*.delete_me")) {
                for (Path marker : markers) {
                    String jarName = marker.getFileName().toString()
                            .replace(".delete_me", "");
                    Path jarFile = modsDir.resolve(jarName);
                    safeDelete(jarFile);
                    safeDelete(marker);
                }
            }

            // .jar.tmp — interrupted downloads
            deleteByGlob(modsDir, "Toba*.jar.tmp");

        } catch (Exception e) {
            System.err.println("[Toba] Update cleanup error: " + e.getMessage());
        }
    }

    /**
     * Checks for an update on a background daemon thread.
     * Safe to call multiple times — only the first invocation per session
     * does anything.
     */
    public static void checkAsync() {
        if (checkedThisSession) return;
        checkedThisSession = true;

        Thread t = new Thread(AutoUpdater::doCheck, "Toba-AutoUpdater");
        t.setDaemon(true);
        t.start();
    }

    // ── Private implementation ──────────────────────────────────────────────

    private static void doCheck() {
        try {
            String currentVersion = ProjectInfo.version.trim();

            HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setRequestProperty("User-Agent", "Toba-Client/" + currentVersion);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) return; // no release or API issue — silently skip

            String body = readFully(conn.getInputStream());
            JsonObject release = safeParseJson(body);
            if (release == null) return;

            String tagName = jsonStr(release, "tag_name");
            if (tagName.isEmpty()) return;

            // Strip leading 'v' from tag for comparison (e.g. "v1.2.0" -> "1.2.0")
            String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            if (latestVersion.equals(currentVersion)) return; // already on latest

            // Find the .jar asset in the release
            String downloadUrl = "";
            if (release.has("assets") && release.get("assets").isJsonArray()) {
                JsonArray assets = release.getAsJsonArray("assets");
                for (JsonElement el : assets) {
                    if (!el.isJsonObject()) continue;
                    JsonObject asset = el.getAsJsonObject();
                    String name = jsonStr(asset, "name");
                    if (name.endsWith(".jar")) {
                        downloadUrl = jsonStr(asset, "browser_download_url");
                        break;
                    }
                }
            }

            if (downloadUrl.isEmpty()) return;

            // Validate download URL points to GitHub
            try {
                java.net.URL dlUrl = new java.net.URL(downloadUrl);
                if (!dlUrl.getProtocol().equals("https")
                        || (!dlUrl.getHost().equals(ALLOWED_DOWNLOAD_HOST)
                            && !dlUrl.getHost().endsWith("." + ALLOWED_DOWNLOAD_HOST)
                            && !dlUrl.getHost().equals(ALLOWED_CDN_HOST))) {
                    System.err.println("[Toba] Rejected update download URL: " + downloadUrl);
                    return;
                }
            } catch (Exception e) {
                System.err.println("[Toba] Malformed download URL");
                return;
            }

            String changelog = jsonStr(release, "body");

            sendChat("Update available: " + latestVersion, Formatting.YELLOW);
            if (!changelog.isEmpty()) {
                // Show first line of changelog only to avoid chat spam
                String firstLine = changelog.split("\n", 2)[0].trim();
                if (!firstLine.isEmpty()) {
                    sendChat("Changelog: " + firstLine, Formatting.GRAY);
                }
            }

            boolean ok = downloadUpdate(downloadUrl, latestVersion);
            if (ok) {
                disableCurrentJar();
                sendChat("Update downloaded! Restart your game to apply.",
                        Formatting.GREEN);
            } else {
                sendChat("Update download failed. Try again next session.",
                        Formatting.RED);
            }

        } catch (Exception e) {
            System.err.println("[Toba] Update check failed: " + e.getMessage());
        }
    }

    /**
     * Downloads the update JAR into the {@code mods/} directory.
     * Uses a {@code .tmp} suffix during download, then atomically renames.
     */
    private static boolean downloadUpdate(String downloadUrl, String versionLabel) {
        try {
            Path modsDir = getModsDir();
            if (modsDir == null) return false;

            // Sanitize version label to safe filename characters only
            String safeVersion = versionLabel.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path targetPath    = modsDir.resolve("Toba-" + safeVersion + ".jar");

            // Already downloaded — user just hasn't restarted yet.
            if (Files.exists(targetPath)) {
                System.err.println("[Toba] Update JAR already exists: "
                        + targetPath.getFileName());
                return true;
            }

            Path tempFile = modsDir.resolve("Toba-" + safeVersion + ".jar.tmp");

            HttpURLConnection conn = (HttpURLConnection) new URL(downloadUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Toba-Client");
            conn.setConnectTimeout(15_000);
            conn.setReadTimeout(60_000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("[Toba] Download returned HTTP " + code);
                return false;
            }

            try (InputStream in   = conn.getInputStream();
                 OutputStream out  = Files.newOutputStream(tempFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }

            // Atomic rename .tmp → .jar
            Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            System.err.println("[Toba] Downloaded update: " + targetPath.getFileName());
            return true;

        } catch (Exception e) {
            System.err.println("[Toba] Download failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Disables the currently running mod JAR so Fabric won't load it on the
     * next launch. Three-tier fallback:
     * <ol>
     *   <li>Rename to {@code .jar.disabled} (works if file isn't locked)</li>
     *   <li>JVM shutdown hook to delete (lock released at JVM exit)</li>
     *   <li>{@code .delete_me} marker for {@link #cleanupOldJars()} next startup</li>
     * </ol>
     */
    private static void disableCurrentJar() {
        try {
            Optional<ModContainer> container =
                    FabricLoader.getInstance().getModContainer("toba");
            if (container.isEmpty()) return;

            for (Path jarPath : container.get().getOrigin().getPaths()) {
                if (!jarPath.toString().endsWith(".jar")) continue;

                Path disabledPath =
                        jarPath.resolveSibling(jarPath.getFileName() + ".disabled");
                try {
                    Files.move(jarPath, disabledPath);
                    System.err.println("[Toba] Disabled old JAR: "
                            + jarPath.getFileName());
                } catch (IOException e) {
                    // Windows file lock — try shutdown hook
                    System.err.println("[Toba] Could not rename JAR (locked), "
                            + "registering shutdown hook");
                    Path finalJarPath = jarPath;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Files.deleteIfExists(finalJarPath);
                        } catch (IOException ignored) {
                            // Last resort: marker for next launch
                            try {
                                Path marker = finalJarPath.resolveSibling(
                                        finalJarPath.getFileName() + ".delete_me");
                                Files.createFile(marker);
                            } catch (IOException ignored2) {}
                        }
                    }, "Toba-Cleanup-Shutdown"));
                }

                break; // only process the first JAR
            }
        } catch (Exception e) {
            System.err.println("[Toba] Failed to disable old JAR: "
                    + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static Path getModsDir() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.runDirectory == null) return null;
        return mc.runDirectory.toPath().resolve("mods");
    }

    private static String readFully(InputStream is) {
        try (Scanner s = new Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static JsonObject safeParseJson(String raw) {
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static String jsonStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    /**
     * Dispatches a prefixed chat message once the player entity is available.
     */
    private static void sendChat(String message, Formatting color) {
        new Thread(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                for (int i = 0; i < 50; i++) {
                    if (client.player != null) {
                        client.execute(() ->
                                TobaChat.sendColored(message, color));
                        break;
                    }
                    Thread.sleep(100);
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private static void deleteByGlob(Path dir, String glob) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path file : stream) {
                safeDelete(file);
            }
        }
    }

    private static void safeDelete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[Toba] Could not delete "
                    + file.getFileName() + ": " + e.getMessage());
        }
    }
}
