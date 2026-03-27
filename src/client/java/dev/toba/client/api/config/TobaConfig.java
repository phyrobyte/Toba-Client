package dev.toba.client.api.config;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class TobaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_CONFIG = "default";

    private static Path getConfigDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("config/toba");
    }

    private static Path getConfigPath(String name) {
        return getConfigDir().resolve(name + ".json");
    }

    public static void save() {
        saveAs(DEFAULT_CONFIG);
    }

    public static void saveAs(String configName) {
        try {
            JsonObject root = new JsonObject();
            JsonObject modulesObj = new JsonObject();

            for (Module module : ModuleManager.getInstance().getModules()) {
                JsonObject moduleObj = new JsonObject();
                moduleObj.addProperty("enabled", module.isEnabled());
                moduleObj.addProperty("keyBind", module.getKeyBind());

                JsonObject settingsObj = new JsonObject();
                serializeSettings(settingsObj, module.getSettings());
                moduleObj.add("settings", settingsObj);

                modulesObj.add(module.getName(), moduleObj);
            }

            root.add("modules", modulesObj);

            Path path = getConfigPath(configName);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (Exception e) {
            System.err.println("[Toba] Failed to save config '" + configName + "': " + e.getMessage());
        }
    }

    public static void load() {
        loadFrom(DEFAULT_CONFIG);
    }

    public static void loadFrom(String configName) {
        try {
            Path path = getConfigPath(configName);
            if (!Files.exists(path)) return;

            String json = Files.readString(path);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            JsonObject modulesObj = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();

            for (Module module : ModuleManager.getInstance().getModules()) {
                if (!modulesObj.has(module.getName())) continue;
                JsonObject moduleObj = modulesObj.getAsJsonObject(module.getName());

                if (moduleObj.has("enabled")) {
                    boolean enabled = moduleObj.get("enabled").getAsBoolean();
                    if (enabled != module.isEnabled()) {
                        module.setEnabled(enabled);
                    }
                }

                if (moduleObj.has("keyBind")) {
                    module.setKeyBind(moduleObj.get("keyBind").getAsInt());
                }

                if (moduleObj.has("settings")) {
                    deserializeSettings(moduleObj.getAsJsonObject("settings"), module.getSettings());
                }
            }
        } catch (Exception e) {
            System.err.println("[Toba] Failed to load config '" + configName + "': " + e.getMessage());
        }
    }

    public static List<String> getConfigNames() {
        List<String> configs = new ArrayList<>();
        try {
            Path configDir = getConfigDir();
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                return configs;
            }

            try (Stream<Path> paths = Files.list(configDir)) {
                paths.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            String fileName = path.getFileName().toString();
                            String configName = fileName.substring(0, fileName.length() - 5);
                            if (!configName.equals(DEFAULT_CONFIG)) {
                                configs.add(configName);
                            }
                        });
            }
        } catch (IOException ignored) {}
        return configs;
    }

    public static void deleteConfig(String configName) {
        if (configName.equals(DEFAULT_CONFIG)) return;
        try {
            Path path = getConfigPath(configName);
            if (Files.exists(path)) {
                Files.delete(path);
            }
        } catch (IOException ignored) {}
    }

    private static void serializeSettings(JsonObject obj, List<Module.Setting<?>> settings) {
        for (Module.Setting<?> setting : settings) {
            String key = setting.getName();
            switch (setting.getType()) {
                case BOOLEAN -> obj.addProperty(key, (Boolean) setting.getValue());
                case INTEGER -> obj.addProperty(key, (Integer) setting.getValue());
                case FLOAT   -> obj.addProperty(key, (Float) setting.getValue());
                case COLOR   -> obj.addProperty(key, (Integer) setting.getValue());
                case STRING, MODE -> obj.addProperty(key, (String) setting.getValue());
                case SUB_CONFIG -> {
                    JsonObject subObj = new JsonObject();
                    subObj.addProperty("expanded", setting.isExpanded());
                    if (setting.hasChildren()) {
                        JsonObject childrenObj = new JsonObject();
                        serializeSettings(childrenObj, setting.getChildren());
                        subObj.add("children", childrenObj);
                    }
                    obj.add(key, subObj);
                }
                case REWARP_LIST -> {
                    Module.Setting.RewarpListProvider provider = setting.getRewarpListProvider();
                    if (provider != null) {
                        JsonArray arr = new JsonArray();
                        List<Module.Setting.RewarpEntry> entries = provider.getRewarpEntries();
                        if (entries != null) {
                            for (Module.Setting.RewarpEntry entry : entries) {
                                JsonObject entryObj = new JsonObject();
                                entryObj.addProperty("name", entry.name);
                                entryObj.addProperty("x", entry.x);
                                entryObj.addProperty("y", entry.y);
                                entryObj.addProperty("z", entry.z);
                                arr.add(entryObj);
                            }
                        }
                        obj.add(key, arr);
                    }
                }
                case MULTI_RANGE -> {
                    Module.Setting.RangeValue rv = (Module.Setting.RangeValue) setting.getValue();
                    if (rv != null) {
                        JsonObject rangeObj = new JsonObject();
                        rangeObj.addProperty("min", rv.min);
                        rangeObj.addProperty("max", rv.max);
                        obj.add(key, rangeObj);
                    }
                }
                case BLOCK_LIST -> {
                    @SuppressWarnings("unchecked")
                    List<String> blocks = (List<String>) setting.getValue();
                    if (blocks != null) {
                        JsonArray arr = new JsonArray();
                        for (String block : blocks) arr.add(block);
                        obj.add(key, arr);
                    }
                }
                case BUTTON -> {} // Buttons have no persistent state
            }
            if (setting.getType() != Module.Setting.SettingType.SUB_CONFIG && setting.hasChildren()) {
                JsonObject childrenObj = new JsonObject();
                serializeSettings(childrenObj, setting.getChildren());
                obj.add(key + "__children", childrenObj);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void deserializeSettings(JsonObject obj, List<Module.Setting<?>> settings) {
        for (Module.Setting<?> setting : settings) {
            String key = setting.getName();
            if (!obj.has(key)) continue;

            JsonElement el = obj.get(key);
            try {
                switch (setting.getType()) {
                    case BOOLEAN -> ((Module.Setting<Boolean>) setting).setValue(el.getAsBoolean());
                    case INTEGER -> ((Module.Setting<Integer>) setting).setValue(el.getAsInt());
                    case FLOAT   -> ((Module.Setting<Float>) setting).setValue(el.getAsFloat());
                    case COLOR   -> ((Module.Setting<Integer>) setting).setValue(el.getAsInt());
                    case STRING, MODE -> ((Module.Setting<String>) setting).setValue(el.getAsString());
                    case SUB_CONFIG -> {
                        if (el.isJsonObject()) {
                            JsonObject subObj = el.getAsJsonObject();
                            if (subObj.has("expanded")) {
                                setting.setExpanded(subObj.get("expanded").getAsBoolean());
                            }
                            if (subObj.has("children") && setting.hasChildren()) {
                                deserializeSettings(subObj.getAsJsonObject("children"), setting.getChildren());
                            }
                        }
                    }
                    case REWARP_LIST -> {
                        Module.Setting.RewarpListProvider provider = setting.getRewarpListProvider();
                        if (provider != null && el.isJsonArray()) {
                            // Clear existing entries first
                            List<Module.Setting.RewarpEntry> existing = provider.getRewarpEntries();
                            if (existing != null) {
                                while (!existing.isEmpty()) {
                                    provider.onRemoveRewarp(existing.size() - 1);
                                }
                            }
                            // Load saved entries
                            JsonArray arr = el.getAsJsonArray();
                            for (JsonElement entryEl : arr) {
                                if (entryEl.isJsonObject()) {
                                    JsonObject entryObj = entryEl.getAsJsonObject();
                                    String name = entryObj.has("name") ? entryObj.get("name").getAsString() : "Rewarp";
                                    int x = entryObj.has("x") ? entryObj.get("x").getAsInt() : 0;
                                    int y = entryObj.has("y") ? entryObj.get("y").getAsInt() : 0;
                                    int z = entryObj.has("z") ? entryObj.get("z").getAsInt() : 0;
                                    existing.add(new Module.Setting.RewarpEntry(name, x, y, z));
                                }
                            }
                        }
                    }
                    case MULTI_RANGE -> {
                        if (el.isJsonObject()) {
                            JsonObject rangeObj = el.getAsJsonObject();
                            Module.Setting.RangeValue rv = (Module.Setting.RangeValue) setting.getValue();
                            if (rv != null) {
                                if (rangeObj.has("min")) rv.min = rangeObj.get("min").getAsFloat();
                                if (rangeObj.has("max")) rv.max = rangeObj.get("max").getAsFloat();
                            }
                        }
                    }
                    case BLOCK_LIST -> {
                        if (el.isJsonArray()) {
                            List<String> blocks = new ArrayList<>();
                            for (JsonElement item : el.getAsJsonArray()) {
                                blocks.add(item.getAsString());
                            }
                            ((Module.Setting<List<String>>) setting).setValue(blocks);
                        }
                    }
                    case BUTTON -> {} // No persistent state
                }
            } catch (Exception ignored) {}

            if (setting.getType() != Module.Setting.SettingType.SUB_CONFIG && setting.hasChildren()) {
                String childrenKey = key + "__children";
                if (obj.has(childrenKey)) {
                    deserializeSettings(obj.getAsJsonObject(childrenKey), setting.getChildren());
                }
            }
        }
    }
}
