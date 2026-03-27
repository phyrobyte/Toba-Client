# Scoreboard API

The `scoreboard` table reads the sidebar scoreboard. Useful for parsing server-specific data (SkyBlock objective info, timers, event data).

All functions return `nil` if the world is not loaded or no sidebar objective exists.

## Functions

### `scoreboard.getTitle()`
Returns the sidebar scoreboard title as a string.

```lua
local title = scoreboard.getTitle()
if title then
    chat.info("Scoreboard: " .. title)
end
```

### `scoreboard.getLines()`
Returns all sidebar lines as a Lua table (array of strings), ordered top-to-bottom. Color codes are stripped.

```lua
local lines = scoreboard.getLines()
if lines then
    for i = 1, #lines do
        chat.info(i .. ": " .. lines[i])
    end
end
```

### `scoreboard.getLine(index)`
Returns a single scoreboard line by index (1-based, top-to-bottom). Color codes are stripped.

| Parameter | Type | Description |
|-----------|------|-------------|
| `index` | integer | Line number (1 = top line) |

```lua
-- Get the 3rd line from the top
local line = scoreboard.getLine(3)
if line then
    chat.info("Line 3: " .. line)
end
```

## Example: SkyBlock Event Detector

```lua
name = "EventDetector"
description = "Alerts when a SkyBlock event appears on scoreboard"
category = "MISC"

local lastEvent = ""

function onTick()
    local lines = scoreboard.getLines()
    if not lines then return end

    for i = 1, #lines do
        if lines[i]:find("Event:") then
            if lines[i] ~= lastEvent then
                lastEvent = lines[i]
                chat.info("Detected: " .. lastEvent)
            end
            return
        end
    end
    lastEvent = ""
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `getTitle()` | string or nil | Sidebar title |
| `getLines()` | table or nil | All lines (1-indexed array) |
| `getLine(index)` | string or nil | Single line by index |
