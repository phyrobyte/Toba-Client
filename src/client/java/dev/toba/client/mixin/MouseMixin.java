/**
 * @author Fogma
 * @since 2026-02-22
 */
package dev.toba.client.mixin;

import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    private static boolean shouldBlock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen != null) return false;
        for (Module m : ModuleManager.getInstance().getModules()) {
            if (m.getCategory() == Module.Category.MACRO && m.isEnabled()) return true;
        }
        return false;
    }

    private static boolean isFreelookActive() {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        return freelook != null && freelook.isActive();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void blockMouseButtons(long window, MouseInput mouseInput, int action, CallbackInfo ci) {
        if (shouldBlock()) ci.cancel();
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void blockCameraRotation(CallbackInfo ci) {
        if (shouldBlock() && !isFreelookActive()) ci.cancel();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void blockCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (shouldBlock() && !isFreelookActive()) ci.cancel();
    }
}