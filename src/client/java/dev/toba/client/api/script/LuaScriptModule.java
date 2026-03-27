/**
 * @author Fogma
 * @since 2026-03-01
 * no1 touch this either except Fogma
 */
package dev.toba.client.api.script;

import dev.toba.client.api.imgui.ImGuiImpl;
import dev.toba.client.api.imgui.RenderInterface;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.features.settings.Module;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.luaj.vm2.lib.PackageLib;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class LuaScriptModule extends Module {

    private final File file;
    private final Globals globals;

    private LuaFunction onEnableFn;
    private LuaFunction onDisableFn;
    private LuaFunction onTickFn;
    private LuaFunction onRenderFn;
    private LuaFunction onChatFn;

    private final RenderInterface renderRef = io -> callRender();

    /** Map from setting name to its Setting object, for fast Lua get/set. */
    private final Map<String, Setting<?>> settingsByName = new LinkedHashMap<>();

    private LuaScriptModule(File file, String name, String desc, Category category, Globals preloaded) {
        super(name, desc, category);
        this.file = file;
        setIsScript(true);

        this.globals = preloaded;

        onEnableFn  = fnOrNull("onEnable");
        onDisableFn = fnOrNull("onDisable");
        onTickFn    = fnOrNull("onTick");
        onRenderFn  = fnOrNull("onRender");
        onChatFn    = fnOrNull("onChat");
    }

    @Override
    protected void onEnable() {
        guardedCall(onEnableFn);
        if (onRenderFn != null) ImGuiImpl.registerHudOverlay(renderRef);
    }

    @Override
    protected void onDisable() {
        guardedCall(onDisableFn);
        if (onRenderFn != null) ImGuiImpl.unregisterHudOverlay(renderRef);
    }

    @Override
    public void onTick() {
        guardedCall(onTickFn);
    }

    /** Called by LuaAPI.onChatMessage when any chat message is received. */
    public void onChatMessage(String message) {
        if (onChatFn == null) return;
        InstructionLimitLib lim = getLimiter();
        if (lim != null) lim.reset();
        try {
            onChatFn.call(LuaValue.valueOf(message));
        } catch (LuaError e) {
            TobaChat.error(getName() + " onChat: " + e.getMessage());
        } finally {
            if (lim != null) lim.stop();
        }
    }

    /**
     * Loads a Lua script file into a sandboxed environment.
     * The script is executed exactly once; metadata (name, description, category)
     * and callback functions are read from the same Globals instance.
     *
     * @return the loaded module, or {@code null} if the script failed to compile or execute.
     */
    public static LuaScriptModule load(File file) {
        Globals g;
        try {
            g = createSandboxedGlobals();
        } catch (Exception e) {
            scriptError(file, "failed to create Lua VM: " + e);
            return null;
        }

        try {
            LuaAPI.bind(g);
        } catch (Exception e) {
            scriptError(file, "failed to bind API: " + e);
            e.printStackTrace();
            return null;
        }

        // Create a temporary module reference for settings binding.
        // Settings defined during script load are collected here.
        Map<String, Setting<?>> pendingSettings = new LinkedHashMap<>();
        LuaTable settingsTable = createSettingsTable(pendingSettings);
        g.set("settings", settingsTable);

        try {
            LuaValue chunk = g.loadfile(file.getAbsolutePath());
            if (chunk.isnil()) {
                scriptError(file, "failed to compile (file not found or syntax error)");
                return null;
            }
            chunk.call();
        } catch (LuaError e) {
            scriptError(file, e.getMessage());
            return null;
        } catch (Exception e) {
            scriptError(file, e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        String name   = g.get("name").optjstring(file.getName().replace(".lua", ""));
        String desc   = g.get("description").optjstring("");
        String catStr = g.get("category").optjstring("MISC").toUpperCase();

        Category cat;
        try { cat = Category.valueOf(catStr); }
        catch (Exception e) { cat = Category.MISC; }

        LuaScriptModule module = new LuaScriptModule(file, name, desc, cat, g);

        // Register all settings defined during script load onto the module.
        for (Map.Entry<String, Setting<?>> entry : pendingSettings.entrySet()) {
            module.addSetting(entry.getValue());
            module.settingsByName.put(entry.getKey(), entry.getValue());
        }

        // Now re-bind the settings table with the actual module reference
        // so get/set resolve against the real settings.
        g.set("settings", createLiveSettingsTable(module));

        return module;
    }

    // =========================================================================
    // Settings table — load-time (collects setting definitions)
    // =========================================================================

    private static LuaTable createSettingsTable(Map<String, Setting<?>> pending) {
        LuaTable t = new LuaTable();

        t.set("addBoolean", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue name, LuaValue def) {
                Setting<Boolean> s = new Setting<>(name.tojstring(), def.optboolean(false), Setting.SettingType.BOOLEAN);
                pending.put(name.tojstring(), s);
                return NIL;
            }
        });

        t.set("addFloat", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                String n = a.arg1().tojstring();
                float def = (float) a.arg(2).optdouble(0);
                float min = (float) a.arg(3).optdouble(0);
                float max = (float) a.arg(4).optdouble(100);
                Setting<Float> s = new Setting<>(n, def, Setting.SettingType.FLOAT);
                s.range(min, max);
                pending.put(n, s);
                return NIL;
            }
        });

        t.set("addInteger", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                String n = a.arg1().tojstring();
                int def = a.arg(2).optint(0);
                int min = a.arg(3).optint(0);
                int max = a.arg(4).optint(100);
                Setting<Integer> s = new Setting<>(n, def, Setting.SettingType.INTEGER);
                s.range(min, max);
                pending.put(n, s);
                return NIL;
            }
        });

        t.set("addMode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                String n = a.arg1().tojstring();
                String def = a.arg(2).optjstring("");
                LuaTable modesTable = a.arg(3).opttable(new LuaTable());
                String[] modes = new String[modesTable.length()];
                for (int i = 1; i <= modesTable.length(); i++) {
                    modes[i - 1] = modesTable.get(i).tojstring();
                }
                Setting<String> s = new Setting<>(n, def, Setting.SettingType.MODE);
                if (modes.length > 0) s.modes(modes);
                pending.put(n, s);
                return NIL;
            }
        });

        t.set("addColor", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue name, LuaValue def) {
                Setting<Integer> s = new Setting<>(name.tojstring(), def.optint(0xFFFFFFFF), Setting.SettingType.COLOR);
                pending.put(name.tojstring(), s);
                return NIL;
            }
        });

        t.set("addString", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue name, LuaValue def) {
                Setting<String> s = new Setting<>(name.tojstring(), def.optjstring(""), Setting.SettingType.STRING);
                pending.put(name.tojstring(), s);
                return NIL;
            }
        });

        // Placeholder get/set during load — returns default values
        t.set("get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) {
                Setting<?> s = pending.get(name.tojstring());
                return s != null ? javaToLua(s.getValue()) : NIL;
            }
        });
        t.set("set", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue name, LuaValue val) { return NIL; }
        });

        return t;
    }

    // =========================================================================
    // Settings table — runtime (reads/writes actual module settings)
    // =========================================================================

    private static LuaTable createLiveSettingsTable(LuaScriptModule module) {
        LuaTable t = new LuaTable();

        t.set("get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) {
                Setting<?> s = module.settingsByName.get(name.tojstring());
                return s != null ? javaToLua(s.getValue()) : NIL;
            }
        });

        t.set("set", new TwoArgFunction() {
            @SuppressWarnings("unchecked")
            @Override public LuaValue call(LuaValue name, LuaValue val) {
                Setting<?> s = module.settingsByName.get(name.tojstring());
                if (s == null) return NIL;
                switch (s.getType()) {
                    case BOOLEAN -> ((Setting<Boolean>) s).setValue(val.toboolean());
                    case INTEGER -> ((Setting<Integer>) s).setValue(val.toint());
                    case FLOAT   -> ((Setting<Float>) s).setValue((float) val.todouble());
                    case COLOR   -> ((Setting<Integer>) s).setValue(val.toint());
                    case STRING, MODE -> ((Setting<String>) s).setValue(val.tojstring());
                    default -> {}
                }
                return NIL;
            }
        });

        // Re-expose add methods as no-ops at runtime (script already loaded)
        LuaValue noop = new VarArgFunction() { @Override public Varargs invoke(Varargs a) { return NIL; } };
        t.set("addBoolean", noop);
        t.set("addFloat", noop);
        t.set("addInteger", noop);
        t.set("addMode", noop);
        t.set("addColor", noop);
        t.set("addString", noop);

        return t;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Logs a script error to both stderr (always visible in launcher logs) and TobaChat
     * (visible in-game once the player joins).
     */
    private static void scriptError(File file, String message) {
        String msg = "[Toba Scripts] " + file.getName() + ": " + message;
        System.err.println(msg);
        TobaChat.error(file.getName() + ": " + message);
    }

    /** Maximum instructions per callback invocation (prevents infinite loops from freezing the game). */
    private static final int MAX_INSTRUCTIONS_PER_CALL = 1_000_000;

    private static Globals createSandboxedGlobals() {
        Globals g = new Globals();
        g.load(new JseBaseLib());
        // PackageLib must be loaded before other libs — they all register in package.loaded.
        // We remove require() afterward so scripts stay self-contained.
        g.load(new PackageLib());
        g.load(new Bit32Lib());
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new CoroutineLib());
        g.load(new JseMathLib());
        // NOT loaded: OsLib, IoLib, LuajavaLib
        LoadState.install(g);
        LuaC.install(g);
        // Remove require/module so scripts can't load arbitrary code
        g.set("require", LuaValue.NIL);
        g.set("module", LuaValue.NIL);

        // Install instruction limiter to prevent infinite loops from freezing the game.
        InstructionLimitLib limiter = new InstructionLimitLib();
        g.load(limiter);
        g.set("__limiter", LuaValue.userdataOf(limiter));
        return g;
    }

    /**
     * DebugLib subclass that counts instructions and throws after the limit,
     * preventing a buggy or malicious script from freezing the game thread.
     */
    static class InstructionLimitLib extends org.luaj.vm2.lib.DebugLib {
        private int count;
        private boolean active;

        void reset() { count = 0; active = true; }
        void stop()  { active = false; }

        @Override
        public void onInstruction(int pc, Varargs v, int top) {
            if (!active) return;
            if (++count > MAX_INSTRUCTIONS_PER_CALL) {
                active = false;
                throw new LuaError("Script exceeded instruction limit (" + MAX_INSTRUCTIONS_PER_CALL
                        + ") — possible infinite loop.");
            }
            super.onInstruction(pc, v, top);
        }
    }

    /** Gets the instruction limiter from globals, or null. */
    private InstructionLimitLib getLimiter() {
        LuaValue v = globals.get("__limiter");
        return v.isuserdata() && v.touserdata() instanceof InstructionLimitLib l ? l : null;
    }

    private LuaFunction fnOrNull(String name) {
        LuaValue v = globals.get(name);
        return v instanceof LuaFunction f ? f : null;
    }

    private void guardedCall(LuaFunction fn) {
        if (fn == null) return;
        InstructionLimitLib lim = getLimiter();
        if (lim != null) lim.reset();
        try { fn.call(); }
        catch (LuaError e) { TobaChat.error(getName() + ": " + e.getMessage()); }
        finally { if (lim != null) lim.stop(); }
    }

    private static void call(LuaFunction fn) {
        if (fn == null) return;
        try { fn.call(); }
        catch (LuaError e) { TobaChat.error(e.getMessage()); }
    }

    private void callRender() {
        if (onRenderFn == null) return;
        InstructionLimitLib lim = getLimiter();
        if (lim != null) lim.reset();
        try { onRenderFn.call(); }
        catch (LuaError e) { TobaChat.error(getName() + " onRender: " + e.getMessage()); }
        finally { if (lim != null) lim.stop(); }
    }

    private static LuaValue javaToLua(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof Boolean b) return LuaBoolean.valueOf(b);
        if (value instanceof Integer i) return LuaValue.valueOf(i);
        if (value instanceof Float f)   return LuaValue.valueOf(f);
        if (value instanceof Double d)  return LuaValue.valueOf(d);
        if (value instanceof String s)  return LuaValue.valueOf(s);
        return LuaValue.valueOf(value.toString());
    }

    public File getFile() { return file; }
}
