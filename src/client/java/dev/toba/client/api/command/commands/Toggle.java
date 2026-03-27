package dev.toba.client.api.command.commands;

import dev.toba.client.api.command.Command;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.concurrent.CompletableFuture;

public class Toggle extends Command {

    public Toggle() {
        super("toggle", "Toggles a module on or off", "t");
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(String[] args, SuggestionsBuilder builder) {
        String input = String.join(" ", args).toLowerCase();

        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.getName().toLowerCase().startsWith(input)) {
                String status = module.isEnabled() ? "§aEnabled" : "§cDisabled";
                builder.suggest(module.getName(), Text.literal(module.getDescription() + " (" + status + "§r)"));
            }
        }
        return builder.buildFuture();
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            TobaChat.error("Syntax: " + getSyntax());
            return;
        }

        ModuleMatch match = findModule(args);

        if (match == null) {
            TobaChat.error("Module not found: " + String.join(" ", args));
            return;
        }

        Module module = match.module();
        module.toggle();

        TobaChat.send(Text.literal(module.getName()).formatted(Formatting.AQUA)
                .append(Text.literal(" has been ").formatted(Formatting.GRAY))
                .append(Text.literal(module.isEnabled() ? "ENABLED" : "DISABLED")
                        .formatted(module.isEnabled() ? Formatting.GREEN : Formatting.RED)));
    }

    @Override
    public String getSyntax() {
        return ".toggle <module>";
    }
}