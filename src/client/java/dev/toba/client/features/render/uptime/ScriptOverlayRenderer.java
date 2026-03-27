package dev.toba.client.features.render.uptime;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;

/**
 * Renders a stats overlay on the left side of the screen when a script module is running.
 * Matches the green/frog theme of the Toba GUI.
 */
public class ScriptOverlayRenderer {

    private static final int BG_COLOR = 0xCC1B2D1B;
    private static final int BORDER_COLOR = 0xFF3A5A3A;
    private static final int ACCENT = 0xFF4CAF50;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int WIDTH = 150;

    /**
     * Called each frame from HudRenderCallback. Renders overlay for any active script modules.
     */
    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;

        // Find active script modules
        for (Module module : ModuleManager.getInstance().getModules()) {
            if (!module.isScript() || !module.isEnabled()) continue;
            if (!(module instanceof ScriptModule script)) continue;

            renderOverlay(ctx, client, module, script);
        }
    }

    private static void renderOverlay(DrawContext ctx, MinecraftClient client, Module module, ScriptModule script) {
        TextRenderer textRenderer = client.textRenderer;

        // Calculate runtime
        long elapsed = System.currentTimeMillis() - script.getStartTimeMillis();
        String runtime = formatDuration(elapsed);
        String[] statsLines = script.getStatsLines();

        // Calculate height: title + separator + runtime + stats
        int contentLines = 2 + statsLines.length; // runtime + blank + stats
        int totalHeight = PADDING * 2 + LINE_HEIGHT + 4 + (contentLines * LINE_HEIGHT);

        // Position: left side, vertically centered
        int screenHeight = client.getWindow().getScaledHeight();
        int x = 6;
        int y = (screenHeight - totalHeight) / 2;

        // Background
        ctx.fill(x, y, x + WIDTH, y + totalHeight, BG_COLOR);

        // Border
        drawBorder(ctx, x, y, WIDTH, totalHeight, BORDER_COLOR);

        // Left accent bar
        ctx.fill(x, y, x + 3, y + totalHeight, ACCENT);

        int textX = x + PADDING + 2;
        int textY = y + PADDING;

        // Module name
        ctx.drawTextWithShadow(textRenderer, module.getName(), textX, textY, ACCENT);
        textY += LINE_HEIGHT + 2;

        // Separator line
        ctx.fill(x + 6, textY, x + WIDTH - 6, textY + 1, 0x44FFFFFF);
        textY += 4;

        // Runtime
        drawStatLine(ctx, textRenderer, textX, textY, "Runtime", runtime);
        textY += LINE_HEIGHT;

        // Blank spacer
        textY += LINE_HEIGHT / 2;

        // Stats lines
        for (String line : statsLines) {
            ctx.drawTextWithShadow(textRenderer, line, textX, textY, TEXT_SECONDARY);
            textY += LINE_HEIGHT;
        }
    }

    private static void drawStatLine(DrawContext ctx, TextRenderer textRenderer, int x, int y, String label, String value) {
        ctx.drawTextWithShadow(textRenderer, label, x, y, TEXT_SECONDARY);
        int valueWidth = textRenderer.getWidth(value);
        ctx.drawTextWithShadow(textRenderer, value, x + WIDTH - PADDING * 2 - valueWidth - 8, y, TEXT_PRIMARY);
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
