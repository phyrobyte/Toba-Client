package dev.toba.client.mixin;

import com.mojang.brigadier.suggestion.Suggestions;
import dev.toba.client.api.command.CommandManager;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {

    @Shadow @Final TextFieldWidget textField;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    public abstract void show(boolean narrateFirstSuggestion);

    @Shadow private ChatInputSuggestor.SuggestionWindow window;

    @Shadow private boolean completingSuggestions;

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void toba$onRefresh(CallbackInfo ci) {
        if (this.completingSuggestions) {
            ci.cancel();
            return;
        }

        String text = this.textField.getText();
        CommandManager cm = CommandManager.getInstance();
        String prefix = cm.getPrefix();

        if (text.startsWith(prefix)) {
            if (this.pendingSuggestions != null && !this.pendingSuggestions.isDone()) {
                this.pendingSuggestions.cancel(false);
            }

            this.textField.setSuggestion("");
            this.window = null;

            this.pendingSuggestions = cm.getSuggestions(text);
            this.pendingSuggestions.thenAccept(s -> {
                if (s != null && !s.getList().isEmpty()) {
                    this.show(false);
                }
            });

            ci.cancel();
        }
    }
}