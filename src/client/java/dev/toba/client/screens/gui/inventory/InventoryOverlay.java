package dev.toba.client.screens.gui.inventory;

import dev.toba.client.api.imgui.RenderInterface;
import dev.toba.client.features.impl.module.render.InventoryHUDModule;
import imgui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

/**
 * ImGui HUD overlay that draws the panel border, separator, and syncs layout
 * to {@link InventoryHUDRenderer} which handles the filled background, item
 * icons, and 3D player model via DrawContext.
 */
public class InventoryOverlay implements RenderInterface {

    private final InventoryHUDModule module;

    // Layout constants
    static final float MARGIN = 8;
    static final float PADDING = 8;
    static final float SLOT_SIZE = 26;
    static final float SLOT_GAP = 2;
    static final float HEADER_HEIGHT = 8;
    static final float HOTBAR_GAP = 6;
    static final float PREVIEW_WIDTH = 100;
    // Colors (ABGR)
    private static final int COL_SEPARATOR = imColor(255, 255, 255, 30);
    private static final int COL_TEXT_SHADOW = imColor(0, 0, 0, 200);
    private static final int COL_TEXT = imColor(255, 255, 255, 255);

    public InventoryOverlay(InventoryHUDModule module) {
        this.module = module;
    }

    @Override
    public void render(ImGuiIO io) {
        if (!module.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        float scale = module.getScale();
        float opacity = module.getOpacity();
        boolean showPreview = module.isShowPlayerPreview();

        // Scaled dimensions
        float sSlot = SLOT_SIZE * scale;
        float sGap = SLOT_GAP * scale;
        float sPad = PADDING * scale;
        float sHeader = HEADER_HEIGHT * scale;
        float sHotbarGap = HOTBAR_GAP * scale;
        float sPreviewW = PREVIEW_WIDTH * scale;

        // Grid: 9 columns x 3 main rows + 1 hotbar row
        float gridW = 9 * (sSlot + sGap) - sGap;
        float gridH = 3 * (sSlot + sGap) - sGap;
        float hotbarH = sSlot;

        // Panel size
        float invSectionW = gridW + sPad * 2;
        float previewSectionW = showPreview ? sPreviewW + sPad : 0;
        float panelW = invSectionW + previewSectionW;
        float panelH = sHeader + sPad * 0.5f + gridH + sHotbarGap + hotbarH + sPad;

        float panelX = MARGIN * scale;
        float panelY = MARGIN * scale;

        ImDrawList draw = ImGui.getForegroundDrawList();

        float rounding = module.getCornerRounding();
        int borderArgb = module.getBorderColor();
        int borderAbgr = argbToAbgr(borderArgb);
        int borderCol = applyOpacity(borderAbgr, opacity);

        // ── Panel background fill (drawn on background draw list so it renders behind items) ──
        int bgArgb = module.getBackgroundColor();
        int bgAbgr = argbToAbgr(bgArgb);
        int bgAlpha = MathHelper.clamp((int) (module.getBackgroundAlpha() * opacity), 0, 255);
        int bgColor = (bgAbgr & 0x00FFFFFF) | (bgAlpha << 24);
        ImGui.getBackgroundDrawList().addRectFilled(panelX, panelY, panelX + panelW, panelY + panelH, bgColor, rounding);

        // ── Panel border ──
        draw.addRect(panelX, panelY, panelX + panelW, panelY + panelH, borderCol, rounding, 0, 1f);

        float gridX = panelX + sPad;
        float gridY = panelY + sHeader;

        // ── Separator between inventory and hotbar ──
        float sepY = gridY + gridH + sHotbarGap * 0.5f;
        draw.addLine(gridX, sepY, gridX + gridW, sepY, applyOpacity(COL_SEPARATOR, opacity), 1f);

        // ── Sync settings to the DrawContext-based renderer ──
        // Pass the full panelW so the background fill matches the border exactly.
        InventoryHUDRenderer renderer = InventoryHUDRenderer.getInstance();
        renderer.setEnabled(true);
        renderer.setScale(scale);
        renderer.setOpacity(opacity);
        renderer.setShowPlayerPreview(showPreview);
        renderer.setEntityModelSize(module.getEntityModelSize());
        renderer.setSlotPositions(gridX, gridY, sSlot, sGap, sHotbarGap);
        renderer.setPanelLayout(panelX, panelY, panelW, panelH, sHeader, sPreviewW);

        // ── Stack count text (drawn here in the main ImGui pass, on top of items) ──
        java.util.List<InventoryHUDRenderer.StackOverlayEntry> overlays = renderer.getStackOverlays();
        if (!overlays.isEmpty()) {
            ImVec2 textSize = new ImVec2();
            for (InventoryHUDRenderer.StackOverlayEntry entry : overlays) {
                ImGui.calcTextSize(textSize, entry.text);
                float tx = entry.rightPixelX - textSize.x - 1;
                float ty = entry.bottomPixelY - textSize.y - 1;
                draw.addText(tx + 1, ty + 1, COL_TEXT_SHADOW, entry.text);
                draw.addText(tx, ty, COL_TEXT, entry.text);
            }
        }
    }

    public void dispose() {
        InventoryHUDRenderer.getInstance().setEnabled(false);
    }

    private static int imColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int argbToAbgr(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int applyOpacity(int abgrColor, float opacity) {
        int a = (abgrColor >> 24) & 0xFF;
        a = (int) (a * opacity);
        return (abgrColor & 0x00FFFFFF) | (a << 24);
    }
}
