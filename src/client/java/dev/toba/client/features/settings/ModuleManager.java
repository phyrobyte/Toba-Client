package dev.toba.client.features.settings;

import dev.toba.client.api.script.LuaScriptLoader;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.impl.module.Scripting.ScriptsModule;
import dev.toba.client.features.impl.module.client.*;
import dev.toba.client.features.impl.module.combat.AimAssistModule;
import dev.toba.client.features.impl.module.combat.TriggerBotModule;
import dev.toba.client.features.impl.module.macro.combat.MobKillerModule;
import dev.toba.client.features.impl.module.macro.combat.RevenantSlayerModule;
import dev.toba.client.features.impl.module.macro.foraging.TreeCutterModule;
import dev.toba.client.features.impl.module.macro.mining.CobbleMacro;
import dev.toba.client.features.impl.module.macro.mining.MithrilMacro;
import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.impl.module.render.ESPModule;
import dev.toba.client.features.impl.module.render.FreelookModule;
import dev.toba.client.features.impl.module.render.InventoryHUDModule;

import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    private static ModuleManager instance;
    private final List<Module> modules = new ArrayList<>();
    private PathfinderModule pathfinderModule;

    public static ModuleManager getInstance() {
        if (instance == null) instance = new ModuleManager();
        return instance;
    }

    public void init() {
        register(pathfinderModule = new PathfinderModule());
        register(new TreeCutterModule());

        register(new MithrilMacro());
        register(new CobbleMacro());
        register(new FreelookModule());
        register(new ESPModule());
        register(new InventoryHUDModule());
        register(new ClickGUI());
        register(new Failsafes());
        register(new ArrayListModule());
        register(new Watermark());
        register(new AimAssistModule());
        register(new TriggerBotModule());
        register(new ScriptsModule());
        register(new MobKillerModule());
        register(new RevenantSlayerModule());


        LuaScriptLoader.init();
        startScriptWatcher();
    }


    private void startScriptWatcher() {
        File dir = LuaScriptLoader.SCRIPTS_DIR;
        Thread t = new Thread(() -> {
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();
                dir.toPath().register(watcher, StandardWatchEventKinds.ENTRY_CREATE);

                while (true) {
                    WatchKey key = watcher.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) continue;

                        @SuppressWarnings("unchecked")
                        Path changed = ((WatchEvent<Path>) event).context();
                        if (!changed.toString().endsWith(".lua")) continue;

                        File f = new File(dir, changed.toString());
                        Thread.sleep(200);

                        try {
                            dev.toba.client.api.script.LuaScriptModule mod =
                                    dev.toba.client.api.script.LuaScriptModule.load(f);
                            if (mod != null) {
                                // Register on the main thread to avoid concurrent modification
                                final var loaded = mod;
                                MinecraftClient.getInstance().execute(() -> {
                                    register(loaded);
                                    TobaChat.info("[Scripts] auto loaded: " + loaded.getName());
                                });
                            }
                        } catch (Exception e) {
                            System.err.println("[Toba Scripts] failed to load " + f.getName() + ": " + e.getMessage());
                            TobaChat.error("[Scripts] failed to load " + f.getName() + ": " + e.getMessage());
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                TobaChat.error("script watcher failed, reason is: " + e.getMessage());
            }
        }, "123123");
        t.setDaemon(true);
        t.start();
    }

    public PathfinderModule getPathfinderModule() { return pathfinderModule; }

    public void register(Module module) { modules.add(module); }

    public List<Module> getModules() { return modules; }

    public List<Module> getModulesByCategory(Module.Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category && !m.isHidden())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module m : modules) {
            if (clazz.isInstance(m)) return (T) m;
        }
        return null;
    }
}