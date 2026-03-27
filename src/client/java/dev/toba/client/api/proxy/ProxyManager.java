package dev.toba.client.api.proxy;

import com.google.common.net.InetAddresses;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.ProxyHandler;
import io.netty.resolver.NoopAddressResolverGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ServerAddress;
import org.slf4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProxyManager {
    public static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int CONFIG_VERSION = 1;
    private static final String DEFAULT_TEST_TARGET = "example.com:80";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProxyManager INSTANCE = new ProxyManager();
    private static final ExecutorService TEST_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Toba-Proxy-Test");
        thread.setDaemon(true);
        return thread;
    });

    private volatile List<ProxyProfile> profiles = List.of();
    private volatile UUID activeProfileId;

    private ProxyManager() {
    }

    public static ProxyManager getInstance() {
        return INSTANCE;
    }

    public synchronized void load() {
        try {
            Path path = getConfigPath();
            if (!Files.exists(path)) {
                profiles = List.of();
                activeProfileId = null;
                return;
            }

            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            List<ProxyProfile> loadedProfiles = new ArrayList<>();
            if (root.has("profiles") && root.get("profiles").isJsonArray()) {
                JsonArray profilesArray = root.getAsJsonArray("profiles");
                for (JsonElement element : profilesArray) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    ProxyProfile profile = readProfile(element.getAsJsonObject());
                    if (profile != null && profile.isPersistable()) {
                        loadedProfiles.add(profile);
                    }
                }
            }

            UUID loadedActiveProfileId = parseUuid(root.get("activeProfileId"));
            if (loadedActiveProfileId != null) {
                UUID candidateActiveProfileId = loadedActiveProfileId;
                if (loadedProfiles.stream().noneMatch(profile -> profile.id().equals(candidateActiveProfileId))) {
                    loadedActiveProfileId = null;
                }
            }

            profiles = List.copyOf(loadedProfiles);
            activeProfileId = loadedActiveProfileId;
        } catch (Exception e) {
            LOGGER.error("[Toba] Failed to load proxy config", e);
            profiles = List.of();
            activeProfileId = null;
        }
    }

    public synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", CONFIG_VERSION);
            if (activeProfileId != null) {
                root.addProperty("activeProfileId", activeProfileId.toString());
            } else {
                root.add("activeProfileId", JsonNull.INSTANCE);
            }

            JsonArray profilesArray = new JsonArray();
            for (ProxyProfile profile : profiles) {
                profilesArray.add(writeProfile(profile));
            }
            root.add("profiles", profilesArray);

            Path path = getConfigPath();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception e) {
            LOGGER.error("[Toba] Failed to save proxy config", e);
        }
    }

    public synchronized void upsertProfile(ProxyProfile profile) {
        if (profile == null || !profile.isPersistable()) {
            throw new IllegalArgumentException("Proxy profile is invalid");
        }

        List<ProxyProfile> updated = new ArrayList<>(profiles);
        boolean replaced = false;
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(profile.id())) {
                updated.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updated.add(profile);
        }

        profiles = List.copyOf(updated);
        save();
    }

    public synchronized void removeProfile(UUID id) {
        if (id == null) {
            return;
        }

        List<ProxyProfile> updated = new ArrayList<>(profiles);
        updated.removeIf(profile -> profile.id().equals(id));
        profiles = List.copyOf(updated);

        if (id.equals(activeProfileId)) {
            activeProfileId = null;
        }
        save();
    }

    public synchronized void setActiveProfile(UUID id) {
        if (id == null || profiles.stream().noneMatch(profile -> profile.id().equals(id))) {
            throw new IllegalArgumentException("Unknown proxy profile");
        }

        activeProfileId = id;
        save();
    }

    public synchronized void clearActiveProfile() {
        activeProfileId = null;
        save();
    }

    public List<ProxyProfile> getProfiles() {
        return profiles;
    }

    public Optional<ProxyProfile> getProfile(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return profiles.stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    public ProxyProfile getActiveProfile() {
        UUID currentId = activeProfileId;
        if (currentId == null) {
            return null;
        }

        for (ProxyProfile profile : profiles) {
            if (profile.id().equals(currentId)) {
                return profile;
            }
        }
        return null;
    }

    public boolean hasActiveProxy() {
        return getActiveProfile() != null;
    }

    public boolean isActive(UUID id) {
        return id != null && id.equals(activeProfileId);
    }

    public boolean shouldProxy(ServerAddress target) {
        return getActiveProfile() != null && target != null && !shouldBypassHost(target.getAddress());
    }

    public boolean shouldProxy(InetSocketAddress target) {
        if (getActiveProfile() == null || target == null) {
            return false;
        }

        if (shouldBypassHost(target.getHostString())) {
            return false;
        }

        InetAddress address = target.getAddress();
        return address == null || !isLocalAddress(address);
    }

    public ProxyHandler createActiveProxyHandler() {
        ProxyProfile activeProfile = getActiveProfile();
        if (activeProfile == null) {
            throw new IllegalStateException("No active proxy is configured");
        }

        ProxyHandler proxyHandler = activeProfile.createProxyHandler();
        proxyHandler.setConnectTimeoutMillis(CONNECT_TIMEOUT_MILLIS);
        return proxyHandler;
    }

    public String getButtonLabel() {
        ProxyProfile activeProfile = getActiveProfile();
        if (activeProfile == null) {
            return "Proxy: Direct";
        }
        return "Proxy: " + truncate(activeProfile.displayName(), 10);
    }

    public String getTooltipText() {
        ProxyProfile activeProfile = getActiveProfile();
        if (activeProfile == null) {
            return "Direct connection.\nClick to add or manage SOCKS proxies.";
        }

        return activeProfile.displayName()
                + "\n" + activeProfile.shortSummary()
                + "\n" + activeProfile.authSummary()
                + "\nRemote DNS through proxy"
                + "\nClick to manage proxies.";
    }

    public CompletableFuture<ProxyTestResult> testProxy(ProxyProfile profile, String preferredTarget) {
        return CompletableFuture.supplyAsync(() -> testProxyBlocking(profile, preferredTarget), TEST_EXECUTOR);
    }

    private ProxyTestResult testProxyBlocking(ProxyProfile profile, String preferredTarget) {
        long start = System.nanoTime();
        String targetAddress = sanitizeTestTarget(preferredTarget);
        ServerAddress serverAddress = ServerAddress.parse(targetAddress);
        InetSocketAddress remoteTarget = InetSocketAddress.createUnresolved(serverAddress.getAddress(), serverAddress.getPort());

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1, runnable -> {
            Thread thread = new Thread(runnable, "Toba-Proxy-Test-Loop");
            thread.setDaemon(true);
            return thread;
        });

        Channel channel = null;
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .resolver(NoopAddressResolverGroup.INSTANCE)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                    .handler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel channel) {
                            ProxyHandler proxyHandler = profile.createProxyHandler();
                            proxyHandler.setConnectTimeoutMillis(CONNECT_TIMEOUT_MILLIS);
                            channel.pipeline().addLast("proxy", proxyHandler);
                        }
                    });

            ChannelFuture future = bootstrap.connect(remoteTarget);
            future.awaitUninterruptibly();
            if (!future.isSuccess()) {
                throw unwrap(future.cause());
            }

            channel = future.channel();
            return ProxyTestResult.success(
                    "Connected through " + profile.shortSummary(),
                    targetAddress,
                    elapsedMillis(start)
            );
        } catch (Exception e) {
            return ProxyTestResult.failure(cleanMessage(e), targetAddress, elapsedMillis(start));
        } finally {
            if (channel != null) {
                channel.close().awaitUninterruptibly();
            }
            eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
        }
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static Exception unwrap(Throwable throwable) {
        if (throwable instanceof Exception exception) {
            return exception;
        }
        return new Exception(throwable == null ? "Unknown proxy error" : throwable.getMessage(), throwable);
    }

    private static String cleanMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getMessage() == null) {
            current = current.getCause();
        }

        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable == null ? "Proxy test failed" : throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static String sanitizeTestTarget(String preferredTarget) {
        if (preferredTarget != null) {
            String trimmed = preferredTarget.trim();
            if (!trimmed.isEmpty() && ServerAddress.isValid(trimmed)) {
                return trimmed;
            }
        }
        return DEFAULT_TEST_TARGET;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static boolean shouldBypassHost(String host) {
        if (host == null) {
            return false;
        }

        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.equals("localhost") || normalized.endsWith(".local")) {
            return true;
        }

        try {
            return isLocalAddress(InetAddresses.forString(normalized));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean isLocalAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet6Address inet6Address) {
            byte first = inet6Address.getAddress()[0];
            return (first & 0xFE) == 0xFC;
        }

        return false;
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("toba").resolve("proxies.json");
    }

    private static ProxyProfile readProfile(JsonObject profileObject) {
        try {
            UUID id = parseUuid(profileObject.get("id"));
            ProxyType type = profileObject.has("type")
                    ? ProxyType.valueOf(profileObject.get("type").getAsString())
                    : ProxyType.SOCKS5;

            return new ProxyProfile(
                    id == null ? UUID.randomUUID() : id,
                    readPlainString(profileObject, "name"),
                    type,
                    readPlainString(profileObject, "host"),
                    readPort(profileObject),
                    readSecret(profileObject, "username"),
                    readSecret(profileObject, "password")
            );
        } catch (Exception e) {
            LOGGER.warn("[Toba] Skipping invalid proxy profile entry", e);
            return null;
        }
    }

    private static JsonObject writeProfile(ProxyProfile profile) throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("id", profile.id().toString());
        object.addProperty("name", profile.name());
        object.addProperty("type", profile.type().name());
        object.addProperty("host", profile.host());
        object.addProperty("port", profile.port());
        object.addProperty("username", encrypt(profile.username()));
        object.addProperty("password", encrypt(profile.password()));
        return object;
    }

    private static String readPlainString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static String readSecret(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }

        String raw = object.get(key).getAsString();
        if (raw.isBlank()) {
            return "";
        }

        try {
            return decrypt(raw);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private static int readPort(JsonObject object) {
        if (object == null || !object.has("port") || object.get("port").isJsonNull()) {
            return 0;
        }
        return object.get("port").getAsInt();
    }

    private static UUID parseUuid(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }

        try {
            String raw = element.getAsString();
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SecretKey deriveKey() throws Exception {
        String username = System.getProperty("user.name", "toba");
        byte[] raw = MessageDigest.getInstance("SHA-256")
                .digest(("toba-cfg-v1:" + username).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(raw, "AES");
    }

    private static String encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private static String decrypt(String cipherBase64) throws Exception {
        byte[] combined = Base64.getDecoder().decode(cipherBase64);
        byte[] iv = new byte[12];
        byte[] ciphertext = new byte[combined.length - 12];
        System.arraycopy(combined, 0, iv, 0, 12);
        System.arraycopy(combined, 12, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
