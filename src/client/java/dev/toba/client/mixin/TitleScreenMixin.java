/**
 * @author Fogma
 * @since 2026-02-24
 * No one fucking dare touch this code except Fogma
 */

package dev.toba.client.mixin;

import dev.toba.client.ProjectInfo;
import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.render.PanoramaRenderer;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void toba$renderImGui(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {

        ImGuiImpl.beginFrame();

        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();

        float scaleX = ImGui.getIO().getDisplayFramebufferScaleX();
        float scaleY = ImGui.getIO().getDisplayFramebufferScaleY();

        ImGuiIO io = ImGui.getIO();
        io.setMousePos(mouseX * scaleX, mouseY * scaleY);

        // Shared panorama renderer
        PanoramaRenderer.render();

        // Title screen specific overlays
        ImDrawList drawList = ImGui.getBackgroundDrawList();
        drawList.addText(5, 5, 0xFFFFFFFF, "Toba by: " + ProjectInfo.author_list + ".\nPanorama by: Fogma/Fogmalol.");
        drawList.addText(5, 35, 0xFFaceabc, "updates for " + ProjectInfo.version + ":\n" + ProjectInfo.update_list);

        ImGuiImpl.endImGuiRendering();
    }
}
