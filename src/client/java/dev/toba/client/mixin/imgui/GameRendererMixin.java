/*
 * This file is part of fabric-imgui-example-mod - https://github.com/FlorianMichael/fabric-imgui-example-mod
 * by FlorianMichael/EnZaXD and contributors
 * ported by Fogma to 1.21.11 yarn mappings
 */
package dev.toba.client.mixin.imgui;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "render", at = @At("RETURN"))
    private void render(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        // Render screen-based ImGui (click GUI, etc.)
        boolean hasScreen = client.currentScreen instanceof final RenderInterface renderInterface;
        // Render global HUD overlays (failsafe warning, etc.) — always active
        boolean hasOverlays = !ImGuiImpl.getHudOverlays().isEmpty();

        if (hasScreen || hasOverlays) {
            ImGuiImpl.beginFrame();
            if (hasScreen) {
                ((RenderInterface) client.currentScreen).render(ImGui.getIO());
            }
            for (dev.toba.client.api.imgui.RenderInterface overlay : ImGuiImpl.getHudOverlays()) {
                overlay.render(ImGui.getIO());
            }
            ImGuiImpl.endImGuiRendering();
        }
    }

}
