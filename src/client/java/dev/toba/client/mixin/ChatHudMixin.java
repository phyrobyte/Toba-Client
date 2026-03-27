package dev.toba.client.mixin;

import dev.toba.client.api.script.LuaAPI;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatHudMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void toba$onAddMessage(Text message, CallbackInfo ci) {
        try {
            LuaAPI.onChatMessage(message.getString());
        } catch (Exception ignored) {}
    }
}
