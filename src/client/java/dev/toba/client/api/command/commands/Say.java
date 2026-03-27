package dev.toba.client.api.command.commands;

import dev.toba.client.api.command.Command;
import dev.toba.client.api.utils.TobaChat;
import net.minecraft.client.MinecraftClient;

public class Say extends Command {

    public Say() {
        super("say", "Sends a message in chat", "s");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            TobaChat.error("Syntax: " + getSyntax());
            return;
        }

        StringBuilder message = new StringBuilder();
        for (String arg : args) {
            message.append(arg).append(" ");
        }

        var client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.networkHandler.sendChatMessage(message.toString().trim());
        }
    }

    @Override
    public String getSyntax() {
        return ".say <text>";
    }
}