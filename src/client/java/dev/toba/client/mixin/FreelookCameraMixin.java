package dev.toba.client.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.settings.ModuleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class FreelookCameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void moveBy(float x, float y, float z);

    @Shadow
    protected abstract float clipToSpace(float f);

    @Inject(method = "update", at = @At("RETURN"))
    private void toba$onCameraUpdate(World area, Entity focusedEntity, boolean thirdPerson,
                                     boolean inverseView, float tickDelta, CallbackInfo ci) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook == null || !freelook.isActive()) return;

        // Override camera rotation to freelook angles
        setRotation(freelook.getCameraYaw(), freelook.getCameraPitch());

        // Pull camera back to the configured distance, skip wall clipping
        float dist = freelook.getCameraDistance();
        moveBy(-dist, 0.0f, 0.0f);
    }

    // Make the game think we're in third person so the player model renders
    // and the hand overlay is hidden
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void toba$isThirdPerson(CallbackInfoReturnable<Boolean> cir) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook != null && freelook.isActive()) {
            cir.setReturnValue(true);
        }
    }
}
