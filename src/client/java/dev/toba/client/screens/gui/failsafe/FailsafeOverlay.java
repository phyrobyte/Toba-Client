package dev.toba.client.screens.gui.failsafe;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import imgui.*;
import imgui.flag.*;

/**
 * ImGui-based failsafe warning overlay. Renders a big red pulsing alert
 * in the center of the screen with a countdown and takeover instructions.
 */
public class FailsafeOverlay implements RenderInterface {

    private String failsafeName = "";
    private long triggerTimeMs = 0;
    private int countdownSeconds = 3;
    private boolean visible = false;

    public void show(String failsafeName, int countdownSeconds) {
        this.failsafeName = failsafeName;
        this.countdownSeconds = countdownSeconds;
        this.triggerTimeMs = System.currentTimeMillis();
        this.visible = true;
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!visible) return;

        float displayW = io.getDisplaySizeX();
        float displayH = io.getDisplaySizeY();
        if (displayW <= 0 || displayH <= 0) return;

        long elapsed = System.currentTimeMillis() - triggerTimeMs;
        double remainingExact = countdownSeconds - (elapsed / 1000.0);
        if (remainingExact < 0) remainingExact = 0;

        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 150.0));

        // ── Full-screen red vignette (using foreground draw list to render on top) ──
        ImDrawList fgDraw = ImGui.getForegroundDrawList();
        int vignetteAlpha = (int) (25 + 35 * pulse);
        fgDraw.addRectFilled(0, 0, displayW, displayH, imColor(255, 0, 0, vignetteAlpha));

        // Red bars at top and bottom
        int barAlpha = (int) (150 + 105 * pulse);
        fgDraw.addRectFilled(0, 0, displayW, 5, imColor(255, 0, 0, barAlpha));
        fgDraw.addRectFilled(0, displayH - 5, displayW, displayH, imColor(255, 0, 0, barAlpha));

        // ── Central warning box (drawn manually on foreground draw list) ──
        float boxW = 380;
        float boxH = 140;
        float boxX = (displayW - boxW) / 2;
        float boxY = (displayH - boxH) / 2;

        // Dark red background
        fgDraw.addRectFilled(boxX, boxY, boxX + boxW, boxY + boxH, imColor(40, 0, 0, 230), 6f);

        // Pulsing red border
        int borderAlpha = (int) (180 + 75 * pulse);
        fgDraw.addRect(boxX, boxY, boxX + boxW, boxY + boxH, imColor(255, (int)(30 * pulse), 0, borderAlpha), 6f, 0, 3f);

        // All text centered in the box
        float textY = boxY + 15;

        // "FAILSAFE TRIGGERED"
        ImFont titleFont = ImGuiImpl.getActiveFont("title");
        if (titleFont != null) ImGui.pushFont(titleFont);
        String title = "FAILSAFE TRIGGERED";
        ImVec2 titleSize = new ImVec2();
        ImGui.calcTextSize(titleSize, title);
        int titleR = (int) (200 + 55 * pulse);
        fgDraw.addText(boxX + (boxW - titleSize.x) / 2, textY, imColor(titleR, 50, 50, 255), title);
        if (titleFont != null) ImGui.popFont();

        textY += titleSize.y + 8;

        // Failsafe name
        ImFont boldFont = ImGuiImpl.getActiveFont("bold");
        if (boldFont != null) ImGui.pushFont(boldFont);
        ImVec2 nameSize = new ImVec2();
        ImGui.calcTextSize(nameSize, failsafeName);
        fgDraw.addText(boxX + (boxW - nameSize.x) / 2, textY, imColor(255, 170, 170, 255), failsafeName);
        if (boldFont != null) ImGui.popFont();

        textY += nameSize.y + 12;

        // Countdown with 3 decimal places
        ImFont countFont = ImGuiImpl.getActiveFont("title");
        if (countFont != null) ImGui.pushFont(countFont);
        String countText = remainingExact > 0 ? String.format("%.3f", remainingExact) : "NOW!";
        ImVec2 countSize = new ImVec2();
        ImGui.calcTextSize(countSize, countText);
        int countColor;
        if (remainingExact > 0) {
            countColor = imColor(255, 255, 255, 255);
        } else {
            countColor = pulse > 0.5 ? imColor(255, 50, 50, 255) : imColor(255, 255, 255, 255);
        }
        fgDraw.addText(boxX + (boxW - countSize.x) / 2, textY, countColor, countText);
        if (countFont != null) ImGui.popFont();

        textY += countSize.y + 8;

        // "Press ENTER to take over"
        String instruction = "Press B to take over";
        ImVec2 instrSize = new ImVec2();
        ImGui.calcTextSize(instrSize, instruction);
        int instrG = pulse > 0.5 ? 255 : 200;
        fgDraw.addText(boxX + (boxW - instrSize.x) / 2, textY, imColor(255, instrG, 0, 255), instruction);
    }

    /**
     * Build an ImU32 color from RGBA components (imgui expects ABGR packed).
     */
    private static int imColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
