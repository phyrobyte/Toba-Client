package dev.toba.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import dev.toba.client.screens.gui.inventory.InventoryHUDRenderer;
import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.settings.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class FreelookHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void toba$hideHudDuringFreelook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook != null && freelook.isActive()) {
            InventoryHUDRenderer.getInstance().render(context, tickCounter); // Keep our custom InventoryHudRenderer alive even when we cancel the vanilla HUD.
            ci.cancel();
        }
    }
}
