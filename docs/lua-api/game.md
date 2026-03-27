# Game API

The `game` table provides general game/client information.

## Functions

### `game.fps()`

Returns the current frames per second as an integer.

```lua
function onRender()
    if imgui.begin("FPS") then
        imgui.text("FPS: " .. game.fps())
    end
    imgui.finish()
end
```

### `game.windowWidth()`

Returns the scaled window width in pixels (integer). This accounts for GUI scale settings.

### `game.windowHeight()`

Returns the scaled window height in pixels (integer).

```lua
-- Center a window on screen
local w, h = 200, 100
imgui.setNextWindowPos(
    (game.windowWidth() - w) / 2,
    (game.windowHeight() - h) / 2
)
imgui.setNextWindowSize(w, h)
```

### `game.serverAddress()`

Returns the current server address as a string, or `nil` if in singleplayer or not connected.

```lua
function onEnable()
    local server = game.serverAddress()
    if server then
        chat.info("Connected to: " .. server)
    else
        chat.info("Playing singleplayer")
    end
end
```

## Global Time Utilities

These are standalone global functions (not in the `game` table) that provide system time. They replace the unavailable `os.time()` and `os.clock()`.

### `millis()`
Returns system time in milliseconds (long integer). Useful for timing and cooldowns.

```lua
local start = millis()
-- ... some work ...
local elapsed = millis() - start
chat.info("Took " .. elapsed .. "ms")
```

### `seconds()`
Returns system time in seconds (double). Useful for animations and elapsed time.

```lua
local started = seconds()

function onRender()
    local uptime = seconds() - started
    imgui.text(string.format("Uptime: %.1fs", uptime))
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `game.fps()` | number | Current FPS |
| `game.windowWidth()` | number | Scaled window width |
| `game.windowHeight()` | number | Scaled window height |
| `game.serverAddress()` | string or nil | Server IP/address |
| `millis()` | number | System time in milliseconds (global) |
| `seconds()` | number | System time in seconds (global) |
