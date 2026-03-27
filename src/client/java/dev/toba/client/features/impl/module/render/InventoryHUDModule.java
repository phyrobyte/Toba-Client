package dev.toba.client.features.impl.module.render;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.features.settings.Module;
import dev.toba.client.screens.gui.inventory.InventoryOverlay;

/**
 * Toggleable module that displays an always-on inventory HUD overlay
 * with a miniature player preview in the top-left corner.
 */
public class InventoryHUDModule extends Module {

    private final Setting<Float> scale;
    private final Setting<Float> opacity;
    private final Setting<Boolean> showPlayerPreview;
    private final Setting<Integer> borderColor;
    private final Setting<Integer> backgroundColor;
    private final Setting<Float> cornerRounding;
    private final Setting<Integer> backgroundAlpha;
    private final Setting<Integer> entityModelSize;

    private InventoryOverlay overlay;

    public InventoryHUDModule() {
        super("Inventory HUD", "Shows inventory and player preview on screen", Category.RENDER);

        scale = addSetting(new Setting<>("Scale", 1.0f, Setting.SettingType.FLOAT));
        scale.range(0.5, 2.0);

        opacity = addSetting(new Setting<>("Opacity", 0.85f, Setting.SettingType.FLOAT));
        opacity.range(0.1, 1.0);

        showPlayerPreview = addSetting(new Setting<>("Player Preview", true, Setting.SettingType.BOOLEAN));

        borderColor = addSetting(new Setting<>("Border Color", 0xB43C3C50, Setting.SettingType.COLOR));
        backgroundColor = addSetting(new Setting<>("Background Color", 0xFF0F0F19, Setting.SettingType.COLOR));
        cornerRounding = addSetting(new Setting<>("Corner Rounding", 15.0f, Setting.SettingType.FLOAT));
        cornerRounding.range(0.0, 30.0);
        backgroundAlpha = addSetting(new Setting<>("Background Alpha", 140, Setting.SettingType.INTEGER));
        backgroundAlpha.range(0, 255);
        entityModelSize = addSetting(new Setting<>("Entity Model Size", 28, Setting.SettingType.INTEGER));
        entityModelSize.range(10, 60);
    }

    @Override
    protected void onEnable() {
        overlay = new InventoryOverlay(this);
        ImGuiImpl.registerHudOverlay(overlay);
    }

    @Override
    protected void onDisable() {
        if (overlay != null) {
            ImGuiImpl.unregisterHudOverlay(overlay);
            overlay.dispose();
            overlay = null;
        }
    }

    public float getScale() { return scale.getValue(); }
    public float getOpacity() { return opacity.getValue(); }
    public boolean isShowPlayerPreview() { return showPlayerPreview.getValue(); }
    public int getBorderColor() { return borderColor.getValue(); }
    public int getBackgroundColor() { return backgroundColor.getValue(); }
    public float getCornerRounding() { return cornerRounding.getValue(); }
    public int getBackgroundAlpha() { return backgroundAlpha.getValue(); }
    public int getEntityModelSize() { return entityModelSize.getValue(); }
}
