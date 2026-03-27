package dev.toba.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.toba.client.api.command.CommandManager;
import dev.toba.client.api.command.commands.Bind;
import dev.toba.client.api.command.commands.Help;
import dev.toba.client.api.command.commands.Say;
import dev.toba.client.api.command.commands.Toggle;
import dev.toba.client.api.config.TobaConfig;
import dev.toba.client.api.failsafes.FailsafeManager;
import dev.toba.client.api.pathfinding.PathfindingService;
import dev.toba.client.api.proxy.ProxyManager;
import dev.toba.client.api.proxy.ProxyUiHooks;
import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.render.PathRenderer;
import dev.toba.client.api.rotation.RotationManager;
import dev.toba.client.api.utils.AutoUpdater;
import dev.toba.client.api.utils.BazaarPricingService;
import dev.toba.client.api.utils.KeybindUtil;
import dev.toba.client.api.utils.TabListParser;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.math.BlockPos;

public class TobaClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ProxyManager.getInstance().load();
        AutoUpdater.cleanupOldJars();

        ModuleManager.getInstance().init();
        TobaConfig.load();
        ProxyUiHooks.register();

        registerCommands();
        registerEvents();
    }

    private void registerCommands() {
        CommandManager cm = CommandManager.getInstance();
        cm.register(new Toggle());
        cm.register(new Say());
        cm.register(new Bind());
        cm.register(new Help());

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(cm.getPrefix())) {
                cm.handle(message);
                return false;
            }
            return true;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            PathfinderModule pathfinderModule = ModuleManager.getInstance().getPathfinderModule();
            dispatcher.register(ClientCommandManager.literal("goto")
                    .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                            .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                            .executes(ctx -> {
                                                pathfinderModule.startPathTo(new BlockPos(
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z")
                                                ));
                                                return 1;
                                            })))));
        });
    }

    private void registerEvents() {
        WorldRenderEvents.END_MAIN.register(ctx -> {
            ESPRenderer.getInstance().render(ctx);
            PathRenderer.getInstance().render(ctx);
        });

        WorldRenderEvents.START_MAIN.register(ctx -> {
            RotationManager.getInstance().onRender();
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            KeybindUtil.handleModuleKeys(client);
            PathfindingService.getInstance().tick();

            for (Module module : ModuleManager.getInstance().getModules()) {
                if (module.isEnabled()) {
                    try {
                        module.onTick();
                    } catch (Exception e) {
                        System.err.println("[Toba] Module '" + module.getName()
                                + "' threw in onTick(): " + e.getMessage());
                        e.printStackTrace();
                        try { module.setEnabled(false); } catch (Exception ex) {
                            System.err.println("[Toba] " + module.getName()
                                    + " also threw in onDisable()");
                        }
                        TobaChat.error("Module '" + module.getName()
                                + "' crashed and was disabled.");
                    }
                }
            }

            RotationManager.getInstance().onTick();
            FailsafeManager.getInstance().tick();
            TabListParser.getInstance().refresh();
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            BazaarPricingService.getInstance().start();
            AutoUpdater.checkAsync();
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            drawContext.drawTextWithShadow(textRenderer, "Toba Client", 5, 5, 0x00FF00);
        });
    }
}
