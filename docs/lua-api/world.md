# World API

The `world` table provides read-only access to the client world. All functions return `nil` if the world is not loaded.

## Functions

### `world.time()`

Returns the current world time of day as a long integer (0-24000).

| Time | Value |
|------|-------|
| Sunrise | 0 |
| Noon | 6000 |
| Sunset | 12000 |
| Midnight | 18000 |

```lua
function onTick()
    local t = world.time()
    if t and t >= 12000 and t <= 13000 then
        chat.info("The sun is setting!")
    end
end
```

### `world.getBlock(x, y, z)`

Returns the block type at the given coordinates as a string (e.g., `"Block{minecraft:stone}"`).

| Parameter | Type | Description |
|-----------|------|-------------|
| `x` | integer | Block X coordinate |
| `y` | integer | Block Y coordinate |
| `z` | integer | Block Z coordinate |

```lua
function onTick()
    local x = math.floor(player.x())
    local y = math.floor(player.y()) - 1  -- block below feet
    local z = math.floor(player.z())
    local block = world.getBlock(x, y, z)
    if block then
        -- check what you're standing on
    end
end
```

### `world.getBlockName(x, y, z)`

Returns the human-readable block name at the given coordinates (e.g. `"Stone"`, `"Oak Planks"`).

```lua
local name = world.getBlockName(0, 64, 0)
chat.info("Block: " .. (name or "unknown"))
```

### `world.isAir(x, y, z)`

Returns `true` if the block at the given position is air.

### `world.isSolid(x, y, z)`

Returns `true` if the block at the given position is solid (not air and blocks movement).

```lua
-- Check if the block ahead is walkable
local bx = math.floor(player.x())
local by = math.floor(player.y())
local bz = math.floor(player.z()) + 1
if world.isSolid(bx, by, bz) then
    chat.info("Wall ahead!")
end
```

### `world.isLoaded(x, y, z)`

Returns `true` if the chunk containing the given position is loaded.

### `world.getBiome(x, y, z)`

Returns the biome identifier at the given position (e.g. `"minecraft:plains"`). Returns `"unknown"` on error.

### `world.getDifficulty()`

Returns the world difficulty as a string (e.g. `"easy"`, `"normal"`, `"hard"`).

### `world.isRaining()`

Returns `true` if it is currently raining in the world.

### `world.isThundering()`

Returns `true` if there is a thunderstorm.

```lua
function onTick()
    if world.isThundering() then
        chat.error("Thunderstorm! Take cover!")
    end
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `world.time()` | number | World time of day (0-24000) |
| `world.getBlock(x, y, z)` | string | Block type at position |
| `world.getBlockName(x, y, z)` | string | Block display name |
| `world.isAir(x, y, z)` | boolean | Block is air |
| `world.isSolid(x, y, z)` | boolean | Block is solid |
| `world.isLoaded(x, y, z)` | boolean | Chunk is loaded |
| `world.getBiome(x, y, z)` | string | Biome identifier |
| `world.getDifficulty()` | string | World difficulty |
| `world.isRaining()` | boolean | Currently raining |
| `world.isThundering()` | boolean | Currently thundering |
