package dev.toba.client.features.settings;

import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private boolean enabled = false;
    private boolean hidden = false;
    private boolean settingsOnly = false;
    private boolean isScript = false;
    private int keyBind = -1;
    public transient boolean wasKeyDown = false;
    private final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable()  {}
    protected void onDisable() {}
    public void onTick()       {}

    public List<Setting<?>> getSettings() { return settings; }

    public Setting<?> getSettingByName(String name) {
        for (Setting<?> s : settings) {
            if (s.getName().equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    protected <T> Setting<T> addSetting(Setting<T> setting) {
        settings.add(setting);
        return setting;
    }

    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Category getCategory()  { return category; }
    public boolean isEnabled()     { return enabled; }
    public int getKeyBind()        { return keyBind; }
    public void setKeyBind(int keyBind) { this.keyBind = keyBind; }
    public boolean isHidden()      { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public boolean isSettingsOnly()  { return settingsOnly; }
    public void setSettingsOnly(boolean settingsOnly) { this.settingsOnly = settingsOnly; }
    public boolean isScript()        { return isScript; }
    public void setIsScript(boolean isScript) { this.isScript = isScript; }

    public enum Category {
        CLIENT("Client"), MACRO("Macro"), RENDER("Render"), MISC("Misc"), COMBAT("Combat"), SCRIPT("Script");
        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public static class Setting<T> {
        private final String name;
        private T value;
        private final T defaultValue;
        private final SettingType type;
        private double min = 0;
        private double max = 99999999;
        private ItemStack icon = ItemStack.EMPTY;
        private final List<Setting<?>> children = new ArrayList<>();
        private final List<String> modes = new ArrayList<>();
        private boolean expanded = false;
        private Runnable buttonCallback = null;
        private RewarpListProvider rewarpListProvider = null;

        public Setting(String name, T defaultValue, SettingType type) {
            this.name = name;
            this.value = defaultValue;
            this.defaultValue = defaultValue;
            this.type = type;
        }

        public Setting<T> modes(String... options)       { this.modes.addAll(Arrays.asList(options)); return this; }
        public Setting<T> range(double min, double max)  { this.min = min; this.max = max; return this; }
        public Setting<T> icon(ItemStack icon)           { this.icon = icon; return this; }
        public Setting<T> child(Setting<?> child)        { this.children.add(child); return this; }
        public Setting<T> callback(Runnable callback)    { this.buttonCallback = callback; return this; }
        public Setting<T> rewarpProvider(RewarpListProvider p) { this.rewarpListProvider = p; return this; }

        public String getName()               { return name; }
        public T getValue()                   { return value; }
        public void setValue(T value)         { this.value = value; }
        public T getDefaultValue()            { return defaultValue; }
        public SettingType getType()          { return type; }
        public double getMin()                { return min; }
        public double getMax()                { return max; }
        public ItemStack getIcon()            { return icon; }
        public List<Setting<?>> getChildren() { return children; }
        public List<String> getModes()        { return modes; }
        public boolean isExpanded()           { return expanded; }
        public void setExpanded(boolean e)    { this.expanded = e; }
        public void toggleExpanded()          { this.expanded = !this.expanded; }
        public boolean hasChildren()          { return !children.isEmpty(); }
        public Runnable getButtonCallback()   { return buttonCallback; }
        public RewarpListProvider getRewarpListProvider() { return rewarpListProvider; }

        public enum SettingType {
            BOOLEAN, INTEGER, FLOAT, COLOR, SUB_CONFIG, STRING, MODE, BUTTON, REWARP_LIST, MULTI_RANGE, BLOCK_LIST
        }

        public static class RangeValue {
            public float min, max;

            public RangeValue(float min, float max) {
                this.min = min;
                this.max = max;
            }

            public float random() {
                return min + (float) Math.random() * (max - min);
            }

            @Override
            public String toString() {
                return min + " – " + max;
            }
        }

        public interface RewarpListProvider {
            List<RewarpEntry> getRewarpEntries();
            void onSetRewarp();
            void onRemoveRewarp(int index);
        }

        public static class RewarpEntry {
            public final String name;
            public final int x, y, z;

            public RewarpEntry(String name, int x, int y, int z) {
                this.name = name;
                this.x = x;
                this.y = y;
                this.z = z;
            }

            public String coordString() { return x + ", " + y + ", " + z; }
        }
    }
}