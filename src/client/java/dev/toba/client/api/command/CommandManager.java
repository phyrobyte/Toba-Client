package dev.toba.client.api.command;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.toba.client.api.utils.TobaChat;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CommandManager {

    private static final CommandManager instance = new CommandManager();
    private final List<Command> commands = new ArrayList<>();
    private String prefix = ".";

    private CommandManager() {}

    public static CommandManager getInstance() {
        return instance;
    }

    public void register(Command command) {
        commands.add(command);
    }

    public List<Command> getCommands() {
        return commands;
    }

    public CompletableFuture<Suggestions> getSuggestions(String message) {
        if (!message.startsWith(prefix)) return Suggestions.empty();

        String raw = message.substring(prefix.length());
        String[] split = raw.split(" ", -1);

        String label = split[0];
        String[] args = Arrays.copyOfRange(split, 1, split.length);

        if (split.length == 1) {
            SuggestionsBuilder builder = new SuggestionsBuilder(message, prefix.length());
            for (Command command : commands) {
                String name = command.getName();
                if (name.toLowerCase().startsWith(label.toLowerCase())) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        }

        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(label) || Arrays.stream(command.getAliases()).anyMatch(label::equalsIgnoreCase)) {
                int lastSpace = message.lastIndexOf(' ');
                int argsStart = (lastSpace == -1) ? prefix.length() + label.length() + 1 : lastSpace + 1;

                SuggestionsBuilder builder = new SuggestionsBuilder(message, argsStart);
                return command.getSuggestions(args, builder);
            }
        }

        return Suggestions.empty();
    }

    public void handle(String message) {
        if (!message.startsWith(prefix)) return;

        String raw = message.substring(prefix.length());
        if (raw.isEmpty()) return;

        List<String> splitList = new ArrayList<>();
        StringBuilder currentArg = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!currentArg.isEmpty()) {
                    splitList.add(currentArg.toString());
                    currentArg.setLength(0);
                }
            } else {
                currentArg.append(c);
            }
        }
        if (!currentArg.isEmpty()) {
            splitList.add(currentArg.toString());
        }

        if (splitList.isEmpty()) return;

        String label = splitList.get(0);

        String[] args = splitList.subList(1, splitList.size()).toArray(new String[0]);

        for (Command command : commands) {
            if (command.getName().equalsIgnoreCase(label) || Arrays.stream(command.getAliases()).anyMatch(label::equalsIgnoreCase)) {
                try {
                    command.execute(args);
                } catch (Exception e) {
                    TobaChat.sendColored("An error occurred while executing that command!", Formatting.RED);
                    e.printStackTrace();
                }
                return;
            }
        }

        TobaChat.sendColored("Unknown command. Type " + prefix + "help for a list of commands.", Formatting.RED);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}