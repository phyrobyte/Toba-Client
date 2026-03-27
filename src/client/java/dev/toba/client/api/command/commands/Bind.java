package dev.toba.client.api.command.commands;

import dev.toba.client.api.command.Command;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.api.utils.KeyUtil;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class Bind extends Command {

    public Bind() {
        super("bind", "Binds a module to a key", "b");
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(String[] args, SuggestionsBuilder builder) {
        String input = String.join(" ", args).toLowerCase();

        for (Module module : ModuleManager.getInstance().getModules()) {
            if (module.getName().toLowerCase().startsWith(input)) {
                String currentKey = module.getKeyBind() == -1 ? "None" : KeyUtil.getKeyName(module.getKeyBind());
                builder.suggest(module.getName(), Text.literal("Current bind: §7" + currentKey));
            }
        }

        boolean matchesModule = ModuleManager.getInstance().getModules().stream()
                .anyMatch(m -> m.getName().equalsIgnoreCase(input));
        if (matchesModule) {
            builder.suggest("NONE", Text.literal("Unbind the module"));
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

        int keyStartIndex = match.consumedArgs();

        if (keyStartIndex >= args.length) {
            TobaChat.error("Please specify a key. Syntax: " + getSyntax());
            return;
        }

        String keyName = String.join(" ", Arrays.copyOfRange(args, keyStartIndex, args.length)).toUpperCase();

        int keyCode = -1;
        if (!keyName.equalsIgnoreCase("NONE")) {
            for (int i = 32; i <= 348; i++) {
                String name = KeyUtil.getKeyName(i);
                if (name != null && name.equalsIgnoreCase(keyName)) {
                    keyCode = i;
                    break;
                }
            }

            if (keyCode == -1) {
                TobaChat.error("Invalid key: " + keyName);
                return;
            }
        }

        module.setKeyBind(keyCode);

        TobaChat.send(Text.literal("Bound ").formatted(Formatting.GRAY)
                .append(Text.literal(module.getName()).formatted(Formatting.AQUA))
                .append(Text.literal(" to ").formatted(Formatting.GRAY))
                .append(Text.literal(keyCode == -1 ? "NONE" : KeyUtil.getKeyName(keyCode)).formatted(Formatting.LIGHT_PURPLE)));
    }

    @Override
    public String getSyntax() {
        return ".bind <module> <key>";
    }
}