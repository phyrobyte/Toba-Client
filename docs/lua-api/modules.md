# Modules API

The `modules` table lets you query and control other Toba modules from your scripts.

## Functions

### `modules.get(name)`

Returns a module wrapper table by name (case-insensitive), or `nil` if not found.

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | string | Module name (case-insensitive) |

```lua
local esp = modules.get("ESP")
if esp then
    chat.info("ESP is " .. (esp.isEnabled() and "ON" or "OFF"))
end
```

### `modules.list()`

Returns a Lua table (array) of all non-hidden module names.

```lua
local names = modules.list()
for i = 1, #names do
    chat.info(names[i])
end
```

## Module Wrapper

The table returned by `modules.get()` exposes the following:

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `name` | string | Module display name |
| `description` | string | Module description |

### Methods

#### `isEnabled()`
Returns `true` if the module is currently enabled.

```lua
local esp = modules.get("ESP")
if esp and esp.isEnabled() then
    chat.info("ESP is active")
end
```

#### `toggle()`
Toggles the module on/off. Executes on the main thread.

```lua
local esp = modules.get("ESP")
if esp then esp.toggle() end
```

#### `enable()`
Enables the module. No effect if already enabled. Executes on the main thread.

```lua
modules.get("ESP").enable()
```

#### `disable()`
Disables the module. No effect if already disabled. Executes on the main thread.

```lua
modules.get("ESP").disable()
```

#### `getSetting(name)`
Returns the current value of a module setting by name. Returns `nil` if the setting doesn't exist.

```lua
local esp = modules.get("ESP")
local range = esp.getSetting("Range")
chat.info("ESP Range: " .. tostring(range))
```

#### `setSetting(name, value)`
Sets a module setting value. The value type must match the setting type (boolean, number, string).

```lua
local esp = modules.get("ESP")
esp.setSetting("Range", 64)
esp.setSetting("ShowPlayers", true)
```

#### `getSettings()`
Returns a table of all settings for the module. Each entry has:

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Setting name |
| `type` | string | Setting type (e.g. `"BOOLEAN"`, `"FLOAT"`, `"INTEGER"`) |
| `value` | any | Current value |

```lua
local esp = modules.get("ESP")
local settings = esp.getSettings()
for i = 1, #settings do
    chat.info(settings[i].name .. " = " .. tostring(settings[i].value))
end
```

## Protected Modules

The following modules cannot be toggled or modified by scripts (read-only access only):

- `failsafe` / `failsafes`
- `antifailsafe`
- `auth`
- `autoupdater`

Attempting to call `toggle()`, `enable()`, `disable()`, or `setSetting()` on a protected module will throw a Lua error.

## Full Reference

| Function / Property | Returns | Description |
|---------------------|---------|-------------|
| `modules.get(name)` | table or nil | Get module by name |
| `modules.list()` | table | Array of module names |
| `wrapper.name` | string | Module name |
| `wrapper.description` | string | Module description |
| `wrapper.isEnabled()` | boolean | Check if enabled |
| `wrapper.toggle()` | nil | Toggle on/off |
| `wrapper.enable()` | nil | Enable module |
| `wrapper.disable()` | nil | Disable module |
| `wrapper.getSetting(name)` | any or nil | Get setting value |
| `wrapper.setSetting(name, value)` | nil | Set setting value |
| `wrapper.getSettings()` | table | List all settings |
