/**
 * @author Fogma
 * @since 2026-02-17
 */
package dev.toba.client.mixin;

import dev.toba.client.features.impl.module.client.ClickGUI;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void toba$onGetWindowTitle(CallbackInfoReturnable<String> cir) {
        ClickGUI clickGUI = ModuleManager.getInstance().getModule(ClickGUI.class);
        if (clickGUI != null && clickGUI.allowCustomTitle.getValue()) {
            String title = clickGUI.customTitleText.getValue();
            if (title != null && !title.isEmpty()) {
                cir.setReturnValue(title);
            }
        }
    }
}