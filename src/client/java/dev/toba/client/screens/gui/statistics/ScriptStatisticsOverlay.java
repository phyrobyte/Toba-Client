package dev.toba.client.screens.gui.statistics;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import dev.toba.client.api.utils.BazaarPricingService;
import dev.toba.client.api.utils.TabListParser;
import dev.toba.client.features.render.uptime.ScriptModule;
import dev.toba.client.features.settings.Module;
import imgui.*;
import imgui.flag.*;

import java.util.List;

/**
 * ImGui-based statistics overlay on the right side of the screen.
 * <p>
 * Three sections: Crop stats, Skills, and Jacob's Contest (only shown when active).
 * Automatically stops rendering when the module is disabled (including on disconnect).
 */
public class ScriptStatisticsOverlay implements RenderInterface {

    private final Module module;
    private final ScriptModule scriptModule;

    // ── Theme colors (ABGR packed) ──
    private static final int COL_BG = imColor(20, 20, 30, 210);
    private static final int COL_BORDER = imColor(60, 60, 80, 180);

    private static final int COL_HEADER_CROP = imColor(85, 255, 255, 255);
    private static final int COL_HEADER_SKILLS = imColor(85, 255, 85, 255);
    private static final int COL_HEADER_JACOB = imColor(255, 170, 0, 255);

    private static final int COL_LABEL = imColor(170, 170, 170, 255);
    private static final int COL_VALUE_WHITE = imColor(255, 255, 255, 255);
    private static final int COL_VALUE_CYAN = imColor(85, 255, 255, 255);
    private static final int COL_VALUE_GREEN = imColor(85, 255, 85, 255);
    private static final int COL_VALUE_YELLOW = imColor(255, 255, 85, 255);
    private static final int COL_VALUE_RED = imColor(255, 85, 85, 255);
    private static final int COL_VALUE_GOLD = imColor(255, 170, 0, 255);
    private static final int COL_SUBTEXT = imColor(130, 130, 130, 255);

    // Layout — larger than before
    private static final float PANEL_WIDTH = 240;
    private static final float PADDING = 10;
    private static final float LINE_HEIGHT = 18;
    private static final float SECTION_GAP = 8;
    private static final float MARGIN_RIGHT = 6;
    private static final float MARGIN_TOP = 30; // below top edge, avoids boss bars etc.

    public ScriptStatisticsOverlay(Module module) {
        if (!(module instanceof ScriptModule)) {
            throw new IllegalArgumentException("Module must implement ScriptModule");
        }
        this.module = module;
        this.scriptModule = (ScriptModule) module;
    }

    @Override
    public void render(ImGuiIO io) {
        // Stop rendering entirely if module is disabled (including on disconnect)
        if (!module.isEnabled()) return;

        float displayW = io.getDisplaySizeX();
        float displayH = io.getDisplaySizeY();
        if (displayW <= 0 || displayH <= 0) return;

        ImDrawList draw = ImGui.getForegroundDrawList();

        long elapsed = System.currentTimeMillis() - scriptModule.getStartTimeMillis();
        String[] statsLines = scriptModule.getStatsLines();
        TabListParser tab = TabListParser.getInstance();

        // Check if Jacob's contest is active
        boolean jacobActive = isJacobContestActive(tab);

        // ── Calculate total panel height ──
        float totalHeight = PADDING;

        // Crop section: header + subtitle + stat lines
        totalHeight += LINE_HEIGHT; // crop name + runtime
        totalHeight += LINE_HEIGHT; // "Farming" subtitle
        for (String ignored : statsLines) totalHeight += LINE_HEIGHT;
        totalHeight += SECTION_GAP;

        // Skills section
        totalHeight += LINE_HEIGHT; // header
        totalHeight += LINE_HEIGHT * 4; // farming level, progress, xp rate, ETA
        totalHeight += SECTION_GAP;

        // Jacob's Contest section — only if active
        if (jacobActive) {
            totalHeight += LINE_HEIGHT; // header
            totalHeight += LINE_HEIGHT * 3; // rank, time, score
        }

        totalHeight += PADDING;

        // Position: right side, below top
        float x = displayW - PANEL_WIDTH - MARGIN_RIGHT;
        float y = MARGIN_TOP;

        // ── Background ──
        draw.addRectFilled(x, y, x + PANEL_WIDTH, y + totalHeight, COL_BG, 6f);
        draw.addRect(x, y, x + PANEL_WIDTH, y + totalHeight, COL_BORDER, 6f, 0, 1f);

        float textX = x + PADDING;
        float rightEdge = x + PANEL_WIDTH - PADDING;
        float curY = y + PADDING;

        ImFont boldFont = ImGuiImpl.getActiveFont("bold");
        ImFont regularFont = ImGuiImpl.getActiveFont("regular");

        // ══════════════════════════════════════
        //  Section 1: Crop Stats
        // ══════════════════════════════════════

        if (boldFont != null) ImGui.pushFont(boldFont);

        String cropName = getCropName(statsLines);
        String runtimeShort = formatDurationShort(elapsed);
        String cropHeader = cropName + " (" + runtimeShort + ")";
        draw.addText(textX, curY, COL_HEADER_CROP, cropHeader);
        curY += LINE_HEIGHT;

        if (boldFont != null) ImGui.popFont();

        if (regularFont != null) ImGui.pushFont(regularFont);
        draw.addText(textX, curY, COL_SUBTEXT, "Farming");
        curY += LINE_HEIGHT;

        // Stats from the module (skip first line which is the Crop mode — already in header)
        for (int i = 1; i < statsLines.length; i++) {
            String line = statsLines[i];
            String[] parts = parseStatLine(line);
            if (parts != null) {
                draw.addText(textX, curY, COL_LABEL, parts[0] + ":");

                int valueColor = getValueColor(parts[0], parts[1]);
                ImVec2 valueSize = new ImVec2();
                ImGui.calcTextSize(valueSize, parts[1]);
                draw.addText(rightEdge - valueSize.x, curY, valueColor, parts[1]);
            }
            curY += LINE_HEIGHT;
        }
        if (regularFont != null) ImGui.popFont();

        curY += SECTION_GAP;

        // ══════════════════════════════════════
        //  Section 2: Skills
        // ══════════════════════════════════════

        draw.addLine(x + 6, curY - SECTION_GAP / 2, x + PANEL_WIDTH - 6, curY - SECTION_GAP / 2,
                imColor(255, 255, 255, 30), 1f);

        if (boldFont != null) ImGui.pushFont(boldFont);
        draw.addText(textX, curY, COL_HEADER_SKILLS, "Skills");
        curY += LINE_HEIGHT;
        if (boldFont != null) ImGui.popFont();

        if (regularFont != null) ImGui.pushFont(regularFont);

        String farmingLevel = tab.getFarmingLevel();
        double farmingProgress = tab.getFarmingLevelProgress();
        if (farmingLevel.isEmpty()) farmingLevel = "?";

        drawLabelValue(draw, textX, curY, rightEdge, "Farming Level", farmingLevel, COL_VALUE_GREEN);
        curY += LINE_HEIGHT;

        drawLabelValue(draw, textX, curY, rightEdge, "Progress", String.format("%.2f%%", farmingProgress), COL_VALUE_GREEN);
        curY += LINE_HEIGHT;

        drawLabelValue(draw, textX, curY, rightEdge, "XP Rate", "\u2014", COL_VALUE_WHITE);
        curY += LINE_HEIGHT;

        drawLabelValue(draw, textX, curY, rightEdge, "ETA", "\u2014", COL_VALUE_WHITE);
        curY += LINE_HEIGHT;

        if (regularFont != null) ImGui.popFont();

        curY += SECTION_GAP;

        // ══════════════════════════════════════
        //  Section 3: Jacob's Contest (only if active)
        // ══════════════════════════════════════

        if (jacobActive) {
            draw.addLine(x + 6, curY - SECTION_GAP / 2, x + PANEL_WIDTH - 6, curY - SECTION_GAP / 2,
                    imColor(255, 255, 255, 30), 1f);

            if (boldFont != null) ImGui.pushFont(boldFont);
            draw.addText(textX, curY, COL_HEADER_JACOB, "Jacob's Contest");
            curY += LINE_HEIGHT;
            if (boldFont != null) ImGui.popFont();

            if (regularFont != null) ImGui.pushFont(regularFont);

            String jacobRank = tab.getJacobRank();
            String jacobTime = tab.getJacobTimeRemaining();
            int jacobScore = tab.getJacobScore();

            if (jacobRank.isEmpty()) jacobRank = "No Rank Yet";
            if (jacobTime.isEmpty()) jacobTime = "\u2014";

            int rankColor = jacobRank.contains("No Rank") ? COL_VALUE_RED : COL_VALUE_GOLD;
            drawLabelValue(draw, textX, curY, rightEdge, "Rank", jacobRank, rankColor);
            curY += LINE_HEIGHT;

            drawLabelValue(draw, textX, curY, rightEdge, "Ends in", jacobTime, COL_VALUE_YELLOW);
            curY += LINE_HEIGHT;

            String scoreStr = jacobScore > 0 ? formatShortNumber(jacobScore) : "0";
            drawLabelValue(draw, textX, curY, rightEdge, "Total Score", scoreStr, COL_VALUE_WHITE);
            curY += LINE_HEIGHT;

            if (regularFont != null) ImGui.popFont();
        }
    }

    // ── Helpers ──

    private boolean isJacobContestActive(TabListParser tab) {
        // Contest is active if we have any contest data from the tab list
        String time = tab.getJacobTimeRemaining();
        String rank = tab.getJacobRank();
        int score = tab.getJacobScore();
        String crop = tab.getJacobCrop();

        // At least time or crop must be present for the contest to be active
        return (!time.isEmpty() || !crop.isEmpty() || score > 0);
    }

    private void drawLabelValue(ImDrawList draw, float x, float y, float rightEdge,
                                String label, String value, int valueColor) {
        draw.addText(x, y, COL_LABEL, label + ":");
        ImVec2 valueSize = new ImVec2();
        ImGui.calcTextSize(valueSize, value);
        draw.addText(rightEdge - valueSize.x, y, valueColor, value);
    }

    private int getValueColor(String label, String value) {
        String lower = label.toLowerCase();
        if (lower.contains("bps") || lower.contains("average")) return COL_VALUE_CYAN;
        if (lower.contains("pest")) {
            try {
                int pests = Integer.parseInt(value);
                if (pests == 0) return COL_VALUE_GREEN;
                if (pests >= 4) return COL_VALUE_RED;
                return COL_VALUE_YELLOW;
            } catch (NumberFormatException e) {
                return COL_VALUE_WHITE;
            }
        }
        if (lower.contains("profit")) return COL_VALUE_GOLD;
        return COL_VALUE_WHITE;
    }

    private String getCropName(String[] statsLines) {
        if (statsLines.length > 0) {
            String[] parts = parseStatLine(statsLines[0]);
            if (parts != null) {
                String mode = parts[1];
                if (mode.contains("Melon")) return "Melon";
                if (mode.contains("Pumpkin")) return "Pumpkin";
                return mode;
            }
        }
        return "Melon";
    }

    private String[] parseStatLine(String line) {
        int separatorStart = -1;
        for (int i = 0; i < line.length() - 1; i++) {
            if (line.charAt(i) == ' ' && line.charAt(i + 1) == ' ') {
                separatorStart = i;
                break;
            }
        }
        if (separatorStart < 0) return null;

        String label = line.substring(0, separatorStart).trim();
        String value = line.substring(separatorStart).trim();
        if (label.isEmpty() || value.isEmpty()) return null;

        return new String[]{label, value};
    }

    private static String formatDurationShort(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + (seconds % 60) + "s";
        long hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private static String formatShortNumber(int num) {
        if (num >= 1_000_000) return String.format("%.1fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%.1fk", num / 1_000.0);
        return String.valueOf(num);
    }

    private static int imColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
