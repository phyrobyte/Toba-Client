/**
 * @author Fogma
 * @since 2026-03-01
 * no1 touch this either except Fogma
 */
package dev.toba.client.api.script;

import dev.toba.client.api.pathfinding.PathfindingService;
import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.utils.*;
import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.ModuleManager;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.scoreboard.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.luaj.vm2.*;
import org.luaj.vm2.lib.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import static org.luaj.vm2.LuaValue.NIL;
import static org.luaj.vm2.LuaValue.valueOf;

public class LuaAPI {

    /** Modules that scripts are NOT allowed to enable/disable/modify settings on. */
    private static final Set<String> PROTECTED_MODULES = Set.of(
            // Add module names (case-insensitive match) that should be script-inaccessible
            "failsafe", "failsafes", "antifailsafe", "auth", "autoupdater"
    );

    private static boolean isProtectedModule(String name) {
        return PROTECTED_MODULES.contains(name.toLowerCase());
    }

    // ── Incoming chat message queue (fed by ChatHudMixin) ──
    private static final ConcurrentLinkedQueue<String> incomingChatMessages = new ConcurrentLinkedQueue<>();

    /** Called by ChatHudMixin when a chat message is received. */
    public static void onChatMessage(String message) {
        incomingChatMessages.offer(message);
        // Cap at 200 to avoid memory leak if no script is polling
        while (incomingChatMessages.size() > 200) incomingChatMessages.poll();
        // Dispatch to all active LuaScriptModules
        for (var m : ModuleManager.getInstance().getModules()) {
            if (m instanceof LuaScriptModule lsm && m.isEnabled()) {
                lsm.onChatMessage(message);
            }
        }
    }

    public static void bind(Globals g) {
        safeBind(g, "player",     LuaAPI::playerTable);
        safeBind(g, "world",      LuaAPI::worldTable);
        safeBind(g, "game",       LuaAPI::gameTable);
        safeBind(g, "chat",       LuaAPI::chatTable);
        safeBind(g, "imgui",      LuaAPI::imguiTable);
        safeBind(g, "modules",    LuaAPI::modulesTable);
        safeBind(g, "rotation",   LuaAPI::rotationTable);
        safeBind(g, "pathfinder", LuaAPI::pathfinderTable);
        safeBind(g, "esp",        LuaAPI::espTable);
        safeBind(g, "entities",   LuaAPI::entitiesTable);
        safeBind(g, "inventory",  LuaAPI::inventoryTable);
        safeBind(g, "action",     LuaAPI::actionTable);
        safeBind(g, "movement",   LuaAPI::movementTable);
        safeBind(g, "input",      LuaAPI::inputTable);
        safeBind(g, "tablist",    LuaAPI::tablistTable);
        safeBind(g, "bazaar",     LuaAPI::bazaarTable);
        safeBind(g, "scoreboard", LuaAPI::scoreboardTable);
        safeBind(g, "screen",     LuaAPI::screenTable);

        // Standalone time utilities (os library is not available in the sandbox)
        g.set("millis",  zeroArg(() -> valueOf(System.currentTimeMillis())));
        g.set("seconds", zeroArg(() -> valueOf(System.currentTimeMillis() / 1000.0)));
    }

    /** Binds a single API table, logging and continuing if the constructor throws. */
    private static void safeBind(Globals g, String name, java.util.function.Supplier<LuaTable> factory) {
        try {
            g.set(name, factory.get());
        } catch (Exception e) {
            System.err.println("[Toba Scripts] FAILED to bind '" + name + "' API: " + e);
            e.printStackTrace();
        }
    }

    // =========================================================================
    // player — read-only access to the local player
    // =========================================================================

    private static LuaTable playerTable() {
        LuaTable t = new LuaTable();

        // ── Position ──
        t.set("name", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getName().getString()) : NIL; }));
        t.set("x",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getX()) : NIL; }));
        t.set("y",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getY()) : NIL; }));
        t.set("z",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getZ()) : NIL; }));

        // ── Rotation ──
        t.set("yaw",   zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getYaw())   : NIL; }));
        t.set("pitch", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getPitch())  : NIL; }));

        // ── Vitals ──
        t.set("health",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getHealth())    : NIL; }));
        t.set("maxHealth",  zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getMaxHealth()) : NIL; }));
        t.set("hunger",     zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getHungerManager().getFoodLevel()) : NIL; }));
        t.set("absorption", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getAbsorptionAmount()) : NIL; }));
        t.set("armorValue", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getArmor()) : NIL; }));

        // ── XP ──
        t.set("xpLevel",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.experienceLevel)    : NIL; }));
        t.set("xpProgress", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.experienceProgress) : NIL; }));

        // ── Movement state ──
        t.set("isOnGround",  zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isOnGround())  : NIL; }));
        t.set("isSneaking",  zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isSneaking())  : NIL; }));
        t.set("isSprinting", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isSprinting()) : NIL; }));
        t.set("isSwimming",  zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isSwimming())  : NIL; }));
        t.set("isFlying",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getAbilities().flying) : NIL; }));
        t.set("isInWater",   zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isTouchingWater()) : NIL; }));
        t.set("isInLava",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.isInLava()) : NIL; }));

        // ── Eye height ──
        t.set("eyeHeight", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getStandingEyeHeight()) : NIL; }));

        // ── Velocity ──
        t.set("velocityX", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getVelocity().x) : NIL; }));
        t.set("velocityY", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getVelocity().y) : NIL; }));
        t.set("velocityZ", zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getVelocity().z) : NIL; }));

        // ── Inventory (basic) ──
        t.set("heldItem", zeroArg(() -> {
            var p = player();
            if (p == null) return NIL;
            ItemStack stack = p.getMainHandStack();
            return stack.isEmpty() ? valueOf("air") : valueOf(stack.getItem().toString());
        }));

        // ── Dimension ──
        t.set("dimension", zeroArg(() -> {
            ClientWorld w = mc().world;
            return w != null ? valueOf(w.getRegistryKey().getValue().toString()) : NIL;
        }));

        // ── Status effects ──
        t.set("statusEffects", zeroArg(() -> {
            var p = player();
            if (p == null) return NIL;
            LuaTable list = new LuaTable();
            int i = 1;
            for (StatusEffectInstance effect : p.getStatusEffects()) {
                LuaTable e = new LuaTable();
                e.set("name", valueOf(effect.getEffectType().value().getTranslationKey()));
                e.set("duration", valueOf(effect.getDuration()));
                e.set("amplifier", valueOf(effect.getAmplifier()));
                list.set(i++, e);
            }
            return list;
        }));

        return t;
    }

    // =========================================================================
    // world — read-only access to the client world
    // =========================================================================

    private static LuaTable worldTable() {
        LuaTable t = new LuaTable();

        t.set("time", zeroArg(() -> { var w = mc().world; return w != null ? valueOf(w.getTimeOfDay()) : NIL; }));

        t.set("getBlock", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                Block block = w.getBlockState(new BlockPos(x.checkint(), y.checkint(), z.checkint())).getBlock();
                return valueOf(block.toString());
            }
        });

        t.set("getBlockName", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                Block block = w.getBlockState(new BlockPos(x.checkint(), y.checkint(), z.checkint())).getBlock();
                return valueOf(block.getName().getString());
            }
        });

        t.set("isAir", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                return valueOf(w.getBlockState(new BlockPos(x.checkint(), y.checkint(), z.checkint())).isAir());
            }
        });

        t.set("isSolid", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                BlockState state = w.getBlockState(new BlockPos(x.checkint(), y.checkint(), z.checkint()));
                return valueOf(!state.isAir() && state.isSolidBlock(w, new BlockPos(x.checkint(), y.checkint(), z.checkint())));
            }
        });

        t.set("isLoaded", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                return valueOf(w.isChunkLoaded(x.checkint() >> 4, z.checkint() >> 4));
            }
        });

        t.set("getBiome", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                ClientWorld w = mc().world; if (w == null) return NIL;
                try {
                    var biome = w.getBiome(new BlockPos(x.checkint(), y.checkint(), z.checkint()));
                    return valueOf(biome.getKey().map(k -> k.getValue().toString()).orElse("unknown"));
                } catch (Exception e) { return valueOf("unknown"); }
            }
        });

        t.set("getDifficulty", zeroArg(() -> {
            ClientWorld w = mc().world;
            return w != null ? valueOf(w.getDifficulty().getName()) : NIL;
        }));

        t.set("isRaining",     zeroArg(() -> { var w = mc().world; return w != null ? valueOf(w.isRaining())     : NIL; }));
        t.set("isThundering",  zeroArg(() -> { var w = mc().world; return w != null ? valueOf(w.isThundering())  : NIL; }));

        return t;
    }

    // =========================================================================
    // game
    // =========================================================================

    private static LuaTable gameTable() {
        LuaTable t = new LuaTable();
        t.set("fps",          zeroArg(() -> valueOf(MinecraftClient.getInstance().getCurrentFps())));
        t.set("windowWidth",  zeroArg(() -> valueOf(mc().getWindow().getScaledWidth())));
        t.set("windowHeight", zeroArg(() -> valueOf(mc().getWindow().getScaledHeight())));
        t.set("serverAddress", zeroArg(() -> {
            var info = mc().getCurrentServerEntry();
            return info != null ? valueOf(info.address) : NIL;
        }));
        return t;
    }

    // =========================================================================
    // chat
    // =========================================================================

    private static LuaTable chatTable() {
        LuaTable t = new LuaTable();
        t.set("send",  new OneArgFunction() { @Override public LuaValue call(LuaValue s) { mc().execute(() -> { if (mc().player != null) mc().player.networkHandler.sendChatMessage(s.tojstring()); }); return NIL; } });
        t.set("command", new OneArgFunction() { @Override public LuaValue call(LuaValue s) { mc().execute(() -> { if (mc().player != null) mc().player.networkHandler.sendChatCommand(s.tojstring()); }); return NIL; } });
        t.set("info",  new OneArgFunction() { @Override public LuaValue call(LuaValue s) { mc().execute(() -> TobaChat.info(s.tojstring()));  return NIL; } });
        t.set("error", new OneArgFunction() { @Override public LuaValue call(LuaValue s) { mc().execute(() -> TobaChat.error(s.tojstring())); return NIL; } });
        return t;
    }

    // =========================================================================
    // modules — query and control modules + settings access
    // =========================================================================

    private static LuaTable modulesTable() {
        LuaTable t = new LuaTable();

        t.set("get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) {
                Module m = findModule(name.tojstring());
                return m != null ? wrapModule(m) : NIL;
            }
        });

        t.set("list", zeroArg(() -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (Module m : ModuleManager.getInstance().getModules()) {
                if (!m.isHidden()) list.set(i++, valueOf(m.getName()));
            }
            return list;
        }));

        return t;
    }

    private static LuaTable wrapModule(Module m) {
        LuaTable t = new LuaTable();
        boolean prot = isProtectedModule(m.getName());
        t.set("name", valueOf(m.getName()));
        t.set("description", valueOf(m.getDescription()));
        t.set("isEnabled", zeroArg(() -> valueOf(m.isEnabled())));

        if (prot) {
            // Protected modules: read-only access, no state changes
            LuaValue denied = zeroArg(() -> { throw new LuaError("Module '" + m.getName() + "' is protected and cannot be modified by scripts."); });
            t.set("toggle",  denied);
            t.set("enable",  denied);
            t.set("disable", denied);
        } else {
            t.set("toggle",  zeroArg(() -> { mc().execute(m::toggle); return NIL; }));
            t.set("enable",  zeroArg(() -> { mc().execute(() -> m.setEnabled(true));  return NIL; }));
            t.set("disable", zeroArg(() -> { mc().execute(() -> m.setEnabled(false)); return NIL; }));
        }

        // ── Settings access ──
        t.set("getSetting", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) {
                Module.Setting<?> s = m.getSettingByName(name.tojstring());
                if (s == null) return NIL;
                return javaToLua(s.getValue());
            }
        });

        if (prot) {
            t.set("setSetting", new TwoArgFunction() {
                @Override public LuaValue call(LuaValue name, LuaValue val) {
                    throw new LuaError("Module '" + m.getName() + "' is protected and cannot be modified by scripts.");
                }
            });
        } else {
            t.set("setSetting", new TwoArgFunction() {
                @Override public LuaValue call(LuaValue name, LuaValue val) {
                    Module.Setting<?> s = m.getSettingByName(name.tojstring());
                    if (s == null) return NIL;
                    mc().execute(() -> setSettingValue(s, val));
                    return NIL;
                }
            });
        }

        t.set("getSettings", zeroArg(() -> {
            LuaTable list = new LuaTable();
            int i = 1;
            for (Module.Setting<?> s : m.getSettings()) {
                LuaTable st = new LuaTable();
                st.set("name", valueOf(s.getName()));
                st.set("type", valueOf(s.getType().name()));
                st.set("value", javaToLua(s.getValue()));
                list.set(i++, st);
            }
            return list;
        }));

        return t;
    }

    // =========================================================================
    // rotation — RotationUtil control
    // =========================================================================

    private static LuaTable rotationTable() {
        LuaTable t = new LuaTable();

        t.set("setTarget", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue yaw, LuaValue pitch, LuaValue smoothing) {
                RotationUtil.setTarget((float) yaw.todouble(), (float) pitch.todouble(),
                        (float) smoothing.optdouble(0.3));
                return NIL;
            }
        });

        t.set("lookAt", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                var p = player(); if (p == null) return NIL;
                float smoothing = (float) a.arg(4).optdouble(0.3);
                float[] rot = RotationUtil.getRotations(new Vec3d(a.arg1().todouble(), a.arg(2).todouble(), a.arg(3).todouble()));
                RotationUtil.setTarget(rot[0], rot[1], smoothing);
                return NIL;
            }
        });

        t.set("lookAtEntity", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue entityId, LuaValue smoothing) {
                if (mc().world == null) return NIL;
                Entity e = mc().world.getEntityById(entityId.checkint());
                if (e == null) return NIL;
                float[] rot = RotationUtil.getRotations(e);
                RotationUtil.setTarget(rot[0], rot[1], (float) smoothing.optdouble(0.3));
                return NIL;
            }
        });

        t.set("clearTarget", zeroArg(() -> { RotationUtil.clearTarget(); return NIL; }));
        t.set("isActive",    zeroArg(() -> valueOf(RotationUtil.isActive())));
        t.set("getYaw",      zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getYaw())   : NIL; }));
        t.set("getPitch",    zeroArg(() -> { var p = player(); return p != null ? valueOf(p.getPitch())  : NIL; }));

        t.set("getRotationsTo", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                float[] rot = RotationUtil.getRotations(new Vec3d(x.todouble(), y.todouble(), z.todouble()));
                LuaTable r = new LuaTable();
                r.set("yaw", valueOf(rot[0]));
                r.set("pitch", valueOf(rot[1]));
                return r;
            }
        });

        t.set("inFOV", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue entityId, LuaValue fov) {
                if (mc().world == null) return LuaBoolean.FALSE;
                Entity e = mc().world.getEntityById(entityId.checkint());
                if (e == null) return LuaBoolean.FALSE;
                return valueOf(RotationUtil.inFOV(e, (float) fov.optdouble(90)));
            }
        });

        return t;
    }

    // =========================================================================
    // pathfinder — PathfinderModule control
    // =========================================================================

    private static LuaTable pathfinderTable() {
        LuaTable t = new LuaTable();

        t.set("goto", new ThreeArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y, LuaValue z) {
                PathfinderModule pm = ModuleManager.getInstance().getPathfinderModule();
                if (pm != null) mc().execute(() -> pm.startPathTo(new BlockPos(x.checkint(), y.checkint(), z.checkint())));
                return NIL;
            }
        });

        t.set("stop", zeroArg(() -> {
            PathfinderModule pm = ModuleManager.getInstance().getPathfinderModule();
            if (pm != null) mc().execute(pm::stop);
            return NIL;
        }));

        t.set("isPathing", zeroArg(() -> {
            PathfinderModule pm = ModuleManager.getInstance().getPathfinderModule();
            return valueOf(pm != null && pm.isEnabled());
        }));

        return t;
    }

    // =========================================================================
    // esp — ESP rendering
    // =========================================================================

    private static LuaTable espTable() {
        LuaTable t = new LuaTable();

        t.set("addBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                ESPRenderer.getInstance().addBlock(
                        new BlockPos(a.arg1().checkint(), a.arg(2).checkint(), a.arg(3).checkint()),
                        (float) a.arg(4).optdouble(1), (float) a.arg(5).optdouble(0),
                        (float) a.arg(6).optdouble(0), (float) a.arg(7).optdouble(0.4));
                return NIL;
            }
        });

        t.set("addEntity", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                if (mc().world == null) return NIL;
                Entity e = mc().world.getEntityById(a.arg1().checkint());
                if (e == null) return NIL;
                ESPRenderer.getInstance().addEntity(e, mc().getRenderTickCounter().getTickProgress(true),
                        (float) a.arg(2).optdouble(1), (float) a.arg(3).optdouble(0),
                        (float) a.arg(4).optdouble(0), (float) a.arg(5).optdouble(0.4));
                return NIL;
            }
        });

        t.set("addBox", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                ESPRenderer.getInstance().addBox(
                        new Box(a.arg1().todouble(), a.arg(2).todouble(), a.arg(3).todouble(),
                                a.arg(4).todouble(), a.arg(5).todouble(), a.arg(6).todouble()),
                        (float) a.arg(7).optdouble(1), (float) a.arg(8).optdouble(0),
                        (float) a.arg(9).optdouble(0), (float) a.arg(10).optdouble(0.4));
                return NIL;
            }
        });

        t.set("clear", zeroArg(() -> { ESPRenderer.getInstance().beginBlockScan(); return NIL; }));

        return t;
    }

    // =========================================================================
    // entities — entity scanning and queries
    // =========================================================================

    private static LuaTable entitiesTable() {
        LuaTable t = new LuaTable();

        t.set("getAll", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                return collectEntities(e -> true, maxDist.optdouble(128));
            }
        });

        t.set("getPlayers", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                return collectEntities(e -> e instanceof PlayerEntity && e != mc().player, maxDist.optdouble(128));
            }
        });

        t.set("getMobs", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                return collectEntities(e -> e instanceof HostileEntity, maxDist.optdouble(128));
            }
        });

        t.set("getPassive", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                return collectEntities(e -> e instanceof PassiveEntity, maxDist.optdouble(128));
            }
        });

        t.set("getClosest", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                var p = player(); if (p == null || mc().world == null) return NIL;
                double range = maxDist.optdouble(64);
                Entity closest = null; double closestDist = Double.MAX_VALUE;
                for (Entity e : mc().world.getOtherEntities(p, p.getBoundingBox().expand(range))) {
                    if (!(e instanceof LivingEntity)) continue;
                    double d = p.squaredDistanceTo(e);
                    if (d < closestDist) { closestDist = d; closest = e; }
                }
                return closest != null ? entityToLua(closest) : NIL;
            }
        });

        t.set("getById", new OneArgFunction() {
            @Override public LuaValue call(LuaValue id) {
                if (mc().world == null) return NIL;
                Entity e = mc().world.getEntityById(id.checkint());
                return e != null ? entityToLua(e) : NIL;
            }
        });

        t.set("count", new OneArgFunction() {
            @Override public LuaValue call(LuaValue maxDist) {
                var p = player(); if (p == null || mc().world == null) return valueOf(0);
                double range = maxDist.optdouble(64);
                return valueOf(mc().world.getOtherEntities(p, p.getBoundingBox().expand(range)).size());
            }
        });

        return t;
    }

    // =========================================================================
    // inventory
    // =========================================================================

    private static LuaTable inventoryTable() {
        LuaTable t = new LuaTable();

        t.set("getSlot", new OneArgFunction() {
            @Override public LuaValue call(LuaValue slot) {
                var p = player(); if (p == null) return NIL;
                ItemStack stack = p.getInventory().getStack(slot.checkint());
                return stackToLua(stack);
            }
        });

        t.set("getSelectedSlot", zeroArg(() -> {
            var p = player(); return p != null ? valueOf(p.getInventory().getSelectedSlot()) : NIL;
        }));

        t.set("selectSlot", new OneArgFunction() {
            @Override public LuaValue call(LuaValue slot) {
                var p = player(); if (p == null) return NIL;
                int s = slot.checkint();
                if (s >= 0 && s <= 8) p.getInventory().setSelectedSlot(s);
                return NIL;
            }
        });

        t.set("getArmor", new OneArgFunction() {
            @Override public LuaValue call(LuaValue slot) {
                var p = player(); if (p == null) return NIL;
                // Armor slots: feet=0, legs=1, chest=2, head=3 → inventory indices 36-39
                ItemStack stack = p.getInventory().getStack(36 + slot.checkint());
                return stackToLua(stack);
            }
        });

        t.set("findItem", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) {
                var p = player(); if (p == null) return valueOf(-1);
                String search = name.tojstring().toLowerCase();
                for (int i = 0; i < p.getInventory().size(); i++) {
                    ItemStack stack = p.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.getItem().toString().toLowerCase().contains(search))
                        return valueOf(i);
                }
                return valueOf(-1);
            }
        });

        t.set("getSize", zeroArg(() -> {
            var p = player(); return p != null ? valueOf(p.getInventory().size()) : valueOf(0);
        }));

        return t;
    }

    // =========================================================================
    // action — combat and interaction
    // =========================================================================

    private static LuaTable actionTable() {
        LuaTable t = new LuaTable();

        t.set("attack", new OneArgFunction() {
            @Override public LuaValue call(LuaValue entityId) {
                var p = player(); if (p == null || mc().world == null || mc().interactionManager == null) return NIL;
                Entity e = mc().world.getEntityById(entityId.checkint());
                if (e != null) {
                    mc().interactionManager.attackEntity(p, e);
                    p.swingHand(Hand.MAIN_HAND);
                }
                return NIL;
            }
        });

        t.set("useItem", zeroArg(() -> {
            var p = player();
            if (p != null && mc().interactionManager != null)
                mc().interactionManager.interactItem(p, Hand.MAIN_HAND);
            return NIL;
        }));

        t.set("swingHand", zeroArg(() -> {
            var p = player(); if (p != null) p.swingHand(Hand.MAIN_HAND);
            return NIL;
        }));

        t.set("interactEntity", new OneArgFunction() {
            @Override public LuaValue call(LuaValue entityId) {
                var p = player(); if (p == null || mc().world == null || mc().interactionManager == null) return NIL;
                Entity e = mc().world.getEntityById(entityId.checkint());
                if (e != null) mc().interactionManager.interactEntity(p, e, Hand.MAIN_HAND);
                return NIL;
            }
        });

        t.set("interactBlock", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                var p = player(); if (p == null || mc().interactionManager == null) return NIL;
                BlockPos pos = new BlockPos(a.arg1().checkint(), a.arg(2).checkint(), a.arg(3).checkint());
                String faceName = a.arg(4).optjstring("up").toUpperCase();
                Direction dir;
                try { dir = Direction.valueOf(faceName); } catch (Exception e) { dir = Direction.UP; }
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), dir, pos, false);
                mc().interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                return NIL;
            }
        });

        return t;
    }

    // =========================================================================
    // movement — movement key control
    // =========================================================================

    private static LuaTable movementTable() {
        LuaTable t = new LuaTable();
        // Access mc().options lazily at call time, not during table construction (wrong thread)

        t.set("forward",  new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.forwardKey.setPressed(v.toboolean()); return NIL; } });
        t.set("backward", new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.backKey.setPressed(v.toboolean());    return NIL; } });
        t.set("left",     new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.leftKey.setPressed(v.toboolean());    return NIL; } });
        t.set("right",    new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.rightKey.setPressed(v.toboolean());   return NIL; } });
        t.set("jump",     new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.jumpKey.setPressed(v.toboolean());    return NIL; } });
        t.set("sneak",    new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.sneakKey.setPressed(v.toboolean());   return NIL; } });
        t.set("sprint",   new OneArgFunction() { @Override public LuaValue call(LuaValue v) { mc().options.sprintKey.setPressed(v.toboolean());  return NIL; } });

        t.set("stopAll", zeroArg(() -> {
            GameOptions opts = mc().options;
            opts.forwardKey.setPressed(false); opts.backKey.setPressed(false);
            opts.leftKey.setPressed(false);    opts.rightKey.setPressed(false);
            opts.jumpKey.setPressed(false);    opts.sneakKey.setPressed(false);
            opts.sprintKey.setPressed(false);
            return NIL;
        }));

        return t;
    }

    // =========================================================================
    // input — keyboard state queries
    // =========================================================================

    private static LuaTable inputTable() {
        LuaTable t = new LuaTable();

        t.set("isKeyDown", new OneArgFunction() {
            @Override public LuaValue call(LuaValue keyCode) {
                return valueOf(InputUtil.isKeyPressed(mc().getWindow(), keyCode.checkint()));
            }
        });

        // Common key constants
        t.set("KEY_A", GLFW.GLFW_KEY_A); t.set("KEY_B", GLFW.GLFW_KEY_B); t.set("KEY_C", GLFW.GLFW_KEY_C);
        t.set("KEY_D", GLFW.GLFW_KEY_D); t.set("KEY_E", GLFW.GLFW_KEY_E); t.set("KEY_F", GLFW.GLFW_KEY_F);
        t.set("KEY_G", GLFW.GLFW_KEY_G); t.set("KEY_H", GLFW.GLFW_KEY_H); t.set("KEY_I", GLFW.GLFW_KEY_I);
        t.set("KEY_J", GLFW.GLFW_KEY_J); t.set("KEY_K", GLFW.GLFW_KEY_K); t.set("KEY_L", GLFW.GLFW_KEY_L);
        t.set("KEY_M", GLFW.GLFW_KEY_M); t.set("KEY_N", GLFW.GLFW_KEY_N); t.set("KEY_O", GLFW.GLFW_KEY_O);
        t.set("KEY_P", GLFW.GLFW_KEY_P); t.set("KEY_Q", GLFW.GLFW_KEY_Q); t.set("KEY_R", GLFW.GLFW_KEY_R);
        t.set("KEY_S", GLFW.GLFW_KEY_S); t.set("KEY_T", GLFW.GLFW_KEY_T); t.set("KEY_U", GLFW.GLFW_KEY_U);
        t.set("KEY_V", GLFW.GLFW_KEY_V); t.set("KEY_W", GLFW.GLFW_KEY_W); t.set("KEY_X", GLFW.GLFW_KEY_X);
        t.set("KEY_Y", GLFW.GLFW_KEY_Y); t.set("KEY_Z", GLFW.GLFW_KEY_Z);
        t.set("KEY_0", GLFW.GLFW_KEY_0); t.set("KEY_1", GLFW.GLFW_KEY_1); t.set("KEY_2", GLFW.GLFW_KEY_2);
        t.set("KEY_3", GLFW.GLFW_KEY_3); t.set("KEY_4", GLFW.GLFW_KEY_4); t.set("KEY_5", GLFW.GLFW_KEY_5);
        t.set("KEY_6", GLFW.GLFW_KEY_6); t.set("KEY_7", GLFW.GLFW_KEY_7); t.set("KEY_8", GLFW.GLFW_KEY_8);
        t.set("KEY_9", GLFW.GLFW_KEY_9);
        t.set("KEY_SPACE",     GLFW.GLFW_KEY_SPACE);
        t.set("KEY_SHIFT",     GLFW.GLFW_KEY_LEFT_SHIFT);
        t.set("KEY_CTRL",      GLFW.GLFW_KEY_LEFT_CONTROL);
        t.set("KEY_ALT",       GLFW.GLFW_KEY_LEFT_ALT);
        t.set("KEY_TAB",       GLFW.GLFW_KEY_TAB);
        t.set("KEY_ESCAPE",    GLFW.GLFW_KEY_ESCAPE);
        t.set("KEY_ENTER",     GLFW.GLFW_KEY_ENTER);
        t.set("KEY_BACKSPACE", GLFW.GLFW_KEY_BACKSPACE);
        t.set("KEY_UP",        GLFW.GLFW_KEY_UP);
        t.set("KEY_DOWN",      GLFW.GLFW_KEY_DOWN);
        t.set("KEY_LEFT",      GLFW.GLFW_KEY_LEFT);
        t.set("KEY_RIGHT",     GLFW.GLFW_KEY_RIGHT);
        t.set("KEY_F1",  GLFW.GLFW_KEY_F1);  t.set("KEY_F2",  GLFW.GLFW_KEY_F2);
        t.set("KEY_F3",  GLFW.GLFW_KEY_F3);  t.set("KEY_F4",  GLFW.GLFW_KEY_F4);
        t.set("KEY_F5",  GLFW.GLFW_KEY_F5);  t.set("KEY_F6",  GLFW.GLFW_KEY_F6);
        t.set("KEY_F7",  GLFW.GLFW_KEY_F7);  t.set("KEY_F8",  GLFW.GLFW_KEY_F8);
        t.set("KEY_F9",  GLFW.GLFW_KEY_F9);  t.set("KEY_F10", GLFW.GLFW_KEY_F10);
        t.set("KEY_F11", GLFW.GLFW_KEY_F11); t.set("KEY_F12", GLFW.GLFW_KEY_F12);

        return t;
    }

    // =========================================================================
    // tablist — TabListParser SkyBlock data
    // =========================================================================

    private static LuaTable tablistTable() {
        LuaTable t = new LuaTable();
        TabListParser tl = TabListParser.getInstance();

        t.set("getArea",            zeroArg(() -> valueOf(tl.getArea())));
        t.set("getServerInfo",      zeroArg(() -> valueOf(tl.getServerInfo())));
        t.set("getPurse",           zeroArg(() -> valueOf(tl.getPurse())));
        t.set("getBankBalance",     zeroArg(() -> valueOf(tl.getBankBalance())));
        t.set("getProfileType",     zeroArg(() -> valueOf(tl.getProfileType())));
        t.set("getSbLevel",        zeroArg(() -> valueOf(tl.getSbLevel())));
        t.set("getSbLevelProgress", zeroArg(() -> valueOf(tl.getSbLevelProgress())));
        t.set("getGems",           zeroArg(() -> valueOf(tl.getGems())));
        t.set("getCopper",         zeroArg(() -> valueOf(tl.getCopper())));
        t.set("getFarmingLevel",   zeroArg(() -> valueOf(tl.getFarmingLevel())));
        t.set("getGardenLevel",    zeroArg(() -> valueOf(tl.getGardenLevel())));
        t.set("getPestCount",      zeroArg(() -> valueOf(tl.getPestCount())));
        t.set("getVisitorCount",   zeroArg(() -> valueOf(tl.getVisitorCount())));
        t.set("getJacobCrop",      zeroArg(() -> valueOf(tl.getJacobCrop())));
        t.set("getJacobScore",     zeroArg(() -> valueOf(tl.getJacobScore())));
        t.set("getJacobRank",      zeroArg(() -> valueOf(tl.getJacobRank())));
        t.set("getJacobTimeRemaining", zeroArg(() -> valueOf(tl.getJacobTimeRemaining())));
        t.set("getPetName",        zeroArg(() -> valueOf(tl.getPetName())));
        t.set("getPetLevel",       zeroArg(() -> valueOf(tl.getPetLevel())));
        t.set("getPetRarity",      zeroArg(() -> valueOf(tl.getPetRarity())));
        t.set("getCurrentMayor",   zeroArg(() -> valueOf(tl.getCurrentMayor())));
        t.set("getCollectionName",     zeroArg(() -> valueOf(tl.getCollectionName())));
        t.set("getCollectionProgress", zeroArg(() -> valueOf(tl.getCollectionProgress())));
        t.set("getMinionCount",    zeroArg(() -> valueOf(tl.getMinionCount())));
        t.set("getMinionSlots",    zeroArg(() -> valueOf(tl.getMinionSlots())));
        t.set("getBestiaryArea",   zeroArg(() -> valueOf(tl.getBestiaryArea())));
        t.set("getBestiaryProgress", zeroArg(() -> valueOf(tl.getBestiaryProgress())));

        t.set("getSkillLevel", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) { return valueOf(tl.getSkillLevel(name.tojstring())); }
        });
        t.set("getSkillProgress", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) { return valueOf(tl.getSkillProgress(name.tojstring())); }
        });
        t.set("getStat", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) { return valueOf(tl.getStat(name.tojstring())); }
        });
        t.set("getTimer", new OneArgFunction() {
            @Override public LuaValue call(LuaValue name) { return valueOf(tl.getTimer(name.tojstring())); }
        });

        return t;
    }

    // =========================================================================
    // bazaar — BazaarPricingService data
    // =========================================================================

    private static LuaTable bazaarTable() {
        LuaTable t = new LuaTable();
        BazaarPricingService bz = BazaarPricingService.getInstance();

        t.set("getSellPrice",  new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return valueOf(bz.getSellPrice(id.tojstring())); } });
        t.set("getBuyPrice",   new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return valueOf(bz.getBuyPrice(id.tojstring()));  } });
        t.set("getSellVolume", new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return valueOf(bz.getSellVolume(id.tojstring())); } });
        t.set("getBuyVolume",  new OneArgFunction() { @Override public LuaValue call(LuaValue id) { return valueOf(bz.getBuyVolume(id.tojstring()));  } });
        t.set("isReady",       zeroArg(() -> valueOf(bz.isReady())));
        t.set("getProductCount", zeroArg(() -> valueOf(bz.getProductCount())));

        // Common product IDs
        for (String id : new String[]{
                "ENCHANTED_MELON", "ENCHANTED_PUMPKIN", "ENCHANTED_SUGAR_CANE",
                "ENCHANTED_CACTUS", "ENCHANTED_COCOA", "ENCHANTED_CARROT",
                "ENCHANTED_POTATO", "ENCHANTED_NETHER_STALK", "ENCHANTED_HAY_BALE",
                "ENCHANTED_DIAMOND", "ENCHANTED_GOLD", "ENCHANTED_IRON",
                "ENCHANTED_COAL", "ENCHANTED_COBBLESTONE", "ENCHANTED_LAPIS_LAZULI",
                "ENCHANTED_REDSTONE", "ENCHANTED_EMERALD", "ENCHANTED_MITHRIL",
                "ENCHANTED_ROTTEN_FLESH", "ENCHANTED_BONE", "ENCHANTED_ENDER_PEARL",
                "ENCHANTED_BLAZE_ROD", "ENCHANTED_SPIDER_EYE", "ENCHANTED_STRING",
                "ENCHANTED_GUNPOWDER", "ENCHANTED_SLIME_BALL",
                "REVENANT_FLESH", "TARANTULA_WEB", "WOLF_TOOTH", "NULL_SPHERE",
                "WHEAT", "MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "COCOA_BEANS",
                "CARROT_ITEM", "POTATO_ITEM", "NETHER_STALK",
                "DIAMOND", "GOLD_INGOT", "IRON_INGOT", "COAL", "COBBLESTONE",
                "LAPIS_LAZULI", "REDSTONE", "EMERALD", "MITHRIL_ORE",
                "ROTTEN_FLESH", "BONE", "ENDER_PEARL", "BLAZE_ROD",
                "SPIDER_EYE", "STRING", "GUNPOWDER", "SLIME_BALL"
        }) {
            t.set(id, valueOf(id));
        }

        return t;
    }

    // =========================================================================
    // scoreboard — sidebar scoreboard reading
    // =========================================================================

    private static LuaTable scoreboardTable() {
        LuaTable t = new LuaTable();

        t.set("getTitle", zeroArg(() -> {
            try {
                Scoreboard sb = mc().world.getScoreboard();
                ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                return obj != null ? valueOf(obj.getDisplayName().getString()) : NIL;
            } catch (Exception e) { return NIL; }
        }));

        t.set("getLines", zeroArg(() -> {
            try {
                Scoreboard sb = mc().world.getScoreboard();
                ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                if (obj == null) return NIL;

                LuaTable lines = new LuaTable();
                List<ScoreboardEntry> entries = sb.getScoreboardEntries(obj).stream()
                        .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed())
                        .limit(15)
                        .collect(Collectors.toList());
                int i = 1;
                for (ScoreboardEntry entry : entries) {
                    String text;
                    if (entry.display() != null) {
                        text = entry.display().getString();
                    } else {
                        Team team = sb.getScoreHolderTeam(entry.owner());
                        text = team != null
                                ? Team.decorateName(team, entry.name()).getString()
                                : entry.owner();
                    }
                    // Strip section sign color codes
                    lines.set(i++, valueOf(text.replaceAll("\u00A7.", "")));
                }
                return lines;
            } catch (Exception e) { return NIL; }
        }));

        t.set("getLine", new OneArgFunction() {
            @Override public LuaValue call(LuaValue index) {
                try {
                    Scoreboard sb = mc().world.getScoreboard();
                    ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
                    if (obj == null) return NIL;

                    List<ScoreboardEntry> entries = sb.getScoreboardEntries(obj).stream()
                            .sorted(Comparator.comparingInt(ScoreboardEntry::value).reversed())
                            .limit(15)
                            .collect(Collectors.toList());
                    int idx = index.checkint() - 1; // Lua is 1-indexed
                    if (idx < 0 || idx >= entries.size()) return NIL;
                    ScoreboardEntry entry = entries.get(idx);
                    String text;
                    if (entry.display() != null) {
                        text = entry.display().getString();
                    } else {
                        Team team = sb.getScoreHolderTeam(entry.owner());
                        text = team != null
                                ? Team.decorateName(team, entry.name()).getString()
                                : entry.owner();
                    }
                    return valueOf(text.replaceAll("\u00A7.", ""));
                } catch (Exception e) { return NIL; }
            }
        });

        return t;
    }

    // =========================================================================
    // screen — container/GUI interaction
    // =========================================================================

    private static LuaTable screenTable() {
        LuaTable t = new LuaTable();

        t.set("isOpen", zeroArg(() ->
                valueOf(mc().currentScreen instanceof HandledScreen<?>)));

        t.set("getTitle", zeroArg(() -> {
            if (mc().currentScreen instanceof HandledScreen<?> hs)
                return valueOf(hs.getTitle().getString());
            return NIL;
        }));

        t.set("close", zeroArg(() -> {
            mc().execute(() -> { if (mc().player != null) mc().player.closeHandledScreen(); });
            return NIL;
        }));

        t.set("getSlotCount", zeroArg(() -> {
            if (mc().player == null) return valueOf(0);
            ScreenHandler handler = mc().player.currentScreenHandler;
            return handler != null ? valueOf(handler.slots.size()) : valueOf(0);
        }));

        t.set("getSlot", new OneArgFunction() {
            @Override public LuaValue call(LuaValue slot) {
                if (mc().player == null) return NIL;
                ScreenHandler handler = mc().player.currentScreenHandler;
                if (handler == null) return NIL;
                int idx = slot.checkint();
                if (idx < 0 || idx >= handler.slots.size()) return NIL;
                ItemStack stack = handler.getSlot(idx).getStack();
                if (stack.isEmpty()) return NIL;

                LuaTable st = new LuaTable();
                st.set("name", valueOf(stack.getItem().toString()));
                st.set("displayName", valueOf(stack.getName().getString()));
                st.set("count", valueOf(stack.getCount()));

                // Lore / tooltip lines
                try {
                    LuaTable lore = new LuaTable();
                    Item.TooltipContext ctx = mc().world != null
                            ? Item.TooltipContext.create(mc().world)
                            : Item.TooltipContext.DEFAULT;
                    List<net.minecraft.text.Text> tooltip = stack.getTooltip(ctx, mc().player, TooltipType.BASIC);
                    int i = 1;
                    for (var line : tooltip) lore.set(i++, valueOf(line.getString()));
                    st.set("lore", lore);
                } catch (Exception e) {
                    st.set("lore", new LuaTable());
                }
                return st;
            }
        });

        t.set("click", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue slot, LuaValue button) {
                mc().execute(() -> {
                    if (mc().player == null || mc().interactionManager == null) return;
                    ScreenHandler handler = mc().player.currentScreenHandler;
                    if (handler == null) return;
                    mc().interactionManager.clickSlot(handler.syncId, slot.checkint(),
                            button.optint(0), SlotActionType.PICKUP, mc().player);
                });
                return NIL;
            }
        });

        t.set("shiftClick", new OneArgFunction() {
            @Override public LuaValue call(LuaValue slot) {
                mc().execute(() -> {
                    if (mc().player == null || mc().interactionManager == null) return;
                    ScreenHandler handler = mc().player.currentScreenHandler;
                    if (handler == null) return;
                    mc().interactionManager.clickSlot(handler.syncId, slot.checkint(),
                            0, SlotActionType.QUICK_MOVE, mc().player);
                });
                return NIL;
            }
        });

        return t;
    }

    // =========================================================================
    // imgui (unchanged)
    // =========================================================================

    private static LuaTable imguiTable() {
        LuaTable t = new LuaTable();

        t.set("begin", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue id, LuaValue flags) {
                return valueOf(ImGui.begin(id.tojstring(), flags.optint(0)));
            }
        });
        t.set("finish", zeroArg(() -> { ImGui.end(); return NIL; }));

        t.set("setNextWindowPos", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue x, LuaValue y) {
                ImGui.setNextWindowPos((float) x.todouble(), (float) y.todouble(), ImGuiCond.Always);
                return NIL;
            }
        });
        t.set("setNextWindowSize", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue w, LuaValue h) {
                ImGui.setNextWindowSize((float) w.todouble(), (float) h.todouble());
                return NIL;
            }
        });

        t.set("text", new OneArgFunction() { @Override public LuaValue call(LuaValue s) { ImGui.text(s.tojstring()); return NIL; } });
        t.set("textColored", new VarArgFunction() {
            @Override public Varargs invoke(Varargs a) {
                ImGui.textColored((float) a.arg(1).optdouble(1), (float) a.arg(2).optdouble(1),
                        (float) a.arg(3).optdouble(1), (float) a.arg(4).optdouble(1), a.arg(5).tojstring());
                return NIL;
            }
        });
        t.set("button", new OneArgFunction() { @Override public LuaValue call(LuaValue s) { return valueOf(ImGui.button(s.tojstring())); } });
        t.set("separator", zeroArg(() -> { ImGui.separator(); return NIL; }));
        t.set("sameLine",  zeroArg(() -> { ImGui.sameLine();  return NIL; }));
        t.set("dummy", new TwoArgFunction() {
            @Override public LuaValue call(LuaValue w, LuaValue h) {
                ImGui.dummy((float) w.todouble(), (float) h.todouble()); return NIL;
            }
        });
        t.set("getWindowPosX",    zeroArg(() -> valueOf(ImGui.getWindowPosX())));
        t.set("getWindowPosY",    zeroArg(() -> valueOf(ImGui.getWindowPosY())));
        t.set("getFontSize",      zeroArg(() -> valueOf(ImGui.getFontSize())));
        t.set("isWindowFocused",  zeroArg(() -> valueOf(ImGui.isWindowFocused())));
        t.set("wantCaptureMouse", zeroArg(() -> valueOf(ImGui.getIO().getWantCaptureMouse())));

        LuaTable flags = new LuaTable();
        flags.set("NoTitleBar",       ImGuiWindowFlags.NoTitleBar);
        flags.set("NoResize",         ImGuiWindowFlags.NoResize);
        flags.set("AlwaysAutoResize", ImGuiWindowFlags.AlwaysAutoResize);
        flags.set("NoBackground",     ImGuiWindowFlags.NoBackground);
        flags.set("NoInputs",         ImGuiWindowFlags.NoInputs);
        flags.set("NoMove",           ImGuiWindowFlags.NoMove);
        flags.set("NoScrollbar",      ImGuiWindowFlags.NoScrollbar);
        t.set("Flags", flags);

        return t;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }
    private static ClientPlayerEntity player() { return mc().player; }

    private static Module findModule(String name) {
        for (Module m : ModuleManager.getInstance().getModules())
            if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    /** Shorthand for a zero-arg Lua function backed by a Java Supplier. */
    @FunctionalInterface
    interface LuaSupplier { LuaValue get(); }

    private static ZeroArgFunction zeroArg(LuaSupplier fn) {
        return new ZeroArgFunction() { @Override public LuaValue call() { return fn.get(); } };
    }

    // ── Entity helpers ──

    private static LuaTable entityToLua(Entity e) {
        LuaTable t = new LuaTable();
        t.set("id",   valueOf(e.getId()));
        t.set("name", valueOf(e.getName().getString()));
        t.set("x",    valueOf(e.getX()));
        t.set("y",    valueOf(e.getY()));
        t.set("z",    valueOf(e.getZ()));

        String type = "other";
        if (e instanceof PlayerEntity)  type = "player";
        else if (e instanceof HostileEntity) type = "hostile";
        else if (e instanceof PassiveEntity) type = "passive";
        else if (e instanceof LivingEntity)  type = "living";
        t.set("type", valueOf(type));

        if (e instanceof LivingEntity le) {
            t.set("health",    valueOf(le.getHealth()));
            t.set("maxHealth", valueOf(le.getMaxHealth()));
        }

        var p = player();
        if (p != null) t.set("distance", valueOf(Math.sqrt(p.squaredDistanceTo(e))));

        return t;
    }

    private static LuaTable collectEntities(java.util.function.Predicate<Entity> filter, double range) {
        var p = player();
        if (p == null || mc().world == null) return new LuaTable();

        LuaTable list = new LuaTable();
        int i = 1;
        for (Entity e : mc().world.getOtherEntities(p, p.getBoundingBox().expand(range))) {
            if (filter.test(e)) list.set(i++, entityToLua(e));
        }
        return list;
    }

    // ── Item stack helpers ──

    private static LuaTable stackToLua(ItemStack stack) {
        LuaTable t = new LuaTable();
        if (stack.isEmpty()) {
            t.set("name", valueOf("air"));
            t.set("count", valueOf(0));
            t.set("displayName", valueOf("Air"));
        } else {
            t.set("name", valueOf(stack.getItem().toString()));
            t.set("count", valueOf(stack.getCount()));
            t.set("displayName", valueOf(stack.getName().getString()));
        }
        return t;
    }

    // ── Settings helpers ──

    private static LuaValue javaToLua(Object value) {
        if (value == null) return NIL;
        if (value instanceof Boolean b) return valueOf(b);
        if (value instanceof Integer i) return valueOf(i);
        if (value instanceof Float f)   return valueOf(f);
        if (value instanceof Double d)  return valueOf(d);
        if (value instanceof String s)  return valueOf(s);
        return valueOf(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static void setSettingValue(Module.Setting<?> setting, LuaValue val) {
        switch (setting.getType()) {
            case BOOLEAN -> ((Module.Setting<Boolean>) setting).setValue(val.toboolean());
            case INTEGER -> ((Module.Setting<Integer>) setting).setValue(val.toint());
            case FLOAT   -> ((Module.Setting<Float>) setting).setValue((float) val.todouble());
            case COLOR   -> ((Module.Setting<Integer>) setting).setValue(val.toint());
            case STRING, MODE -> ((Module.Setting<String>) setting).setValue(val.tojstring());
            default -> {} // SUB_CONFIG, BUTTON, etc. — not settable
        }
    }
}
