package dev.toba.client.mixin;

import dev.toba.client.screens.gui.inventory.InventoryHUDRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects at the end of InGameHud.render() to draw the inventory HUD.
 * <p>
 * InventoryHUDRenderer performs a mini ImGui pass for the background,
 * then renders items and player model via DrawContext while the HUD
 * render state is still active.
 */
@Mixin(InGameHud.class)
public class InventoryHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void toba$renderInventoryHUD(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        InventoryHUDRenderer.getInstance().render(context, tickCounter);
    }
}
