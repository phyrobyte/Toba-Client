# Toba Client — Developer Documentation

Comprehensive guide to the architecture, systems, and patterns used throughout the Toba Minecraft mod.

**Minecraft**: 1.21.11 | **Mappings**: Yarn | **Loader**: Fabric | **Java**: 21 | **GUI**: Dear ImGui (imgui-java 1.90.0)

---

## Table of Contents

1. [Project Structure](#1-project-structure)
2. [Initialization Flow](#2-initialization-flow)
3. [Module System](#3-module-system)
4. [Settings System](#4-settings-system)
5. [Config Persistence](#5-config-persistence)
6. [ImGui Rendering System](#6-imgui-rendering-system)
7. [Mixin Reference](#7-mixin-reference)
8. [Rotation Manager](#8-rotation-manager)
9. [Pathfinding System](#9-pathfinding-system)
10. [ESP and Path Rendering](#10-esp-and-path-rendering)
11. [Failsafe System](#11-failsafe-system)
12. [Script Statistics Overlay](#12-script-statistics-overlay)
13. [Inventory HUD](#13-inventory-hud)
14. [Utility Classes](#14-utility-classes)
15. [Client Commands](#15-client-commands)
16. [Built-in Modules Reference](#16-built-in-modules-reference)
17. [Yarn Mapping Gotchas](#17-yarn-mapping-gotchas)
18. [Common Patterns and Recipes](#18-common-patterns-and-recipes)

---

## 1. Project Structure

```
src/client/java/dev/toba/client/
|-- TobaClient.java                          # Client entry point
|
|-- api/
|   |-- config/
|   |   +-- TobaConfig.java                  # JSON config save/load
|   |-- failsafes/
|   |   |-- FailsafeManager.java             # Central failsafe tick loop
|   |   +-- FailsafeWarning.java             # Warning state machine
|   |-- imgui/
|   |   |-- ImGuiImpl.java                   # ImGui init, fonts, frame lifecycle
|   |   +-- RenderInterface.java             # Functional interface for overlays
|   |-- pathfinding/
|   |   |-- PathfindingService.java          # A* facade (ground + fly)
|   |   +-- navmesh/                         # NavMesh generation and search
|   |-- render/
|   |   |-- ESPRenderer.java                 # Entity/block box renderer
|   |   +-- PathRenderer.java                # Waypoint line renderer
|   |-- rotation/
|   |   +-- RotationManager.java             # Centralized look-at system
|   |-- service/
|   |   |-- BlockInteractionService.java     # Central block breaking loop
|   |   |-- InventoryService.java            # Hotbar selection/swapping
|   |   |-- WorldScanner.java                # Block scanning by predicate
|   |   +-- EventBus.java                    # Pub/sub for game events
|   +-- utils/
|       |-- BazaarPricingService.java         # Hypixel bazaar API
|       |-- ColorUtil.java                    # ARGB/ABGR conversion
|       |-- KeybindUtil.java                  # Module keybind handling
|       |-- KeyUtil.java                      # GLFW key names
|       |-- TabListParser.java                # Hypixel tab list parsing
|       |-- TextUtil.java                     # Formatting helpers
|       +-- TobaChat.java                     # Client-side chat messages
|
|-- features/
|   |-- impl/module/
|   |   |-- client/
|   |   |   +-- ClickGUI.java                # Opens settings screen
|   |   |-- macro/
|   |   |   |-- farming/                     # Crop farming macros
|   |   |   |-- fishing/                     # Fishing macros
|   |   |   |-- foraging/
|   |   |   |   +-- TreeCutterModule.java    # Auto tree chopping
|   |   |   |-- mining/                      # Mining macros
|   |   |   +-- misc/
|   |   |       +-- PathfinderModule.java    # Pathfinding wrapper module
|   |   |-- qol/
|   |   |   +-- SprintModule.java            # Auto-sprint
|   |   +-- render/
|   |       |-- ESPModule.java               # Entity/block highlighting
|   |       |-- FreelookModule.java          # Detached camera
|   |       +-- InventoryHUDModule.java      # Inventory overlay module
|   |-- render/uptime/
|   |   +-- ScriptModule.java               # Interface for stat-tracking modules
|   +-- settings/
|       |-- Module.java                      # Base module class + Setting inner class
|       +-- ModuleManager.java               # Singleton module registry
|
|-- mixin/
|   |-- GameRendererMixin.java               # ESP frame tick, hide hand in freelook
|   |-- FullbrightMixin.java                 # Force max gamma
|   |-- MouseAccessor.java                   # Access cursorDeltaX/Y
|   |-- KeyBindingAccessor.java              # Access timesPressed field
|   |-- FreelookCameraMixin.java             # Override camera yaw/pitch/distance
|   |-- FreelookMouseMixin.java              # Intercept mouse for freelook
|   |-- FreelookHudMixin.java                # Hide HUD during freelook
|   |-- MinecraftClientMixin.java            # Custom window title
|   |-- InventoryHudMixin.java               # Inject inventory item rendering
|   |-- imgui/
|   |   |-- GameRendererMixin.java           # ImGui frame render at RETURN
|   |   +-- MinecraftMixin.java              # ImGui init/dispose
|   +-- branding/                            # Title screen branding
|
+-- screens/gui/
    |-- clickgui/
    |   +-- TobaScreen.java                  # Main ImGui settings screen
    |-- failsafe/
    |   +-- FailsafeOverlay.java             # Red pulsing alert overlay
    |-- inventory/
    |   |-- InventoryOverlay.java            # ImGui panel/borders
    |   +-- InventoryHUDRenderer.java        # DrawContext items + player model
    |-- menu/
    |   +-- TobaScreen.java                  # Alternative menu screen
    +-- statistics/
        +-- ScriptStatisticsOverlay.java     # Script runtime stats panel
```

---

## 2. Initialization Flow

Entry point: `TobaClient.onInitializeClient()`

```
1. Register modules with ModuleManager
     ESPModule, PathfinderModule, FreelookModule, TreeCutterModule,
     SprintModule, InventoryHUDModule, ClickGUI

2. Register world render events
     END_MAIN  -> ESPRenderer.render() + PathRenderer.render()
     START_MAIN -> RotationManager.onRender()

3. Register client tick event (END_CLIENT_TICK)
     -> KeybindUtil.handleGuiKey()
     -> KeybindUtil.handleModuleKeys()
     -> PathfindingService.tick()
     -> All enabled modules -> module.onTick()
     -> RotationManager.onTick()
     -> FailsafeManager.tick()

4. Register client commands
     /goto <x> <y> <z>
     /testfailsafe

5. Load config
     TobaConfig.load()
```

ImGui is initialized separately via `imgui.MinecraftMixin`:
- `MinecraftClient.<init>()` RETURN -> `ImGuiImpl.create(windowHandle)`
- `MinecraftClient.close()` HEAD -> `ImGuiImpl.dispose()`

---

## 3. Module System

### Base Class: Module

Location: `features/settings/Module.java`

Every feature extends `Module` and provides:

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Display name |
| `description` | String | Short description |
| `category` | Category | CLIENT, MACRO, RENDER, or QOL |
| `enabled` | boolean | Current on/off state |
| `hidden` | boolean | Hide from ClickGUI (e.g. PathfinderModule) |
| `isScript` | boolean | Marks as automation (triggers failsafe checks when enabled) |
| `keyBind` | int | GLFW key code (-1 = unbound) |
| `settings` | List of Setting | Configurable values |

### Lifecycle

```java
module.toggle()          // Flip enabled state
  -> setEnabled(true)
    -> onEnable()         // Override: register overlays, start tasks
  -> setEnabled(false)
    -> onDisable()        // Override: cleanup, unregister overlays

module.onTick()          // Called every client tick while enabled
```

### ModuleManager

Singleton registry. Access any module by class:

```java
ModuleManager mm = ModuleManager.getInstance();
mm.register(new MyModule());

// Retrieve by class
ESPModule esp = mm.getModule(ESPModule.class);

// Get visible modules in a category (skips hidden)
List<Module> renders = mm.getModulesByCategory(Module.Category.RENDER);
```

### Categories

| Category | Display | Typical Use |
|----------|---------|-------------|
| CLIENT | "Client" | GUI, client settings |
| MACRO | "Macro" | Automation scripts |
| RENDER | "Render" | Visual features (ESP, HUD) |
| QOL | "QOL" | Quality-of-life (sprint, etc.) |

---

## 4. Settings System

### Setting Class: Module.Setting<T>

Located as an inner class of `Module`. Every setting has:

| Field | Description |
|-------|-------------|
| `name` | Display label |
| `value` / `defaultValue` | Current and default values |
| `type` | SettingType enum |
| `min` / `max` | Numeric range constraints |
| `icon` | Optional ItemStack for GUI display |
| `children` | Child settings (for SUB_CONFIG) |
| `modes` | List of options (for MODE) |
| `expanded` | Expand/collapse state (for SUB_CONFIG) |
| `buttonCallback` | Runnable (for BUTTON) |
| `rewarpListProvider` | Provider interface (for REWARP_LIST) |

### SettingType Reference

| Type | Java Type | GUI Widget | Notes |
|------|-----------|------------|-------|
| BOOLEAN | Boolean | Toggle switch | |
| INTEGER | Integer | Slider | Use `.range(min, max)` |
| FLOAT | Float | Slider | Use `.range(min, max)` |
| COLOR | Integer | Color picker | ARGB packed int (0xAARRGGBB) |
| STRING | String | Text input | |
| MODE | String | Dropdown | Use `.modes("A", "B", "C")` |
| BUTTON | Boolean | Click button | Use `.callback(Runnable)` |
| SUB_CONFIG | Boolean | Expandable group | Use `.child(setting)` for children |
| REWARP_LIST | Boolean | Custom list UI | Use `.rewarpProvider(provider)` |

### Creating Settings

```java
public class MyModule extends Module {
    private final Setting<Float> speed;
    private final Setting<Boolean> feature;
    private final Setting<String> mode;
    private final Setting<Integer> color;

    public MyModule() {
        super("My Module", "Does things", Category.RENDER);

        speed = addSetting(new Setting<>("Speed", 1.0f, Setting.SettingType.FLOAT));
        speed.range(0.1, 5.0);

        feature = addSetting(new Setting<>("Feature", true, Setting.SettingType.BOOLEAN));

        mode = addSetting(new Setting<>("Mode", "Fast", Setting.SettingType.MODE));
        mode.modes("Fast", "Slow", "Balanced");

        color = addSetting(new Setting<>("Color", 0xFFFF0000, Setting.SettingType.COLOR));

        // Sub-config with children
        Setting<Boolean> advanced = addSetting(
            new Setting<>("Advanced", false, Setting.SettingType.SUB_CONFIG));
        advanced.child(new Setting<>("Debug", false, Setting.SettingType.BOOLEAN));

        // Button
        addSetting(new Setting<>("Reset", false, Setting.SettingType.BUTTON)
            .callback(() -> speed.setValue(1.0f)));
    }
}
```

### RewarpListProvider

For modules that need saved coordinate lists:

```java
public interface RewarpListProvider {
    List<RewarpEntry> getRewarpEntries();
    void onSetRewarp();            // "Add current position" button click
    void onRemoveRewarp(int index); // Delete entry at index
}

public static class RewarpEntry {
    public final String name;
    public final int x, y, z;
}
```

---

## 5. Config Persistence

### TobaConfig

Location: `api/config/TobaConfig.java`
Storage: `config/toba/default.json`

```java
TobaConfig.save();                  // Save to default.json
TobaConfig.load();                  // Load from default.json
TobaConfig.saveAs("myconfig");      // Save to config/toba/myconfig.json
TobaConfig.loadFrom("myconfig");    // Load from custom config
TobaConfig.getConfigNames();        // List all saved configs
TobaConfig.deleteConfig("myconfig");
```

Auto-save triggers: GUI close, module keybind toggle.

### Serialization Rules

When adding a **new SettingType**, you MUST add cases in BOTH the serialize and deserialize switch statements in `TobaConfig.java`:

| Type | Serialize | Deserialize |
|------|-----------|-------------|
| BOOLEAN | `addProperty(key, bool)` | `getAsBoolean()` |
| INTEGER | `addProperty(key, int)` | `getAsInt()` |
| FLOAT | `addProperty(key, float)` | `getAsFloat()` |
| COLOR | `addProperty(key, int)` | `getAsInt()` |
| STRING | `addProperty(key, string)` | `getAsString()` |
| MODE | `addProperty(key, string)` | `getAsString()` |
| BUTTON | No-op | No-op |
| SUB_CONFIG | `{ "expanded": bool, "children": { ... } }` | Recursive |
| REWARP_LIST | `[ { "name", "x", "y", "z" } ]` | Clear + populate via provider |

### JSON Format Example

```json
{
  "modules": {
    "ESP": {
      "enabled": true,
      "keyBind": -1,
      "settings": {
        "Entity Settings": {
          "expanded": true,
          "children": {
            "Show Players": true,
            "Player Color": -52429
          }
        },
        "Scan Radius": 32
      }
    }
  }
}
```

---

## 6. ImGui Rendering System

### ImGuiImpl

Location: `api/imgui/ImGuiImpl.java`

### Lifecycle

```
MinecraftClient init
  -> ImGuiImpl.create(windowHandle)
    -> ImGui.createContext() + ImPlot.createContext()
    -> Load fonts (6 families x 3 sizes each)
    -> imGuiImplGlfw.init()
    -> imGuiImplGl3.init()

Every frame (imgui.GameRendererMixin at RETURN of GameRenderer.render):
  -> ImGuiImpl.beginImGuiRendering()
    -> Bind MC framebuffer
    -> Set GL viewport
    -> imGuiImplGl3.newFrame()
    -> imGuiImplGlfw.newFrame()
    -> ImGui.newFrame()
    -> Push active font

  -> Render current screen (if implements RenderInterface)
  -> Render all registered HUD overlays

  -> ImGuiImpl.endImGuiRendering()
    -> Pop font
    -> ImGui.render()
    -> imGuiImplGl3.renderDrawData()
    -> Unbind framebuffer
    -> Handle viewports (if enabled)

MinecraftClient close
  -> ImGuiImpl.dispose()
```

### Fonts

Available families: `Rubik` (default), `Roboto`, `Bebas`, `Playwrite`, `OpenSans`, `Minecraft`

Each loaded in three sizes:
- `regular`: 16px
- `bold`: 18px
- `title`: 22px

```java
ImGuiImpl.currentFontFamily = "Rubik";  // Set global font

ImFont font = ImGuiImpl.getActiveFont("regular");
ImGui.pushFont(font);
// ... draw text ...
ImGui.popFont();
```

### HUD Overlays

Overlays render every frame regardless of whether a Screen is open.

```java
ImGuiImpl.registerHudOverlay(myOverlay);    // Add
ImGuiImpl.unregisterHudOverlay(myOverlay);  // Remove
ImGuiImpl.getHudOverlays();                 // List all
```

### RenderInterface

```java
@FunctionalInterface
public interface RenderInterface {
    void render(ImGuiIO io);
}
```

Implemented by both Screen classes (ClickGUI) and HUD overlays.

### Color Format (ABGR)

ImGui uses **ABGR** packed integers, NOT ARGB:

```java
// ImGui color helper used in all overlays
private static int imColor(int r, int g, int b, int a) {
    return (a << 24) | (b << 16) | (g << 8) | r;
}
```

Minecraft uses ARGB. Convert with `ColorUtil.argbToAbgr(int)`.

### Drawing API

```java
ImDrawList draw = ImGui.getForegroundDrawList();  // On top of everything

draw.addRectFilled(x1, y1, x2, y2, abgrColor, rounding);
draw.addRect(x1, y1, x2, y2, abgrColor, rounding, flags, thickness);
draw.addText(x, y, abgrColor, "text");
draw.addLine(x1, y1, x2, y2, abgrColor, thickness);
```

Coordinates are in **raw pixel space** (not GUI-scaled).

---

## 7. Mixin Reference

All mixins registered in `src/client/resources/toba.client.mixins.json`.

### Core Mixins

| Mixin | Target | Injection | Purpose |
|-------|--------|-----------|---------|
| `GameRendererMixin` | `GameRenderer` | `render` HEAD | Call `ESPModule.onFrameUpdate()` each frame |
| | | `renderHand` HEAD (cancel) | Hide hand during freelook |
| `FullbrightMixin` | `LightmapTextureManager` | -- | Force gamma to 10.0f |
| `MinecraftClientMixin` | `MinecraftClient` | -- | Custom window title |
| `InventoryHudMixin` | `InGameHud` | `render` TAIL | Render inventory item icons + player model via DrawContext |

### Input Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| `MouseAccessor` | `Mouse` | `@Accessor` for `cursorDeltaX`, `cursorDeltaY` fields |
| `KeyBindingAccessor` | `KeyBinding` | `@Accessor` for `timesPressed` field (yarn: field_1661) |

### Freelook Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| `FreelookCameraMixin` | `Camera` | Override yaw/pitch at `update` RETURN, adjust third-person distance |
| `FreelookMouseMixin` | `Mouse` | Redirect mouse movement to freelook rotation, intercept scroll for zoom |
| `FreelookHudMixin` | `InGameHud` | Cancel entire HUD render at HEAD when freelook active |

### ImGui Mixins

| Mixin | Target | Purpose |
|-------|--------|---------|
| `imgui.GameRendererMixin` | `GameRenderer` | `render` RETURN -- runs full ImGui frame |
| `imgui.MinecraftMixin` | `MinecraftClient` | `<init>` RETURN -> ImGui create; `close` HEAD -> ImGui dispose |

### Adding a New Mixin

1. Create class in `mixin/`:
```java
@Mixin(TargetClass.class)
public class MyMixin {
    @Inject(method = "targetMethod", at = @At("TAIL"))
    private void toba$myHook(CallbackInfo ci) {
        // Your code here
    }
}
```

2. Register in `src/client/resources/toba.client.mixins.json`:
```json
{
  "client": [
    "MyMixin"
  ]
}
```

Use prefix `toba$` for injected method names to avoid conflicts with other mods.

---

## 8. Rotation Manager

Location: `api/rotation/RotationManager.java`
Singleton: `RotationManager.getInstance()`

Provides centralized, priority-based camera rotation. Multiple modules can request simultaneously -- highest priority wins.

### Priority Levels

| Priority | Value | Use Case |
|----------|-------|----------|
| LOW | 0 | Background tasks |
| NORMAL | 10 | Standard operations |
| HIGH | 20 | Combat, chopping |
| CRITICAL | 30 | Failsafe response |

Equal priority: first requester wins.

### Rotation Styles

| Style | Accel | Friction | Max Speed | Use Case |
|-------|-------|----------|-----------|----------|
| SMOOTH | 0.08 | 0.5 | 4 deg/frame | General pathfinding |
| FAST | 0.12 | 0.55 | 6 deg/frame | Combat, quick targeting |
| INSTANT | -- | -- | -- | Immediate snap |

SMOOTH and FAST use acceleration-based physics with distance scaling (0.6x near target, 1.5x far).

### API

```java
RotationManager rm = RotationManager.getInstance();

// Look at a world position
rm.request("mymodule", targetVec3d, Style.SMOOTH, Priority.NORMAL);

// Look at specific yaw/pitch angles
rm.request("mymodule", yaw, pitch, Style.FAST, Priority.HIGH);

// Release control (always call when done)
rm.release("mymodule");

// Check state
rm.isActive();
```

### Vertical Pitch Compensation

Automatically adjusts pitch based on player vertical velocity during jumps/falls for natural-looking movement.

```java
rm.setVerticalPitchCompensation(true);   // Enable (default)
rm.setVerticalPitchCompensation(false);  // Disable (for God Potion mode)
```

Max adjustment: +/-3 degrees. Smoothed with a 10-tick hold after landing.

---

## 9. Pathfinding System

### PathfindingService

Location: `api/pathfinding/PathfindingService.java`
Singleton: `PathfindingService.getInstance()`

All pathfinding is async and returns `CompletableFuture`.

```java
PathfindingService ps = PathfindingService.getInstance();

// Ground pathfinding via NavMesh
CompletableFuture<NavMeshPath> groundPath =
    ps.findNavMeshPath(start, goal, world, maxRange);

// 3D flight pathfinding (26-connected voxel grid)
CompletableFuture<NavMeshPath> flyPath =
    ps.findFlyPath(start, goal, world, maxRange);

// Try ground first, fallback to fly
CompletableFuture<HybridPathResult> hybridPath =
    ps.findHybridPath(start, goal, world, maxRange);

// Blacklist a stuck area (radius in blocks, duration in ticks)
ps.blacklistArea(center, radius, durationTicks);
ps.clearBlacklist();

// Must be called each tick (done automatically in TobaClient)
ps.tick();
```

### NavMesh Components

- **NavMeshGenerator**: Builds navigation mesh from world blocks. Handles slabs/stairs with fractional Y, configurable jump height, hazard detection (lava, void).
- **NavMeshPathfinder**: A* search through NavMesh polygons with funnel algorithm for smooth diagonal shortcuts.
- **FlyPathfinder**: 3D voxel-grid A* with 26 neighbors and 3D line-of-sight smoothing.

### NavMeshPath Result

```java
path.getWaypoints()    // List<Vec3d> smooth waypoints
path.getCorridor()     // List<NavPoly> NavMesh polygons traversed
path.getLength()       // double total path distance
path.isFound()         // boolean whether path was found
```

### PathfinderModule

Location: `features/impl/module/macro/misc/PathfinderModule.java`
Hidden from GUI -- used as an API by other modules.

```java
PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);

pf.startPathTo(blockPos, () -> { /* arrived callback */ });
pf.startFlyTo(blockPos, onArrival);
pf.startHybridTo(blockPos, onArrival);
pf.stop();
pf.isNavigating();
```

### Pathfinder Settings

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| Path Color | 0xFF00FFFF | -- | ESP path line color |
| Sprint | false | -- | Sprint while following path |
| Highlight Path Nodes | true | -- | Show small node markers |
| Max Range | 400 | 50-1000 | Maximum pathfinding distance in blocks |
| Debug NavMesh | false | -- | Visualize NavMesh polygon edges |
| God Potion Mode | false | -- | 6-block jump height, 10-block safe fall |

### Pathfinder Features

- NavMesh with slab/stair fractional Y support
- Funnel algorithm for smooth diagonal paths
- Line-of-sight waypoint skipping (look-ahead)
- Stuck detection with strafe recovery
- Blacklist system for permanently stuck areas
- Dynamic repathing when world changes
- Anti-cheat randomization (micro-pauses, speed variation)

---

## 10. ESP and Path Rendering

### ESPRenderer

Location: `api/render/ESPRenderer.java`
Singleton: `ESPRenderer.getInstance()`
Called from: `WorldRenderEvents.END_MAIN`

```java
ESPRenderer esp = ESPRenderer.getInstance();

// Entity highlight (cleared each frame automatically)
esp.addEntity(entity, tickDelta, r, g, b, alpha);

// Block highlight (persistent until next beginBlockScan)
esp.beginBlockScan();
esp.addBlock(blockPos, r, g, b, alpha);

// Generic box
esp.addBox(box, r, g, b, alpha);

// Path markers (persistent until next beginPathScan)
esp.beginPathScan();
esp.addPathWaypoint(vec3d, r, g, b, alpha);   // Small marker
esp.addPathBlock(blockPos, r, g, b, alpha);    // Block-sized
```

Colors are 0.0 - 1.0 floats. Uses `WorldRenderer.drawBox()` with buffer builders.

### PathRenderer

Location: `api/render/PathRenderer.java`
Singleton: `PathRenderer.getInstance()`
Called from: `WorldRenderEvents.END_MAIN`

Draws thin quad lines between waypoints.

```java
PathRenderer pr = PathRenderer.getInstance();
pr.setPathVec3d(waypoints);      // List<Vec3d>
pr.setActiveIndex(currentIndex); // Render from this point onward
pr.setColor(r, g, b, alpha);    // 0.0-1.0 floats
pr.clearPath();
```

---

## 11. Failsafe System

### FailsafeManager

Location: `api/failsafes/FailsafeManager.java`
Singleton: `FailsafeManager.getInstance()`

Only ticks failsafe checks when a module with `isScript = true` is currently enabled.

### Creating a Failsafe

```java
public class MyFailsafe implements FailsafeManager.Failsafe {
    @Override
    public String getName() { return "My Check"; }

    @Override
    public boolean check() {
        // Called each tick while scripts run.
        // Return true if anomaly detected.
        return someCondition;
    }

    @Override
    public void reset() {
        // Reset internal tracking state
    }

    // Optional override (default returns true)
    @Override
    public boolean isEnabled() { return true; }
}

// Register (typically during mod init)
FailsafeManager.getInstance().register(new MyFailsafe());
```

### Trigger Flow

```
1. failsafe.check() returns true
2. FailsafeWarning activated (pulsing red overlay via FailsafeOverlay)
3. ALL script modules disabled immediately
4. Chat: "FAILSAFE: [name] triggered!"
5. Player presses B to dismiss
6. If responded in time: "React normally, you are in a macro check..."
   If timeout (unanswered): "Failsafe unanswered. You will most likely get banned soon."
7. 5-second cooldown before re-checking any failsafe
```

### FailsafeOverlay

Location: `screens/gui/failsafe/FailsafeOverlay.java`

Renders on `ImGui.getForegroundDrawList()`:
- Full-screen red pulsing vignette
- Red bars at top and bottom
- Central dark red box with rounded corners
- Pulsing "FAILSAFE TRIGGERED" title (title font)
- Failsafe name (bold font)
- High-precision countdown (3 decimal places)
- "Press B to take over" instruction

### Manual Testing

Command: `/testfailsafe`
Calls `FailsafeManager.getInstance().triggerManual("Test Failsafe")`

---

## 12. Script Statistics Overlay

### ScriptModule Interface

Location: `features/render/uptime/ScriptModule.java`

Modules that track runtime stats implement:

```java
public interface ScriptModule {
    long getStartTimeMillis();  // When the module was enabled
    String[] getStatsLines();   // Formatted stat lines
}
```

`getStatsLines()` returns formatted lines with double-space separators:
```
"Mode  Melon"           // First line = mode (used as crop name in header)
"BPS  12.5"
"Crops/hr  45,000"
"Pests  2"
```

### ScriptStatisticsOverlay

Location: `screens/gui/statistics/ScriptStatisticsOverlay.java`

Right-side panel (240px wide) with three sections:

1. **Crop Stats**: module name + runtime, then all stat lines from `getStatsLines()` (skips first line which becomes the header)
2. **Skills**: farming level and progress from `TabListParser`
3. **Jacob's Contest**: rank, time remaining, total score (only shown when contest is active, detected via TabListParser)

Automatically hides when module is disabled.

Register in module onEnable:
```java
statsOverlay = new ScriptStatisticsOverlay(this); // 'this' must implement ScriptModule
ImGuiImpl.registerHudOverlay(statsOverlay);
```

---

## 13. Inventory HUD

### Architecture

The inventory HUD uses a **dual-pipeline** rendering approach because ImGui cannot render Minecraft item textures or 3D entity models.

| Component | Pipeline | Renders |
|-----------|----------|---------|
| `InventoryOverlay` | ImGui (getForegroundDrawList) | Panel background, borders, separator, hotbar selection |
| `InventoryHUDRenderer` | DrawContext (InGameHud mixin) | Item icons, stack counts, 3D player model |

### Rendering Order

```
InGameHud.render() TAIL
  -> InventoryHUDRenderer.render(context, tickCounter)
    -> Queues items to GuiRenderState via context.drawItem()
    -> Queues player model via InventoryScreen.drawEntity()

guiRenderer.render()
  -> Flushes all DrawContext items to framebuffer

GameRenderer.render() RETURN
  -> ImGui renders
    -> InventoryOverlay draws panel background + borders on top
```

Items render BEHIND the ImGui panel. Panel uses low alpha (140/255) so items show through.

### Coordinate System Difference

**ImGui** uses raw pixel coordinates (e.g. 1920x1080).
**DrawContext** uses GUI-scaled coordinates (e.g. 480x270 at GUI scale 4).

InventoryHUDRenderer divides all ImGui positions by `client.getWindow().getScaleFactor()` before passing to DrawContext.

### Communication Between Pipelines

InventoryOverlay (ImGui phase) syncs layout data to InventoryHUDRenderer each frame via setters:
- `setSlotPositions(gridX, gridY, slotSize, slotGap, hotbarGap)`
- `setPanelLayout(panelX, panelY, invSectionW, panelH, headerHeight, previewW)`
- `setEnabled(true)`, `setScale()`, `setOpacity()`, `setShowPlayerPreview()`

Since ImGui runs AFTER InGameHud, there is a 1-frame delay on first enable (guarded by `slotSize <= 0` check).

### Layout Constants (InventoryOverlay)

| Constant | Value | Description |
|----------|-------|-------------|
| MARGIN | 8 | Distance from screen edge |
| PADDING | 8 | Internal panel padding |
| SLOT_SIZE | 26 | Slot square size in pixels |
| SLOT_GAP | 2 | Gap between slots |
| HEADER_HEIGHT | 8 | Top padding above grid |
| HOTBAR_GAP | 6 | Gap between main inv and hotbar |
| PREVIEW_WIDTH | 100 | Player preview section width |

### Module Settings

| Setting | Default | Range |
|---------|---------|-------|
| Scale | 1.0 | 0.5-2.0 |
| Opacity | 0.85 | 0.1-1.0 |
| Player Preview | true | -- |
| Show Armor Slots | true | -- |

---

## 14. Utility Classes

### TobaChat

```java
TobaChat.send("message");                           // Gray text with [Toba] prefix
TobaChat.sendColored("message", Formatting.RED);    // Colored text
TobaChat.sendDebug("message");                      // Yellow, only shows if debug enabled
```

### KeybindUtil

```java
// Called each tick from TobaClient
KeybindUtil.handleModuleKeys(client);   // Check all module keybinds, toggle on press
KeybindUtil.handleGuiKey(keyBinding);   // Check GUI toggle key
```

Config auto-saves on keybind toggle.

### KeyBindingAccessor (Attack Key Simulation)

**Critical for simulating attacks/clicks:**

```java
KeyBinding attackKey = client.options.attackKey;
attackKey.setPressed(true);
((KeyBindingAccessor) attackKey).setTimesPressed(1);
```

`setPressed(true)` alone does NOT trigger attacks. You MUST also set `timesPressed` to exactly `1` each tick. Do NOT increment -- incrementing causes burst clicking (multiple attacks per tick).

### ColorUtil

```java
ColorUtil.argbToAbgr(0xFFFF0000);   // Minecraft ARGB -> ImGui ABGR
ColorUtil.lighten(color, 20);        // Lighten a color by amount
```

### TabListParser

Singleton: `TabListParser.getInstance()`

Parses Hypixel Skyblock tab list:
- `getFarmingLevel()`, `getFarmingLevelProgress()`
- `getJacobRank()`, `getJacobTimeRemaining()`, `getJacobScore()`, `getJacobCrop()`
- Location, purse, bits, server info

### BazaarPricingService

Fetches Hypixel Bazaar API prices every 60 seconds. Used for profit calculations in macro stats.

### WorldScanner

Location: `api/service/WorldScanner.java`

Scan blocks in a radius by predicate or block type. Supports sorted scans, nearest lookup, line-of-sight checks.

### BlockInteractionService

Location: `api/service/BlockInteractionService.java`

Central block breaking loop with rotation management and timeout handling. Fires EventBus events: `BLOCK_STARTED`, `BLOCK_BROKEN`.

### InventoryService

Location: `api/service/InventoryService.java`

Hotbar selection, slot swapping, and item drop queue. Uses `clickSlot` for proper packet-based inventory interaction.

### EventBus

Location: `api/service/EventBus.java`

Publish/subscribe system for game events. Event types include block, health, inventory, path, and module events. Call `tick()` to poll health and inventory changes.

**Note**: EventBus.tick(), InventoryService.tick(), and BlockInteractionService.tick() are NOT wired into the TobaClient tick loop by default. Add them if your module needs them.

---

## 15. Client Commands

Registered via `ClientCommandRegistrationCallback.EVENT` in `TobaClient`.

| Command | Usage | Description |
|---------|-------|-------------|
| `/goto` | `/goto <x> <y> <z>` | Start pathfinding to coordinates |
| `/testfailsafe` | `/testfailsafe` | Trigger a test failsafe warning overlay |

---

## 16. Built-in Modules Reference

### ClickGUI (CLIENT)

- **Keybind**: RIGHT_SHIFT
- **Behavior**: Opens GUI screen then immediately disables itself
- **Settings**:
  - Mode: "ClickGUI" or "Menu" (two different screen implementations)
  - Font: Rubik, Roboto, Bebas, Playwrite, OpenSans, Minecraft
  - Color Settings (SUB_CONFIG): Primary color, Background, Text color
  - Advanced (SUB_CONFIG): allow chat debug, allow custom title, title text

### ESP (RENDER)

- **Purpose**: Highlight entities and blocks through walls with colored boxes
- **Settings**:
  - Entity Settings: Show Players/Hostiles/Passives/Custom + colors for each
  - Name Filter: filter entities by name substring
  - Block Settings: Show Blocks, Scan Radius (8-64), toggles for each ore type
  - Color Settings: individual color per ore type
  - Misc: Transparency (10-100%)

### Freelook (RENDER)

- **Keybind**: LEFT_ALT
- **Purpose**: Independent camera rotation while player body stays still
- **Settings**: Default Distance (1-20 blocks)
- **Features**: Scroll wheel zoom, hides HUD and hand, forces third-person camera

### Sprint (QOL)

- **Purpose**: Auto-sprint every tick
- **Behavior**: `player.setSprinting(true)` in onTick

### Pathfinder (MACRO, hidden)

- **Purpose**: API module for ground/fly/hybrid pathfinding
- See [Pathfinding System](#9-pathfinding-system) for full details

### Tree Cutter (MACRO, script)

- **Purpose**: Automatic tree detection, pathfinding, and chopping
- **States**: SEARCHING -> PATHING -> EQUIPPING -> CHOPPING -> CLEARING_LEAVES
- **Settings**: Search Radius (8-64), Max Tree Height (4-20), Highlight/Path colors, Sprint, Break Leaves
- **Features**: Bottom-to-top chopping, leaf clearing for line-of-sight, ESP highlighting, stats tracking
- **Implements**: ScriptModule for statistics overlay

### Inventory HUD (RENDER)

- **Purpose**: Always-on inventory display with 3D player preview
- See [Inventory HUD](#13-inventory-hud) for full architecture details

---

## 17. Yarn Mapping Gotchas

Common mistakes when using Yarn mappings for 1.21.11:

| Wrong | Correct | Notes |
|-------|---------|-------|
| `player.getPos()` | `player.getEntityPos()` | Position getter renamed |
| `handler.getPlayerListEntries()` | `handler.getListedPlayerListEntries()` | Tab list accessor |
| `player.selectedSlot` | `player.getInventory().getSelectedSlot()` | Field is private |
| `getTickDelta(boolean)` | `getTickProgress(boolean)` | Tick interpolation renamed |
| `getArmorStack(int)` | `getEquippedStack(EquipmentSlot)` | Method removed, use EquipmentSlot enum |
| `EntityRenderDispatcher` | `EntityRenderManager` | Class renamed |
| `com.mojang.blaze3d.ProjectionType` | `com.mojang.blaze3d.systems.ProjectionType` | Package moved |

### Inner Class References

Must use full qualification:
```java
Module.Setting.RewarpListProvider   // NOT just RewarpListProvider
Module.Setting.RewarpEntry
Module.Setting.SettingType
```

### KeyBinding timesPressed Field

Yarn name: `timesPressed` (obfuscated: `field_1661`)
Access via `KeyBindingAccessor` mixin.

---

## 18. Common Patterns and Recipes

### Creating a New Module

```java
public class MyModule extends Module {
    private final Setting<Boolean> feature;

    public MyModule() {
        super("My Module", "Does things", Category.RENDER);
        feature = addSetting(new Setting<>("Feature", true, Setting.SettingType.BOOLEAN));
    }

    @Override protected void onEnable() { /* setup */ }
    @Override protected void onDisable() { /* cleanup */ }

    @Override
    public void onTick() {
        if (feature.getValue()) {
            // Do stuff each tick
        }
    }
}
```

Register in `TobaClient.onInitializeClient()`:
```java
mm.register(new MyModule());
```

### Creating an ImGui HUD Overlay

```java
public class MyOverlay implements RenderInterface {
    @Override
    public void render(ImGuiIO io) {
        ImDrawList draw = ImGui.getForegroundDrawList();
        draw.addRectFilled(10, 10, 200, 50, imColor(20, 20, 30, 200), 4f);
        draw.addText(15, 15, imColor(255, 255, 255, 255), "Hello");
    }

    private static int imColor(int r, int g, int b, int a) {
        return (a << 24) | (b << 16) | (g << 8) | r;
    }
}
```

Register/unregister in module lifecycle:
```java
@Override
protected void onEnable() {
    overlay = new MyOverlay();
    ImGuiImpl.registerHudOverlay(overlay);
}

@Override
protected void onDisable() {
    if (overlay != null) {
        ImGuiImpl.unregisterHudOverlay(overlay);
        overlay = null;
    }
}
```

### Creating a Script Module with Stats

```java
public class MyMacro extends Module implements ScriptModule {
    private long startTime;
    private ScriptStatisticsOverlay statsOverlay;

    public MyMacro() {
        super("My Macro", "Automates stuff", Category.MACRO);
        setIsScript(true);  // Enables failsafe checking
    }

    @Override
    protected void onEnable() {
        startTime = System.currentTimeMillis();
        statsOverlay = new ScriptStatisticsOverlay(this);
        ImGuiImpl.registerHudOverlay(statsOverlay);
    }

    @Override
    protected void onDisable() {
        if (statsOverlay != null) {
            ImGuiImpl.unregisterHudOverlay(statsOverlay);
            statsOverlay = null;
        }
    }

    @Override public long getStartTimeMillis() { return startTime; }

    @Override
    public String[] getStatsLines() {
        return new String[] {
            "Mode  My Mode",      // First line = mode (becomes header)
            "Items/hr  1,234",    // Double space between label and value
            "Profit  500k"
        };
    }
}
```

### Using Pathfinding

```java
PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);

pf.startPathTo(targetPos, () -> {
    TobaChat.send("Arrived!");
    doNextTask();
});

if (pf.isNavigating()) { /* still moving */ }
pf.stop();  // Cancel
```

### Using Rotation

```java
RotationManager rm = RotationManager.getInstance();

// Look at a block center
Vec3d target = Vec3d.ofCenter(blockPos);
rm.request("mymodule", target, RotationManager.Style.SMOOTH, RotationManager.Priority.NORMAL);

// Always release when done
rm.release("mymodule");
```

### Simulating Attack Key

```java
KeyBinding attackKey = client.options.attackKey;
attackKey.setPressed(true);
((KeyBindingAccessor) attackKey).setTimesPressed(1);  // Exactly 1, never increment
```

### Adding a New SettingType

1. Add enum value in `Module.Setting.SettingType`
2. Add serialize case in `TobaConfig.serializeSettings()`
3. Add deserialize case in `TobaConfig.deserializeSettings()`
4. Add GUI rendering in `TobaScreen` (ClickGUI)

### Adding a New Failsafe

```java
FailsafeManager.getInstance().register(new FailsafeManager.Failsafe() {
    @Override public String getName() { return "Cursor Teleport"; }
    @Override public boolean check() { return detectTeleport(); }
    @Override public void reset() { clearState(); }
});
```
