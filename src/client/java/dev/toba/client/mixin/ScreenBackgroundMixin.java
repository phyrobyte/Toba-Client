package dev.toba.client.mixin;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.render.PanoramaRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

/**
 * Renders the Toba panorama as the background for all menu screens
 * (server selector, world selector, options, etc.).
 *
 * TitleScreen is excluded because TitleScreenMixin handles it
 * with additional overlay text.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {

    @Shadow @Nullable
    protected MinecraftClient client;

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void toba$renderCustomBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // TitleScreen is handled by TitleScreenMixin — skip to avoid double rendering
        if ((Object) this instanceof TitleScreen) return;

        // Only replace the background when no world is loaded (menu screens).
        // In-game screens (pause menu, inventory) keep their translucent overlay.
        if (client != null && client.world != null) return;

        ImGuiImpl.beginFrame();
        PanoramaRenderer.render();
        ImGuiImpl.endImGuiRendering();

        ci.cancel(); // prevent vanilla dirt/panorama background
    }
}
