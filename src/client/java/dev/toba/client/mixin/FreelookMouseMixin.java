package dev.toba.client.mixin;

import net.minecraft.client.Mouse;
import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.settings.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class FreelookMouseMixin {

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void toba$onUpdateMouse(CallbackInfo ci) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook == null || !freelook.isActive()) return;

        // Get the raw cursor delta from the Mouse fields
        Mouse mouse = (Mouse) (Object) this;
        double deltaX = ((MouseAccessor) mouse).getCursorDeltaX();
        double deltaY = ((MouseAccessor) mouse).getCursorDeltaY();

        if (freelook.onMouseMove(deltaX, deltaY)) {
            // Zero out the deltas so the player doesn't rotate, then cancel
            ((MouseAccessor) mouse).setCursorDeltaX(0);
            ((MouseAccessor) mouse).setCursorDeltaY(0);
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void toba$onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook == null || !freelook.isActive()) return;

        if (freelook.onScroll(vertical)) {
            ci.cancel();
        }
    }
}
