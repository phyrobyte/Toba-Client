# ESP API

The `esp` table lets scripts render 3D boxes in the world — highlight blocks, entities, or arbitrary regions.

## Functions

### `esp.addBlock(x, y, z, r, g, b, a)`
Highlight a block position. Color components are 0-1 floats. These persist until `clear()` is called.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `x` | integer | required | Block X |
| `y` | integer | required | Block Y |
| `z` | integer | required | Block Z |
| `r` | number | 1 | Red (0-1) |
| `g` | number | 0 | Green (0-1) |
| `b` | number | 0 | Blue (0-1) |
| `a` | number | 0.4 | Alpha (0-1) |

```lua
-- Highlight the block below your feet in red
local bx = math.floor(player.x())
local by = math.floor(player.y()) - 1
local bz = math.floor(player.z())
esp.addBlock(bx, by, bz, 1, 0, 0, 0.5)
```

### `esp.addEntity(entityId, r, g, b, a)`
Highlight an entity. Must be called every render frame (in `onRender()`), as entity positions change.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `entityId` | integer | required | Entity ID |
| `r` | number | 1 | Red (0-1) |
| `g` | number | 0 | Green (0-1) |
| `b` | number | 0 | Blue (0-1) |
| `a` | number | 0.4 | Alpha (0-1) |

```lua
function onRender()
    local mobs = entities.getMobs(32)
    for _, mob in ipairs(mobs) do
        esp.addEntity(mob.id, 1, 0.2, 0.2, 0.4)
    end
end
```

### `esp.addBox(x1, y1, z1, x2, y2, z2, r, g, b, a)`
Draw an arbitrary axis-aligned box between two corners.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `x1, y1, z1` | number | required | First corner |
| `x2, y2, z2` | number | required | Second corner |
| `r` | number | 1 | Red (0-1) |
| `g` | number | 0 | Green (0-1) |
| `b` | number | 0 | Blue (0-1) |
| `a` | number | 0.4 | Alpha (0-1) |

```lua
-- Draw a 5x5x5 green zone
esp.addBox(100, 64, 200, 105, 69, 205, 0, 1, 0, 0.3)
```

### `esp.clear()`
Remove all block highlights. Call this before adding a fresh set of blocks.

```lua
function onTick()
    esp.clear()
    -- Re-add current highlights
    local bx = math.floor(player.x())
    local bz = math.floor(player.z())
    for dx = -2, 2 do
        for dz = -2, 2 do
            esp.addBlock(bx + dx, 64, bz + dz, 0, 0.5, 1, 0.3)
        end
    end
end
```

## Full Reference

| Function | Description |
|----------|-------------|
| `addBlock(x, y, z, r, g, b, a)` | Highlight a block position |
| `addEntity(entityId, r, g, b, a)` | Highlight an entity (call per frame) |
| `addBox(x1, y1, z1, x2, y2, z2, r, g, b, a)` | Draw arbitrary box |
| `clear()` | Remove all block highlights |
