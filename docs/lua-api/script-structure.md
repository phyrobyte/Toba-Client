# Script Structure

Every Toba Lua script follows a simple structure: **metadata** at the top, followed by **callback functions**.

## Metadata

Set these global variables at the top of your script to control how it appears in the GUI:

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `name` | string | filename (minus `.lua`) | Display name in the module list |
| `description` | string | `""` | Tooltip description |
| `category` | string | `"MISC"` | GUI category tab |

### Available Categories

| Category | Description |
|----------|-------------|
| `COMBAT` | Combat-related modules |
| `MACRO` | Automation macros |
| `RENDER` | Visual/rendering modules |
| `CLIENT` | Client utility modules |
| `SCRIPT` | Scripting modules |
| `MISC` | Miscellaneous |

```lua
name        = "MyModule"
description = "Does something cool"
category    = "RENDER"
```

## Callback Functions

Define any of these global functions to hook into the module lifecycle:

### `onEnable()`

Called when the module is toggled **on** (via Click GUI, keybind, or another script).

```lua
function onEnable()
    chat.info("Module enabled!")
end
```

### `onDisable()`

Called when the module is toggled **off**.

```lua
function onDisable()
    chat.info("Module disabled!")
end
```

### `onTick()`

Called every client tick (~20 times per second) while the module is enabled. Use this for game logic, checks, and automation.

```lua
function onTick()
    if player.health() < 5 then
        chat.error("Low health warning!")
    end
end
```

### `onChat(message)`

Called whenever a chat message is received while the module is enabled. The `message` parameter is the raw message string.

```lua
function onChat(message)
    if message:find("You earned") then
        chat.info("Detected earnings: " .. message)
    end
end
```

### `onRender()`

Called every frame while the module is enabled. Use this **exclusively** for ImGui rendering. If you define `onRender`, an ImGui overlay is automatically registered/unregistered when the module is toggled.

```lua
function onRender()
    imgui.setNextWindowPos(10, 100)
    imgui.setNextWindowSize(200, 80)
    if imgui.begin("My Overlay", imgui.Flags.NoResize) then
        imgui.text("Hello from Lua!")
    end
    imgui.finish()
end
```

## Lifecycle Diagram

```
Script file loaded
        |
    Metadata read (name, description, category)
    Callbacks registered (onEnable, onDisable, onTick, onRender, onChat)
        |
    Module appears in Click GUI
        |
   [User toggles ON]
        |
    onEnable() called
    onRender overlay registered (if defined)
        |
    onTick() called every tick
    onRender() called every frame
    onChat(message) called on chat messages
        |
   [User toggles OFF]
        |
    onDisable() called
    onRender overlay unregistered
```

## Script-Scoped State

All variables declared in your script are scoped to that script's Lua environment. Multiple scripts do not share state.

```lua
local counter = 0   -- private to this script

function onTick()
    counter = counter + 1
end

function onRender()
    if imgui.begin("Counter") then
        imgui.text("Ticks: " .. counter)
    end
    imgui.finish()
end
```
