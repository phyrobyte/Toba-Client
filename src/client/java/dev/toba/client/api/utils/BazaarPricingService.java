package dev.toba.client.api.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Fetches and caches Hypixel Skyblock Bazaar pricing data.
 * Auto-refreshes every 60 seconds in a background thread.
 * <p>
 * Usage:
 * <pre>
 *   BazaarPricingService.getInstance().start(); // call once on init
 *   double price = BazaarPricingService.getInstance().getSellPrice("ENCHANTED_DIAMOND");
 * </pre>
 */
public class BazaarPricingService {

    private static BazaarPricingService instance;

    private static final String BAZAAR_API_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final long REFRESH_INTERVAL_SEC = 60;

    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Toba-BazaarPricing");
        t.setDaemon(true);
        return t;
    });

    private final Map<String, BazaarProduct> products = new ConcurrentHashMap<>();
    private volatile long lastUpdateMs = 0;
    private volatile boolean ready = false;
    private volatile boolean started = false;

    public static BazaarPricingService getInstance() {
        if (instance == null) instance = new BazaarPricingService();
        return instance;
    }

    private BazaarPricingService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Start the background refresh loop. Safe to call multiple times — only starts once.
     */
    public void start() {
        if (started) return;
        started = true;
        scheduler.scheduleAtFixedRate(this::refresh, 0, REFRESH_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    public void refreshAsync() {
        scheduler.execute(this::refresh);
    }

    // ── Price getters ──

    public double getSellPrice(String productId) {
        BazaarProduct p = products.get(productId);
        return p != null ? p.sellPrice : 0;
    }

    public double getBuyPrice(String productId) {
        BazaarProduct p = products.get(productId);
        return p != null ? p.buyPrice : 0;
    }

    public long getSellVolume(String productId) {
        BazaarProduct p = products.get(productId);
        return p != null ? p.sellVolume : 0;
    }

    public long getBuyVolume(String productId) {
        BazaarProduct p = products.get(productId);
        return p != null ? p.buyVolume : 0;
    }

    /**
     * Get the full product data object. Returns null if not found.
     */
    public BazaarProduct getProduct(String productId) {
        return products.get(productId);
    }

    /**
     * Get an unmodifiable view of all cached products.
     */
    public Map<String, BazaarProduct> getAllProducts() {
        return Collections.unmodifiableMap(products);
    }

    public int getProductCount() { return products.size(); }
    public boolean isReady() { return ready; }
    public long getLastUpdateMs() { return lastUpdateMs; }

    // ── Refresh logic ──

    private void refresh() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BAZAAR_API_URL))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;

            JsonObject root = gson.fromJson(response.body(), JsonObject.class);
            if (!root.has("success") || !root.get("success").getAsBoolean()) return;

            JsonObject productsObj = root.getAsJsonObject("products");
            if (productsObj == null) return;

            for (Map.Entry<String, JsonElement> entry : productsObj.entrySet()) {
                String productId = entry.getKey();
                JsonObject productData = entry.getValue().getAsJsonObject();

                JsonObject qs = productData.getAsJsonObject("quick_status");
                if (qs == null) continue;

                products.put(productId, new BazaarProduct(
                        productId,
                        getDouble(qs, "sellPrice"),
                        getDouble(qs, "buyPrice"),
                        getLong(qs, "sellVolume"),
                        getLong(qs, "buyVolume"),
                        getLong(qs, "sellMovingWeek"),
                        getLong(qs, "buyMovingWeek"),
                        getInt(qs, "sellOrders"),
                        getInt(qs, "buyOrders")
                ));
            }

            lastUpdateMs = System.currentTimeMillis();
            ready = true;

        } catch (Exception ignored) {
            // Network errors are expected; we'll retry on next interval
        }
    }

    private static double getDouble(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsDouble() : 0;
    }

    private static long getLong(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsLong() : 0;
    }

    private static int getInt(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsInt() : 0;
    }

    /**
     * Holds cached pricing data for a single bazaar product.
     */
    public static class BazaarProduct {
        public final String productId;
        public final double sellPrice;      // Instant sell (weighted avg of top 2% buy orders)
        public final double buyPrice;       // Instant buy (weighted avg of top 2% sell orders)
        public final long sellVolume;       // Total items in all sell orders
        public final long buyVolume;        // Total items in all buy orders
        public final long sellMovingWeek;   // 7-day sell transaction volume
        public final long buyMovingWeek;    // 7-day buy transaction volume
        public final int sellOrders;        // Number of active sell orders
        public final int buyOrders;         // Number of active buy orders

        public BazaarProduct(String productId, double sellPrice, double buyPrice,
                             long sellVolume, long buyVolume,
                             long sellMovingWeek, long buyMovingWeek,
                             int sellOrders, int buyOrders) {
            this.productId = productId;
            this.sellPrice = sellPrice;
            this.buyPrice = buyPrice;
            this.sellVolume = sellVolume;
            this.buyVolume = buyVolume;
            this.sellMovingWeek = sellMovingWeek;
            this.buyMovingWeek = buyMovingWeek;
            this.sellOrders = sellOrders;
            this.buyOrders = buyOrders;
        }

        /** Spread between instant buy and instant sell. */
        public double getSpread() {
            return buyPrice - sellPrice;
        }

        /** Profit margin as a percentage. */
        public double getMarginPercent() {
            return sellPrice > 0 ? (getSpread() / sellPrice) * 100.0 : 0;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Common Hypixel Skyblock product IDs
    // ══════════════════════════════════════════════════════════════

    // ── Farming ──
    public static final String MELON = "MELON";
    public static final String ENCHANTED_MELON = "ENCHANTED_MELON";
    public static final String ENCHANTED_MELON_BLOCK = "ENCHANTED_MELON_BLOCK";
    public static final String PUMPKIN = "PUMPKIN";
    public static final String ENCHANTED_PUMPKIN = "ENCHANTED_PUMPKIN";
    public static final String POLISHED_PUMPKIN = "POLISHED_PUMPKIN";
    public static final String WHEAT = "WHEAT";
    public static final String ENCHANTED_BREAD = "ENCHANTED_BREAD";
    public static final String ENCHANTED_HAY_BALE = "ENCHANTED_HAY_BALE";
    public static final String CARROT_ITEM = "CARROT_ITEM";
    public static final String ENCHANTED_CARROT = "ENCHANTED_CARROT";
    public static final String ENCHANTED_GOLDEN_CARROT = "ENCHANTED_GOLDEN_CARROT";
    public static final String POTATO_ITEM = "POTATO_ITEM";
    public static final String ENCHANTED_POTATO = "ENCHANTED_POTATO";
    public static final String ENCHANTED_BAKED_POTATO = "ENCHANTED_BAKED_POTATO";
    public static final String SUGAR_CANE = "SUGAR_CANE";
    public static final String ENCHANTED_SUGAR = "ENCHANTED_SUGAR";
    public static final String ENCHANTED_SUGAR_CANE = "ENCHANTED_SUGAR_CANE";
    public static final String CACTUS = "CACTUS";
    public static final String ENCHANTED_CACTUS_GREEN = "ENCHANTED_CACTUS_GREEN";
    public static final String ENCHANTED_CACTUS = "ENCHANTED_CACTUS";
    public static final String COCOA_BEANS = "INK_SACK:3";
    public static final String ENCHANTED_COCOA = "ENCHANTED_COCOA";
    public static final String ENCHANTED_COOKIE = "ENCHANTED_COOKIE";
    public static final String NETHER_STALK = "NETHER_STALK";
    public static final String ENCHANTED_NETHER_STALK = "ENCHANTED_NETHER_STALK";
    public static final String MUTANT_NETHER_STALK = "MUTANT_NETHER_STALK";
    public static final String SEEDS = "SEEDS";
    public static final String ENCHANTED_SEEDS = "ENCHANTED_SEEDS";

    // ── Mining ──
    public static final String DIAMOND = "DIAMOND";
    public static final String ENCHANTED_DIAMOND = "ENCHANTED_DIAMOND";
    public static final String ENCHANTED_DIAMOND_BLOCK = "ENCHANTED_DIAMOND_BLOCK";
    public static final String GOLD_INGOT = "GOLD_INGOT";
    public static final String ENCHANTED_GOLD = "ENCHANTED_GOLD";
    public static final String ENCHANTED_GOLD_BLOCK = "ENCHANTED_GOLD_BLOCK";
    public static final String IRON_INGOT = "IRON_INGOT";
    public static final String ENCHANTED_IRON = "ENCHANTED_IRON";
    public static final String ENCHANTED_IRON_BLOCK = "ENCHANTED_IRON_BLOCK";
    public static final String COAL = "COAL";
    public static final String ENCHANTED_COAL = "ENCHANTED_COAL";
    public static final String ENCHANTED_COAL_BLOCK = "ENCHANTED_COAL_BLOCK";
    public static final String COBBLESTONE = "COBBLESTONE";
    public static final String ENCHANTED_COBBLESTONE = "ENCHANTED_COBBLESTONE";
    public static final String MITHRIL_ORE = "MITHRIL_ORE";
    public static final String ENCHANTED_MITHRIL = "ENCHANTED_MITHRIL";
    public static final String REFINED_MITHRIL = "REFINED_MITHRIL";
    public static final String LAPIS_LAZULI = "INK_SACK:4";
    public static final String ENCHANTED_LAPIS_LAZULI = "ENCHANTED_LAPIS_LAZULI";
    public static final String ENCHANTED_LAPIS_LAZULI_BLOCK = "ENCHANTED_LAPIS_LAZULI_BLOCK";
    public static final String REDSTONE = "REDSTONE";
    public static final String ENCHANTED_REDSTONE = "ENCHANTED_REDSTONE";
    public static final String ENCHANTED_REDSTONE_BLOCK = "ENCHANTED_REDSTONE_BLOCK";
    public static final String EMERALD = "EMERALD";
    public static final String ENCHANTED_EMERALD = "ENCHANTED_EMERALD";
    public static final String ENCHANTED_EMERALD_BLOCK = "ENCHANTED_EMERALD_BLOCK";
    public static final String OBSIDIAN = "OBSIDIAN";
    public static final String ENCHANTED_OBSIDIAN = "ENCHANTED_OBSIDIAN";
    public static final String GLOWSTONE_DUST = "GLOWSTONE_DUST";
    public static final String ENCHANTED_GLOWSTONE_DUST = "ENCHANTED_GLOWSTONE_DUST";
    public static final String ENCHANTED_GLOWSTONE = "ENCHANTED_GLOWSTONE";
    public static final String HARD_STONE = "HARD_STONE";
    public static final String TITANIUM_ORE = "TITANIUM_ORE";
    public static final String ENCHANTED_TITANIUM = "ENCHANTED_TITANIUM";

    // ── Combat ──
    public static final String ROTTEN_FLESH = "ROTTEN_FLESH";
    public static final String ENCHANTED_ROTTEN_FLESH = "ENCHANTED_ROTTEN_FLESH";
    public static final String BONE = "BONE";
    public static final String ENCHANTED_BONE = "ENCHANTED_BONE";
    public static final String ENCHANTED_BONE_BLOCK = "ENCHANTED_BONE_BLOCK";
    public static final String ENDER_PEARL = "ENDER_PEARL";
    public static final String ENCHANTED_ENDER_PEARL = "ENCHANTED_ENDER_PEARL";
    public static final String BLAZE_ROD = "BLAZE_ROD";
    public static final String ENCHANTED_BLAZE_ROD = "ENCHANTED_BLAZE_ROD";
    public static final String ENCHANTED_BLAZE_POWDER = "ENCHANTED_BLAZE_POWDER";
    public static final String SPIDER_EYE = "SPIDER_EYE";
    public static final String ENCHANTED_SPIDER_EYE = "ENCHANTED_SPIDER_EYE";
    public static final String ENCHANTED_FERMENTED_SPIDER_EYE = "ENCHANTED_FERMENTED_SPIDER_EYE";
    public static final String STRING = "STRING";
    public static final String ENCHANTED_STRING = "ENCHANTED_STRING";
    public static final String GUNPOWDER = "SULPHUR";
    public static final String ENCHANTED_GUNPOWDER = "ENCHANTED_GUNPOWDER";
    public static final String GHAST_TEAR = "GHAST_TEAR";
    public static final String ENCHANTED_GHAST_TEAR = "ENCHANTED_GHAST_TEAR";
    public static final String SLIME_BALL = "SLIME_BALL";
    public static final String ENCHANTED_SLIME_BALL = "ENCHANTED_SLIME_BALL";
    public static final String ENCHANTED_SLIME_BLOCK = "ENCHANTED_SLIME_BLOCK";
    public static final String MAGMA_CREAM = "MAGMA_CREAM";
    public static final String ENCHANTED_MAGMA_CREAM = "ENCHANTED_MAGMA_CREAM";

    // ── Slayer drops ──
    public static final String REVENANT_FLESH = "REVENANT_FLESH";
    public static final String REVENANT_VISCERA = "REVENANT_VISCERA";
    public static final String TARANTULA_WEB = "TARANTULA_WEB";
    public static final String TARANTULA_SILK = "TARANTULA_SILK";
    public static final String WOLF_TOOTH = "WOLF_TOOTH";
    public static final String GOLDEN_TOOTH = "GOLDEN_TOOTH";
    public static final String NULL_SPHERE = "NULL_SPHERE";

    // ── Wood / Foraging ──
    public static final String LOG = "LOG";
    public static final String LOG_2 = "LOG_2";
    public static final String ENCHANTED_OAK_LOG = "ENCHANTED_OAK_LOG";
    public static final String ENCHANTED_BIRCH_LOG = "ENCHANTED_BIRCH_LOG";
    public static final String ENCHANTED_SPRUCE_LOG = "ENCHANTED_SPRUCE_LOG";
    public static final String ENCHANTED_DARK_OAK_LOG = "ENCHANTED_DARK_OAK_LOG";
    public static final String ENCHANTED_JUNGLE_LOG = "ENCHANTED_JUNGLE_LOG";
    public static final String ENCHANTED_ACACIA_LOG = "ENCHANTED_ACACIA_LOG";

    // ── Fishing ──
    public static final String RAW_FISH = "RAW_FISH";
    public static final String ENCHANTED_RAW_FISH = "ENCHANTED_RAW_FISH";
    public static final String ENCHANTED_COOKED_FISH = "ENCHANTED_COOKED_FISH";
    public static final String RAW_SALMON = "RAW_FISH:1";
    public static final String ENCHANTED_RAW_SALMON = "ENCHANTED_RAW_SALMON";
    public static final String ENCHANTED_COOKED_SALMON = "ENCHANTED_COOKED_SALMON";
    public static final String PUFFERFISH = "RAW_FISH:3";
    public static final String ENCHANTED_PUFFERFISH = "ENCHANTED_PUFFERFISH";
    public static final String CLOWNFISH = "RAW_FISH:2";
    public static final String ENCHANTED_CLOWNFISH = "ENCHANTED_CLOWNFISH";
    public static final String PRISMARINE_SHARD = "PRISMARINE_SHARD";
    public static final String ENCHANTED_PRISMARINE_SHARD = "ENCHANTED_PRISMARINE_SHARD";
    public static final String PRISMARINE_CRYSTALS = "PRISMARINE_CRYSTALS";
    public static final String ENCHANTED_PRISMARINE_CRYSTALS = "ENCHANTED_PRISMARINE_CRYSTALS";
    public static final String SPONGE = "SPONGE";
    public static final String ENCHANTED_SPONGE = "ENCHANTED_SPONGE";
    public static final String ENCHANTED_WET_SPONGE = "ENCHANTED_WET_SPONGE";
    public static final String LILY_PAD = "WATER_LILY";
    public static final String ENCHANTED_LILY_PAD = "ENCHANTED_WATER_LILY";
    public static final String INK_SACK = "INK_SACK";
    public static final String ENCHANTED_INK_SACK = "ENCHANTED_INK_SACK";
}
