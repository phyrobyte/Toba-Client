package dev.toba.client.screens.gui.inventory;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

public class InventoryHUDRenderer {

    private static InventoryHUDRenderer instance;

    private boolean enabled = false;
    private float scale = 1.0f;
    private float opacity = 0.85f;
    private boolean showPlayerPreview = true;

    private float gridX, gridY, slotSize, slotGap, hotbarGap;
    private float panelX, panelY, panelW, panelH, previewW;

    private static final int ITEM_ICON_SIZE = 16;

    private int entityModelSize = 28;

    private final List<StackOverlayEntry> stackOverlays = new ArrayList<>();

    public static InventoryHUDRenderer getInstance() {
        if (instance == null) instance = new InventoryHUDRenderer();
        return instance;
    }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setScale(float scale) { this.scale = scale; }
    public void setOpacity(float opacity) { this.opacity = opacity; }
    public void setShowPlayerPreview(boolean show) { this.showPlayerPreview = show; }
    public void setEntityModelSize(int size) { this.entityModelSize = size; }
    public void setSlotPositions(float gridX, float gridY, float slotSize, float slotGap, float hotbarGap) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.slotSize = slotSize;
        this.slotGap = slotGap;
        this.hotbarGap = hotbarGap;
    }

    public void setPanelLayout(float panelX, float panelY, float panelW, float panelH,
                               float headerHeight, float previewW) {
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.previewW = previewW;
    }

    public List<StackOverlayEntry> getStackOverlays() {
        return stackOverlays;
    }

    public void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!enabled || slotSize <= 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        float guiScale = (float) client.getWindow().getScaleFactor();
        if (guiScale <= 0) guiScale = 1;

        // Items
        float gX = gridX / guiScale;
        float gY = gridY / guiScale;
        float sSlot = slotSize / guiScale;
        float sGap = slotGap / guiScale;
        float sHotbarGap = hotbarGap / guiScale;
        float gridH = 3 * (sSlot + sGap) - sGap;

        stackOverlays.clear();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                ItemStack stack = player.getInventory().getStack(slot);

                if (!stack.isEmpty()) {
                    float slotX = gX + col * (sSlot + sGap);
                    float slotY = gY + row * (sSlot + sGap);

                    int iconX = Math.round(slotX + (sSlot - ITEM_ICON_SIZE) * 0.5f);
                    int iconY = Math.round(slotY + (sSlot - ITEM_ICON_SIZE) * 0.5f);

                    context.drawItem(stack, iconX, iconY);

                    if (stack.getCount() > 1) {
                        stackOverlays.add(new StackOverlayEntry(
                                (iconX + ITEM_ICON_SIZE) * guiScale,
                                (iconY + ITEM_ICON_SIZE) * guiScale,
                                String.valueOf(stack.getCount())
                        ));
                    }
                }
            }
        }

        float hotbarY = gY + gridH + sHotbarGap;

        for (int col = 0; col < 9; col++) {
            ItemStack stack = player.getInventory().getStack(col);

            if (!stack.isEmpty()) {
                float slotX = gX + col * (sSlot + sGap);

                int iconX = Math.round(slotX + (sSlot - ITEM_ICON_SIZE) * 0.5f);
                int iconY = Math.round(hotbarY + (sSlot - ITEM_ICON_SIZE) * 0.5f);

                context.drawItem(stack, iconX, iconY);

                if (stack.getCount() > 1) {
                    stackOverlays.add(new StackOverlayEntry(
                            (iconX + ITEM_ICON_SIZE) * guiScale,
                            (iconY + ITEM_ICON_SIZE) * guiScale,
                            String.valueOf(stack.getCount())
                    ));
                }
            }
        }

        // ─────────────────────────────────────────────
        // 3️⃣ PLAYER MODEL
        // ─────────────────────────────────────────────
        if (showPlayerPreview && previewW > 0) {

            float sPanelX = panelX / guiScale;
            float sPanelY = panelY / guiScale;
            float sPanelW = panelW / guiScale;
            float sPanelH = panelH / guiScale;
            float sPreviewW = previewW / guiScale;

            float previewX = sPanelX + sPanelW - sPreviewW;

            int px1 = Math.round(previewX);
            int py1 = Math.round(sPanelY);
            int px2 = Math.round(sPanelX + sPanelW);
            int py2 = Math.round(sPanelY + sPanelH);

            float mouseX = px1 + sPreviewW * 0.5f;
            float mouseY = py1 + sPanelH * 0.3f;

            int entitySize = Math.round(entityModelSize * scale);

            InventoryScreen.drawEntity(
                    context,
                    px1, py1, px2, py2,
                    entitySize,
                    0.0625F,
                    mouseX, mouseY,
                    player
            );
        }
    }

    static class StackOverlayEntry {
        final float rightPixelX;
        final float bottomPixelY;
        final String text;

        StackOverlayEntry(float x, float y, String text) {
            this.rightPixelX = x;
            this.bottomPixelY = y;
            this.text = text;
        }
    }
}
