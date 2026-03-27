package dev.toba.client.api.command.commands;

import dev.toba.client.api.command.Command;
import dev.toba.client.api.command.CommandManager;
import dev.toba.client.api.utils.TobaChat;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Help extends Command {

    public Help() {
        super("help", "Displays a list of commands", "h", "?");
    }

    @Override
    public void execute(String[] args) {
        TobaChat.send(Text.literal("--- ").formatted(Formatting.GRAY)
                .append(Text.literal("Commands").formatted(Formatting.GREEN))
                .append(Text.literal(" ---").formatted(Formatting.GRAY)));

        String prefix = CommandManager.getInstance().getPrefix();

        for (Command command : CommandManager.getInstance().getCommands()) {
            String fullCommand = prefix + command.getName();

            MutableText commandNode = Text.literal(fullCommand).formatted(Formatting.GREEN);

            commandNode.styled(style -> style
                    .withClickEvent(new ClickEvent.SuggestCommand(fullCommand + " "))
                    .withHoverEvent(new HoverEvent.ShowText(
                            Text.literal("Click to autofill ").formatted(Formatting.GRAY)
                                    .append(Text.literal(fullCommand).formatted(Formatting.GREEN))
                    ))
            );

            TobaChat.send(commandNode
                    .append(Text.literal(" - ").formatted(Formatting.GRAY))
                    .append(Text.literal(command.getDescription()).formatted(Formatting.WHITE)));
        }
    }

    @Override
    public String getSyntax() {
        return ".help";
    }
}