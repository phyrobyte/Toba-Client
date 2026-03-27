/**
 * Shared animated node-based panorama renderer.
 * Used by TitleScreenMixin and ScreenBackgroundMixin to draw
 * a consistent background across all menu screens.
 *
 * @author Fogma
 */
package dev.toba.client.api.render;

import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PanoramaRenderer {

    private static final List<float[]> nodes = new ArrayList<>();
    private static final int NODE_COUNT = 100;
    private static final float CONNECT_DISTANCE = 100f;
    private static final Random random = new Random();
    private static boolean initialized = false;

    /**
     * Draws the animated node panorama onto the ImGui background draw list.
     * Must be called between ImGuiImpl.beginFrame() and ImGuiImpl.endImGuiRendering().
     */
    public static void render() {
        Window window = MinecraftClient.getInstance().getWindow();
        int fbWidth  = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();

        ImDrawList drawList = ImGui.getBackgroundDrawList();

        // Black background
        drawList.addRectFilled(0, 0, fbWidth, fbHeight, 0xFF000000);

        // Initialize nodes on first call
        if (!initialized) {
            nodes.clear();
            for (int i = 0; i < NODE_COUNT; i++) {
                nodes.add(new float[]{
                        random.nextFloat() * fbWidth,
                        random.nextFloat() * fbHeight,
                        (random.nextFloat() - 0.5f) * 1.5f,
                        (random.nextFloat() - 0.5f) * 1.5f
                });
            }
            initialized = true;
        }

        // Move nodes and draw connecting lines
        for (int i = 0; i < nodes.size(); i++) {
            float[] a = nodes.get(i);
            a[0] += a[2];
            a[1] += a[3];

            // Wrap around screen edges
            if (a[0] < 0) a[0] += fbWidth;
            if (a[0] > fbWidth) a[0] -= fbWidth;
            if (a[1] < 0) a[1] += fbHeight;
            if (a[1] > fbHeight) a[1] -= fbHeight;

            for (int j = i + 1; j < nodes.size(); j++) {
                float[] b = nodes.get(j);
                float dx   = a[0] - b[0];
                float dy   = a[1] - b[1];
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < CONNECT_DISTANCE) {
                    int alpha = (int) ((1f - dist / CONNECT_DISTANCE) * 255);
                    int color = (alpha << 24) | 0xFFFFFF;
                    drawList.addLine(a[0], a[1], b[0], b[1], color, 1.0f);
                }
            }
        }

        // Draw node circles
        for (float[] node : nodes) {
            drawList.addCircleFilled(node[0], node[1], 2f, 0xFFFFFFFF);
        }
    }
}
