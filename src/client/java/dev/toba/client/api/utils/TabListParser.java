package dev.toba.client.api.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Hypixel SkyBlock tab list (player list) widgets.
 * <p>
 * The SkyBlock tab list uses a widget-based system where each widget
 * has a header line and optional content lines. Widgets are identified
 * by their header patterns (color-coded text).
 * <p>
 * Call {@link #refresh()} each tick or as needed, then query parsed data
 * via the getter methods.
 */
public class TabListParser {
    private static TabListParser instance;

    public static TabListParser getInstance() {
        if (instance == null) instance = new TabListParser();
        return instance;
    }

    // ── Parsed data ──

    private final List<String> rawLines = new ArrayList<>();
    private final Map<WidgetType, List<String>> widgets = new EnumMap<>(WidgetType.class);

    // Throttle: skip refresh if called more than once per second
    private long lastRefreshMs = 0;
    private static final long REFRESH_COOLDOWN_MS = 950;

    // ── Garden/farming (existing) ──
    private int pestCount = 0;
    private List<Integer> pestPlots = new ArrayList<>();
    private String farmingLevel = "";
    private double farmingLevelProgress = 0;
    private String gardenLevel = "";
    private int visitorCount = 0;
    private List<String> visitorNames = new ArrayList<>();
    private String jacobContest = "";
    private String jacobCrop = "";
    private int jacobScore = 0;
    private String jacobRank = "";
    private String jacobTimeRemaining = "";
    private String area = "";
    private String serverInfo = "";
    private int copper = 0;

    // ── Profile ──
    private long purse = 0;
    private String profileType = "";

    // ── Bank ──
    private long bankBalance = 0;

    // ── SB Level ──
    private int sbLevel = 0;
    private double sbLevelProgress = 0;

    // ── Gems ──
    private int gems = 0;

    // ── Skills (all, not just farming) ──
    private final Map<String, String> skillLevels = new LinkedHashMap<>();
    private final Map<String, Double> skillProgress = new LinkedHashMap<>();

    // ── Stats ──
    private final Map<String, Integer> stats = new LinkedHashMap<>();

    // ── Active Effects ──
    private final List<ActiveEffect> activeEffects = new ArrayList<>();

    // ── Pet ──
    private String petName = "";
    private int petLevel = 0;
    private String petRarity = "";

    // ── Timers ──
    private final Map<String, String> timers = new LinkedHashMap<>();

    // ── Election ──
    private String currentMayor = "";
    private final List<String> mayorPerks = new ArrayList<>();

    // ── Collection ──
    private String collectionName = "";
    private String collectionProgress = "";

    // ── Minion ──
    private int minionCount = 0;
    private int minionSlots = 0;

    // ── Bestiary ──
    private String bestiaryArea = "";
    private String bestiaryProgress = "";

    // ── Widget types ──

    public enum WidgetType {
        AREA("Area:"),
        SERVER("Server:"),
        PLAYERS("Players"),
        INFO("Info"),
        GARDEN_LEVEL("Garden Level:"),
        COPPER("Copper:"),
        PESTS("Pests:"),
        PEST_TRAPS("Pest Traps:"),
        VISITORS("Visitors:"),
        CROP_MILESTONE("Crop Milestones:"),
        COMPOSTER("Composter:"),
        JACOB_CONTEST("Jacob's Contest"),
        SKILLS("Skills:"),
        ACTIVE_EFFECTS("Active Effects:"),
        COLLECTION("Collection:"),
        PET("Pet:"),
        TIMERS("Timers:"),
        ELECTION("Election:"),
        EVENT("Event:"),
        PROFILE("Profile:"),
        SB_LEVEL("SB Level:"),
        BANK("Bank:"),
        GEMS("Gems:"),
        STATS("Stats:"),
        MINION("Minions:"),
        BESTIARY("Bestiary:"),
        TROPHY_FISH("Trophy Fish:");

        private final String headerPrefix;

        WidgetType(String headerPrefix) {
            this.headerPrefix = headerPrefix;
        }

        public String getHeaderPrefix() {
            return headerPrefix;
        }
    }

    // ── Inner classes ──

    public static class ActiveEffect {
        public final String name;
        public final String duration;
        public final int level;

        public ActiveEffect(String name, String duration, int level) {
            this.name = name;
            this.duration = duration;
            this.level = level;
        }

        @Override
        public String toString() {
            return name + (level > 0 ? " " + toRoman(level) : "") + " " + duration;
        }

        private static String toRoman(int n) {
            if (n <= 0 || n > 10) return String.valueOf(n);
            return new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"}[n];
        }
    }

    // ── Refresh ──

    /**
     * Re-reads the tab list and parses all widgets.
     * Throttled to at most once per second.
     */
    public void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs < REFRESH_COOLDOWN_MS) return;
        lastRefreshMs = now;

        rawLines.clear();
        widgets.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        Collection<PlayerListEntry> entries = handler.getListedPlayerListEntries();

        if (entries == null || entries.isEmpty()) return;

        List<String> allLines = new ArrayList<>();
        List<String> allLinesRaw = new ArrayList<>();
        for (PlayerListEntry entry : entries) {
            Text displayName = entry.getDisplayName();
            if (displayName == null) continue;

            String raw = displayName.getString();
            if (raw == null || raw.isBlank()) continue;

            allLinesRaw.add(raw.trim());
            allLines.add(stripColorCodes(raw).trim());
        }

        rawLines.addAll(allLinesRaw);

        // Parse widgets by finding header lines
        parseWidgets(allLines);

        // Extract specific values from each widget
        parsePests();
        parseGardenLevel();
        parseVisitors();
        parseJacobContest();
        parseArea();
        parseServer();
        parseCopper();
        parseSkills();
        parseProfile();
        parseBank();
        parseSbLevel();
        parseGems();
        parseStats();
        parseActiveEffects();
        parsePet();
        parseTimers();
        parseElection();
        parseCollection();
        parseMinions();
        parseBestiary();
    }

    // ── Widget parsing ──

    private void parseWidgets(List<String> lines) {
        WidgetType currentWidget = null;
        List<String> currentLines = null;

        for (String line : lines) {
            WidgetType detected = detectWidgetHeader(line);
            if (detected != null) {
                if (currentWidget != null && currentLines != null) {
                    widgets.put(currentWidget, new ArrayList<>(currentLines));
                }
                currentWidget = detected;
                currentLines = new ArrayList<>();
                currentLines.add(line);
            } else if (currentWidget != null && currentLines != null) {
                if (!line.isEmpty()) {
                    currentLines.add(line);
                }
            }
        }

        if (currentWidget != null && currentLines != null) {
            widgets.put(currentWidget, new ArrayList<>(currentLines));
        }
    }

    private WidgetType detectWidgetHeader(String line) {
        for (WidgetType type : WidgetType.values()) {
            if (line.contains(type.getHeaderPrefix())) {
                return type;
            }
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    //  Widget-specific parsers
    // ══════════════════════════════════════════════════════════════

    private void parsePests() {
        pestCount = 0;
        pestPlots = new ArrayList<>();

        List<String> lines = widgets.get(WidgetType.PESTS);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            if (line.contains("Plots:")) {
                String plotStr = line.substring(line.indexOf("Plots:") + 6).trim();
                if (!plotStr.isEmpty() && !plotStr.equalsIgnoreCase("none")) {
                    String[] parts = plotStr.split(",");
                    for (String part : parts) {
                        try { pestPlots.add(Integer.parseInt(part.trim())); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        for (String line : lines) {
            Matcher m = Pattern.compile("(\\d+)\\s*(?:pest|Pest)").matcher(line);
            if (m.find()) {
                pestCount = Integer.parseInt(m.group(1));
                return;
            }
        }
        pestCount = pestPlots.size();
    }

    private void parseGardenLevel() {
        gardenLevel = "";
        List<String> lines = widgets.get(WidgetType.GARDEN_LEVEL);
        if (lines == null || lines.isEmpty()) return;
        Matcher m = Pattern.compile("Garden Level:\\s*(.+)").matcher(lines.get(0));
        if (m.find()) gardenLevel = m.group(1).trim();
    }

    private void parseVisitors() {
        visitorCount = 0;
        visitorNames = new ArrayList<>();
        List<String> lines = widgets.get(WidgetType.VISITORS);
        if (lines == null || lines.isEmpty()) return;

        Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(lines.get(0));
        if (m.find()) visitorCount = Integer.parseInt(m.group(1));

        for (int i = 1; i < lines.size(); i++) {
            String name = lines.get(i).trim();
            if (!name.isEmpty()) visitorNames.add(name);
        }
    }

    private void parseJacobContest() {
        jacobContest = "";
        jacobCrop = "";
        jacobScore = 0;
        jacobRank = "";
        jacobTimeRemaining = "";

        List<String> lines = widgets.get(WidgetType.JACOB_CONTEST);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            if (line.contains("Jacob's Contest")) jacobContest = line;
            if (line.contains("Ends in")) {
                Matcher m = Pattern.compile("Ends in\\s*(.+)").matcher(line);
                if (m.find()) jacobTimeRemaining = m.group(1).trim();
            }
            if (line.contains("Score:") || line.contains("Total Score:")) {
                Matcher m = Pattern.compile("(?:Total )?Score:\\s*([\\d,.kKmM]+)").matcher(line);
                if (m.find()) jacobScore = parseShortNumber(m.group(1));
            }
            if (line.contains("Rank:") || line.contains("No Rank")) {
                jacobRank = line.trim();
            }
            String lower = line.toLowerCase();
            for (String crop : new String[]{"melon", "pumpkin", "wheat", "carrot", "potato",
                    "sugar cane", "cactus", "cocoa beans", "mushroom", "nether wart"}) {
                if (lower.contains(crop)) { jacobCrop = line.trim(); break; }
            }
        }
    }

    private void parseArea() {
        area = "";
        List<String> lines = widgets.get(WidgetType.AREA);
        if (lines == null || lines.isEmpty()) return;
        Matcher m = Pattern.compile("Area:\\s*(.+)").matcher(lines.get(0));
        if (m.find()) area = m.group(1).trim();
    }

    private void parseServer() {
        serverInfo = "";
        List<String> lines = widgets.get(WidgetType.SERVER);
        if (lines == null || lines.isEmpty()) return;
        Matcher m = Pattern.compile("Server:\\s*(.+)").matcher(lines.get(0));
        if (m.find()) serverInfo = m.group(1).trim();
    }

    private void parseCopper() {
        copper = 0;
        List<String> lines = widgets.get(WidgetType.COPPER);
        if (lines == null || lines.isEmpty()) return;
        Matcher m = Pattern.compile("Copper:\\s*([\\d,]+)").matcher(lines.get(0));
        if (m.find()) copper = Integer.parseInt(m.group(1).replace(",", ""));
    }

    private void parseSkills() {
        farmingLevel = "";
        farmingLevelProgress = 0;
        skillLevels.clear();
        skillProgress.clear();

        List<String> lines = widgets.get(WidgetType.SKILLS);
        if (lines == null) return;

        String[] skillNames = {"Farming", "Combat", "Mining", "Foraging", "Fishing",
                "Enchanting", "Alchemy", "Taming", "Carpentry", "Runecrafting", "Social"};

        for (String line : lines) {
            for (String skill : skillNames) {
                if (line.contains(skill)) {
                    // Extract level: "Farming XLV" or "Combat 60"
                    Matcher m = Pattern.compile(skill + "\\s+([IVXLCDM]+|\\d+)").matcher(line);
                    if (m.find()) {
                        String level = m.group(1).trim();
                        skillLevels.put(skill, level);

                        // Special case: keep backward compatibility for farming
                        if ("Farming".equals(skill)) farmingLevel = level;
                    }

                    // Extract progress percentage
                    Matcher pm = Pattern.compile("([\\d.]+)%").matcher(line);
                    if (pm.find()) {
                        double progress = Double.parseDouble(pm.group(1));
                        skillProgress.put(skill, progress);

                        if ("Farming".equals(skill)) farmingLevelProgress = progress;
                    }
                    break;
                }
            }
        }
    }

    private void parseProfile() {
        purse = 0;
        profileType = "";

        List<String> lines = widgets.get(WidgetType.PROFILE);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            // "Purse: 1,234,567" or "Piggy Bank: 1,234,567"
            Matcher pm = Pattern.compile("(?:Purse|Piggy Bank):\\s*([\\d,.kKmMbB]+)").matcher(line);
            if (pm.find()) purse = parseLongNumber(pm.group(1));

            // Profile type: "Ironman", "Stranded", etc.
            for (String type : new String[]{"Ironman", "Stranded", "Bingo"}) {
                if (line.contains(type)) { profileType = type; break; }
            }
        }
    }

    private void parseBank() {
        bankBalance = 0;
        List<String> lines = widgets.get(WidgetType.BANK);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            Matcher m = Pattern.compile("Bank:\\s*([\\d,.kKmMbB]+)").matcher(line);
            if (m.find()) { bankBalance = parseLongNumber(m.group(1)); return; }
            // Sometimes balance is on a separate line
            Matcher m2 = Pattern.compile("([\\d,.]+[kKmMbB]?)").matcher(line);
            if (!line.contains("Bank:") && m2.find()) {
                long val = parseLongNumber(m2.group(1));
                if (val > 0) bankBalance = val;
            }
        }
    }

    private void parseSbLevel() {
        sbLevel = 0;
        sbLevelProgress = 0;

        List<String> lines = widgets.get(WidgetType.SB_LEVEL);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            // "SB Level: [205]" or "SB Level: 205"
            Matcher m = Pattern.compile("SB Level:\\s*\\[?(\\d+)]?").matcher(line);
            if (m.find()) sbLevel = Integer.parseInt(m.group(1));

            Matcher pm = Pattern.compile("([\\d.]+)%").matcher(line);
            if (pm.find()) sbLevelProgress = Double.parseDouble(pm.group(1));
        }
    }

    private void parseGems() {
        gems = 0;
        List<String> lines = widgets.get(WidgetType.GEMS);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            Matcher m = Pattern.compile("Gems:\\s*([\\d,]+)").matcher(line);
            if (m.find()) { gems = Integer.parseInt(m.group(1).replace(",", "")); return; }
        }
    }

    private void parseStats() {
        stats.clear();
        List<String> lines = widgets.get(WidgetType.STATS);
        if (lines == null || lines.isEmpty()) return;

        // Stat symbols map to stat names
        // "❤ Health 1,000" or "❁ Strength 500" or just "Speed 400"
        String[] statNames = {"Health", "Defense", "Strength", "Speed", "Crit Chance",
                "Crit Damage", "Attack Speed", "Intelligence", "Sea Creature Chance",
                "Magic Find", "Pet Luck", "Ferocity", "Ability Damage", "Mining Speed",
                "Mining Fortune", "Farming Fortune", "Foraging Fortune", "True Defense",
                "Vitality", "Mending", "Bonus Attack Speed"};

        for (String line : lines) {
            for (String stat : statNames) {
                if (line.contains(stat)) {
                    // Extract the number after the stat name
                    Matcher m = Pattern.compile(stat + "\\s*:?\\s*([\\d,]+)").matcher(line);
                    if (m.find()) {
                        stats.put(stat, Integer.parseInt(m.group(1).replace(",", "")));
                    } else {
                        // Try extracting any number from the line
                        Matcher m2 = Pattern.compile("([\\d,]+)").matcher(
                                line.substring(line.indexOf(stat) + stat.length()));
                        if (m2.find()) {
                            stats.put(stat, Integer.parseInt(m2.group(1).replace(",", "")));
                        }
                    }
                    break;
                }
            }
        }
    }

    private void parseActiveEffects() {
        activeEffects.clear();
        List<String> lines = widgets.get(WidgetType.ACTIVE_EFFECTS);
        if (lines == null || lines.size() <= 1) return;

        // Lines after header: "Haste III  12:30" or "Speed V  1h 2m"
        // Format varies: "EffectName [Roman] TimeRemaining"
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            // Try to match: Name [optional roman numeral] time
            // "Haste III 12:30" or "Critical VI 45:00" or "Speed 120 1:00:00"
            Matcher m = Pattern.compile("(.+?)\\s+([IVXLCDM]+)\\s+(\\S+.*)").matcher(line);
            if (m.find()) {
                String name = m.group(1).trim();
                int level = romanToInt(m.group(2).trim());
                String duration = m.group(3).trim();
                activeEffects.add(new ActiveEffect(name, duration, level));
                continue;
            }

            // No roman numeral: "EffectName TimeRemaining"
            Matcher m2 = Pattern.compile("(.+?)\\s+(\\d+[:\\dhms ]+.*)").matcher(line);
            if (m2.find()) {
                activeEffects.add(new ActiveEffect(m2.group(1).trim(), m2.group(2).trim(), 0));
                continue;
            }

            // Fallback: just store the whole line as effect name with no duration
            activeEffects.add(new ActiveEffect(line, "", 0));
        }
    }

    private void parsePet() {
        petName = "";
        petLevel = 0;
        petRarity = "";

        List<String> lines = widgets.get(WidgetType.PET);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            // "[Lvl 100] Legendary Elephant" or "[Lvl 87] Epic Black Cat"
            Matcher m = Pattern.compile("\\[Lvl\\s*(\\d+)]\\s*(.+)").matcher(line);
            if (m.find()) {
                petLevel = Integer.parseInt(m.group(1));
                String rest = m.group(2).trim();
                // First word is rarity, rest is pet name
                String[] parts = rest.split("\\s+", 2);
                if (parts.length == 2) {
                    petRarity = parts[0];
                    petName = parts[1];
                } else {
                    petName = rest;
                }
                return;
            }
            // Fallback: just grab non-header lines
            if (!line.contains("Pet:") && !line.isEmpty()) {
                petName = line.trim();
            }
        }
    }

    private void parseTimers() {
        timers.clear();
        List<String> lines = widgets.get(WidgetType.TIMERS);
        if (lines == null || lines.size() <= 1) return;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            // "Dark Auction: 5m 30s" or "New Year Celebration: 1h 2m"
            // Also: "Spooky Festival: In 2h 15m" or "Event: Starts in 5m"
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0 && colonIdx < line.length() - 1) {
                String eventName = line.substring(0, colonIdx).trim();
                String timeStr = line.substring(colonIdx + 1).trim();
                // Remove leading "In " or "Starts in "
                timeStr = timeStr.replaceFirst("(?i)^(?:starts? )?in\\s*", "");
                timers.put(eventName, timeStr);
            }
        }
    }

    private void parseElection() {
        currentMayor = "";
        mayorPerks.clear();

        List<String> lines = widgets.get(WidgetType.ELECTION);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            // "Mayor: Diana" or header with mayor name
            Matcher m = Pattern.compile("(?:Mayor|Current):\\s*(.+)").matcher(line);
            if (m.find()) {
                currentMayor = m.group(1).trim();
                continue;
            }
            // Perk lines (non-header, non-mayor)
            if (!line.contains("Election:") && !line.contains("Mayor:") && !line.isEmpty()) {
                mayorPerks.add(line.trim());
            }
        }
    }

    private void parseCollection() {
        collectionName = "";
        collectionProgress = "";

        List<String> lines = widgets.get(WidgetType.COLLECTION);
        if (lines == null || lines.size() <= 1) return;

        // First content line might be the collection being tracked
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            if (collectionName.isEmpty()) {
                collectionName = line;
            } else {
                // Progress: "1,234/5,000" or "45.2%"
                collectionProgress = line;
                break;
            }
        }
    }

    private void parseMinions() {
        minionCount = 0;
        minionSlots = 0;

        List<String> lines = widgets.get(WidgetType.MINION);
        if (lines == null || lines.isEmpty()) return;

        for (String line : lines) {
            // "Minions: 24/29"
            Matcher m = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)").matcher(line);
            if (m.find()) {
                minionCount = Integer.parseInt(m.group(1));
                minionSlots = Integer.parseInt(m.group(2));
                return;
            }
        }
    }

    private void parseBestiary() {
        bestiaryArea = "";
        bestiaryProgress = "";

        List<String> lines = widgets.get(WidgetType.BESTIARY);
        if (lines == null || lines.size() <= 1) return;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            if (bestiaryArea.isEmpty()) {
                bestiaryArea = line;
            } else {
                bestiaryProgress = line;
                break;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Getters — Garden/Farming (existing)
    // ══════════════════════════════════════════════════════════════

    public int getPestCount() { return pestCount; }
    public List<Integer> getPestPlots() { return pestPlots; }
    public String getFarmingLevel() { return farmingLevel; }
    public double getFarmingLevelProgress() { return farmingLevelProgress; }
    public String getGardenLevel() { return gardenLevel; }
    public int getVisitorCount() { return visitorCount; }
    public List<String> getVisitorNames() { return visitorNames; }
    public String getJacobContest() { return jacobContest; }
    public String getJacobCrop() { return jacobCrop; }
    public int getJacobScore() { return jacobScore; }
    public String getJacobRank() { return jacobRank; }
    public String getJacobTimeRemaining() { return jacobTimeRemaining; }
    public String getArea() { return area; }
    public String getServerInfo() { return serverInfo; }
    public int getCopper() { return copper; }

    // ══════════════════════════════════════════════════════════════
    //  Getters — Profile / Economy
    // ══════════════════════════════════════════════════════════════

    public long getPurse() { return purse; }
    public String getProfileType() { return profileType; }
    public long getBankBalance() { return bankBalance; }
    public int getSbLevel() { return sbLevel; }
    public double getSbLevelProgress() { return sbLevelProgress; }
    public int getGems() { return gems; }

    // ══════════════════════════════════════════════════════════════
    //  Getters — Skills (all)
    // ══════════════════════════════════════════════════════════════

    /** Get level string for any skill (e.g., "XLV", "60"). */
    public String getSkillLevel(String skillName) {
        return skillLevels.getOrDefault(skillName, "");
    }

    /** Get progress percentage for any skill. */
    public double getSkillProgress(String skillName) {
        return skillProgress.getOrDefault(skillName, 0.0);
    }

    /** Get all parsed skill levels. */
    public Map<String, String> getAllSkillLevels() {
        return Collections.unmodifiableMap(skillLevels);
    }

    /** Get all parsed skill progress percentages. */
    public Map<String, Double> getAllSkillProgress() {
        return Collections.unmodifiableMap(skillProgress);
    }

    // ══════════════════════════════════════════════════════════════
    //  Getters — Combat / Macro relevant
    // ══════════════════════════════════════════════════════════════

    /** Get a specific stat value. Returns 0 if not found. */
    public int getStat(String statName) {
        return stats.getOrDefault(statName, 0);
    }

    /** Get all parsed stats. */
    public Map<String, Integer> getAllStats() {
        return Collections.unmodifiableMap(stats);
    }

    /** Get all active potion effects. */
    public List<ActiveEffect> getActiveEffects() {
        return Collections.unmodifiableList(activeEffects);
    }

    public String getPetName() { return petName; }
    public int getPetLevel() { return petLevel; }
    public String getPetRarity() { return petRarity; }

    /** Get all event timers (event name -> time remaining). */
    public Map<String, String> getTimers() {
        return Collections.unmodifiableMap(timers);
    }

    /** Get a specific timer value. Returns empty string if not found. */
    public String getTimer(String eventName) {
        return timers.getOrDefault(eventName, "");
    }

    // ══════════════════════════════════════════════════════════════
    //  Getters — Election / Collection / Minion / Bestiary
    // ══════════════════════════════════════════════════════════════

    public String getCurrentMayor() { return currentMayor; }
    public List<String> getMayorPerks() { return Collections.unmodifiableList(mayorPerks); }
    public String getCollectionName() { return collectionName; }
    public String getCollectionProgress() { return collectionProgress; }
    public int getMinionCount() { return minionCount; }
    public int getMinionSlots() { return minionSlots; }
    public String getBestiaryArea() { return bestiaryArea; }
    public String getBestiaryProgress() { return bestiaryProgress; }

    // ══════════════════════════════════════════════════════════════
    //  Generic widget access
    // ══════════════════════════════════════════════════════════════

    /** Get raw widget lines for a specific widget type. First element is the header. */
    public List<String> getWidgetLines(WidgetType type) {
        return widgets.getOrDefault(type, Collections.emptyList());
    }

    /** Check if a widget is present in the current tab list. */
    public boolean hasWidget(WidgetType type) {
        return widgets.containsKey(type);
    }

    public boolean isOnGarden() {
        return area.contains("Garden") || widgets.containsKey(WidgetType.GARDEN_LEVEL);
    }

    public List<String> getRawLines() {
        return rawLines;
    }

    // ══════════════════════════════════════════════════════════════
    //  Utilities
    // ══════════════════════════════════════════════════════════════

    public static String stripColorCodes(String input) {
        if (input == null) return "";
        return input.replaceAll("§[0-9a-fk-or]", "");
    }

    private int parseShortNumber(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.replace(",", "");
        try {
            String lower = s.toLowerCase();
            if (lower.endsWith("k")) return (int) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000);
            if (lower.endsWith("m")) return (int) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000_000);
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) { return 0; }
    }

    private long parseLongNumber(String s) {
        if (s == null || s.isEmpty()) return 0;
        s = s.replace(",", "").trim();
        try {
            String lower = s.toLowerCase();
            if (lower.endsWith("b")) return (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000_000_000L);
            if (lower.endsWith("m")) return (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1_000_000L);
            if (lower.endsWith("k")) return (long) (Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000L);
            return (long) Double.parseDouble(s);
        } catch (NumberFormatException e) { return 0; }
    }

    private static int romanToInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        int result = 0;
        Map<Character, Integer> values = Map.of(
                'I', 1, 'V', 5, 'X', 10, 'L', 50,
                'C', 100, 'D', 500, 'M', 1000);
        for (int i = 0; i < s.length(); i++) {
            int current = values.getOrDefault(s.charAt(i), 0);
            int next = (i + 1 < s.length()) ? values.getOrDefault(s.charAt(i + 1), 0) : 0;
            if (current < next) result -= current;
            else result += current;
        }
        return result;
    }
}
