/**
 * @author Fogma
 * @since 2026-02-22
 */
package dev.toba.client.features.impl.module.macro.mining;

import dev.toba.client.features.settings.Module;
import net.minecraft.client.MinecraftClient;

public class CobbleMacro extends Module {

    public CobbleMacro() {
        super("CobblestoneMacro", "automates using cobblestone builds | good for early-game", Category.MACRO);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) return;
        mc.options.forwardKey.setPressed(true);
        mc.options.attackKey.setPressed(true);
    }
}