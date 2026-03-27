package dev.toba.client.mixin;

import dev.toba.client.api.utils.RotationUtil;
import dev.toba.client.features.impl.module.macro.combat.MobKillerModule;
import dev.toba.client.features.impl.module.macro.combat.RevenantSlayerModule;
import dev.toba.client.features.impl.module.render.ESPModule;
import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void toba$onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        float tickDelta = tickCounter.getTickProgress(false);

        RotationUtil.onFrame(tickDelta);

        ESPModule espModule = ModuleManager.getInstance().getModule(ESPModule.class);
        if (espModule != null) {
            espModule.onFrameUpdate(tickDelta);
        }

        MobKillerModule mobKiller = ModuleManager.getInstance().getModule(MobKillerModule.class);
        if (mobKiller != null) {
            mobKiller.onFrameUpdate(tickDelta);
        }

        RevenantSlayerModule revenantSlayer = ModuleManager.getInstance().getModule(RevenantSlayerModule.class);
        if (revenantSlayer != null) {
            revenantSlayer.onFrameUpdate(tickDelta);
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void toba$hideHandDuringFreelook(CallbackInfo ci) {
        FreelookModule freelook = ModuleManager.getInstance().getModule(FreelookModule.class);
        if (freelook != null && freelook.isActive()) {
            ci.cancel();
        }
    }
}