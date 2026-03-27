# Settings API

The `settings` table lets scripts define custom settings that appear in the Toba click GUI. Users can adjust these values just like built-in module settings. Settings are saved/loaded automatically via the config system.

## Defining Settings

Settings must be defined at the **top level** of your script (not inside callbacks). They are registered when the script loads.

```lua
name = "My Macro"
description = "A custom farming macro"
category = "MACRO"

-- Define settings (runs once at load time)
settings.addBoolean("Auto Sprint", true)
settings.addFloat("Speed", 1.5, 0.1, 5.0)
settings.addInteger("Delay", 200, 0, 2000)
settings.addMode("Crop", "Wheat", {"Wheat", "Carrot", "Potato", "Melon"})
settings.addColor("ESP Color", 0xFF00FF00)
settings.addString("Target", "Zombie")
```

## Setting Types

### `settings.addBoolean(name, default)`
A toggle switch. Default is `false` if omitted.

### `settings.addFloat(name, default, min, max)`
A floating-point slider. Min defaults to 0, max to 100.

### `settings.addInteger(name, default, min, max)`
An integer slider. Min defaults to 0, max to 100.

### `settings.addMode(name, default, options)`
A dropdown selector. `options` is a Lua table of strings.

### `settings.addColor(name, default)`
An ARGB color picker. Default is white (`0xFFFFFFFF`).

### `settings.addString(name, default)`
A text input field. Default is empty string.

## Reading Settings

### `settings.get(name)`
Returns the current value of a setting. Call this in `onTick()` or other callbacks.

```lua
function onTick()
    local speed = settings.get("Speed")
    local crop = settings.get("Crop")
    local sprint = settings.get("Auto Sprint")

    if sprint then
        movement.sprint(true)
    end
end
```

## Writing Settings

### `settings.set(name, value)`
Programmatically change a setting value.

```lua
settings.set("Speed", 2.5)
settings.set("Crop", "Carrot")
```

## Full Example

```lua
name = "Smart Farmer"
description = "Farms crops with configurable speed"
category = "MACRO"

settings.addMode("Crop", "Wheat", {"Wheat", "Carrot", "Potato"})
settings.addFloat("Walk Speed", 1.0, 0.5, 3.0)
settings.addBoolean("Auto Replant", true)
settings.addInteger("Replant Delay", 100, 0, 500)

function onEnable()
    chat.info("Smart Farmer started - crop: " .. settings.get("Crop"))
end

function onTick()
    local speed = settings.get("Walk Speed")
    -- Use speed for movement logic...
end

function onDisable()
    movement.stopAll()
    chat.info("Smart Farmer stopped")
end
```
