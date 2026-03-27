package dev.toba.client.api.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class Command {
    private final String name;
    private final String description;
    private final String[] aliases;

    public Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = aliases;
    }

    public abstract void execute(String[] args);

    public CompletableFuture<Suggestions> getSuggestions(String[] args, SuggestionsBuilder builder) {
        return builder.buildFuture();
    }

    /**
     * Attempts to find a module from an array of arguments, handling names with spaces ex: "Tree Cutter".
     * Returns an object containing the Module and how many arguments were used to find it.
     */

    protected ModuleMatch findModule(String[] args) {
        List<Module> modules = ModuleManager.getInstance().getModules();

        for (int i = args.length; i >= 1; i--) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < i; j++) {
                sb.append(args[j]).append(j == i - 1 ? "" : " ");
            }
            String potentialName = sb.toString();

            Module found = modules.stream()
                    .filter(m -> m.getName().equalsIgnoreCase(potentialName))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                return new ModuleMatch(found, i);
            }
        }
        return null;
    }

    public record ModuleMatch(Module module, int consumedArgs) {

    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getAliases() {
        return aliases;
    }

    public abstract String getSyntax();
}