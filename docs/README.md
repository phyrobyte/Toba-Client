# Toba Client Documentation

Toba is a Minecraft 1.21.11 Fabric client mod with a built-in Lua scripting engine that lets you create custom modules without writing Java.

## Features

- **Lua Scripting** -- Write custom modules in Lua with access to player data, world state, chat, and ImGui rendering
- **Sandboxed Runtime** -- Scripts run in a secure sandbox with no access to the file system, network, or Java reflection
- **Hot Reload** -- Drop `.lua` files into the scripts folder and they load automatically; use the in-game reload button to refresh
- **ImGui Overlays** -- Render custom HUD elements using the Dear ImGui bindings
- **Module Integration** -- Query and toggle any Toba module from your scripts

## Quick Links

| Topic | Description |
|-------|-------------|
| [Getting Started](lua-api/getting-started.md) | Installation, file locations, first script |
| [Script Structure](lua-api/script-structure.md) | Metadata, callbacks, lifecycle |
| [Player API](lua-api/player.md) | Position, health, inventory |
| [World API](lua-api/world.md) | Blocks, weather, time |
| [Game API](lua-api/game.md) | FPS, window, server info |
| [Chat API](lua-api/chat.md) | Send messages, display info/errors |
| [Modules API](lua-api/modules.md) | List, query, toggle modules |
| [ImGui API](lua-api/imgui.md) | Render custom UI overlays |
| [Examples](lua-api/examples.md) | Full working script examples |
