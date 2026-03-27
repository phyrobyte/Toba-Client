# Chat API

The `chat` table lets you send messages to the local chat HUD and to the server. All calls are dispatched to the main thread automatically.

## Functions

### `chat.send(message)`

Sends a chat message to the server (as if the player typed it). This is visible to other players.

| Parameter | Type | Description |
|-----------|------|-------------|
| `message` | string | The message to send |

```lua
chat.send("Hello everyone!")
```

### `chat.command(command)`

Sends a command to the server (without the leading `/`). Equivalent to typing `/<command>` in chat.

| Parameter | Type | Description |
|-----------|------|-------------|
| `command` | string | The command to run (without `/` prefix) |

```lua
chat.command("msg friend Hey!")  -- runs /msg friend Hey!
chat.command("warp hub")         -- runs /warp hub
```

### `chat.info(message)`

Displays an informational message in the local chat HUD. Only you can see it. Prefixed with the Toba tag.

| Parameter | Type | Description |
|-----------|------|-------------|
| `message` | string | The info message to display |

```lua
chat.info("Module loaded successfully")
```

### `chat.error(message)`

Displays an error message in the local chat HUD (typically red). Only you can see it. Prefixed with the Toba tag.

| Parameter | Type | Description |
|-----------|------|-------------|
| `message` | string | The error message to display |

```lua
chat.error("Something went wrong!")
```

## Full Reference

| Function | Visible to | Description |
|----------|------------|-------------|
| `chat.send(msg)` | Everyone | Sends a public chat message |
| `chat.command(cmd)` | Server | Sends a server command (no `/` prefix) |
| `chat.info(msg)` | You only | Displays a local info message |
| `chat.error(msg)` | You only | Displays a local error message |
