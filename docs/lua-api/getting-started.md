# Getting Started

## Installation

No additional setup is required. The Lua scripting engine is built into Toba Client.

## Script Location

Place your `.lua` files in:

```
.minecraft/config/toba/scripts/
```

The directory is created automatically on first launch. You can also open it from the in-game GUI by navigating to the **ScriptsFolder** module and clicking **"open folder"**.

## Loading Scripts

Scripts are loaded in three ways:

1. **On startup** -- All `.lua` files in the scripts directory are loaded when the game starts
2. **Auto-load** -- New `.lua` files dropped into the directory while the game is running are detected and loaded automatically
3. **Manual reload** -- Click **"refresh/reload scripts"** in the ScriptsFolder module to unload all scripts and reload them from disk

## Your First Script

Create a file called `hello.lua` in your scripts directory:

```lua
name        = "HelloWorld"
description = "My first Toba script"
category    = "MISC"

function onEnable()
    chat.info("Hello from Lua!")
end

function onDisable()
    chat.info("Goodbye from Lua!")
end

function onTick()
    -- runs every client tick (~20 times per second)
end
```

The script will appear as a toggleable module named **HelloWorld** in the MISC category of the Click GUI.

## Error Handling

If a script fails to compile or throws an error during loading, it will **not** be registered as a module. Errors are logged in two places:

1. **Launcher console** (`stderr`) -- always visible, even before the player joins a world
2. **In-game chat** -- visible once the player is in-game (via `TobaChat`)

If a callback (`onTick`, `onEnable`, etc.) throws a runtime error, the error is displayed in chat but the module stays registered. The instruction limit (1,000,000 per callback) prevents infinite loops from freezing the game.

## Security Sandbox

Scripts run in a sandboxed Lua environment. The following standard libraries are **not** available:

| Blocked Library | Reason |
|-----------------|--------|
| `os` | Prevents command execution (`os.execute`, `os.remove`, etc.) |
| `io` | Prevents file system access (`io.open`, `io.read`, etc.) |
| `luajava` | Prevents arbitrary Java class loading and reflection |
| `require()` | Removed -- scripts must be self-contained (no external modules) |

Available standard libraries: `base`, `bit32`, `table`, `string`, `coroutine`, `math`.
