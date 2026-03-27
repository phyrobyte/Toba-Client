# Entities API

The `entities` table lets scripts scan for nearby entities (players, mobs, animals, etc.).

## Entity Object

All entity functions return tables with these fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | number | Unique entity ID (use with action/rotation APIs) |
| `name` | string | Entity display name |
| `x`, `y`, `z` | number | Position coordinates |
| `type` | string | `"player"`, `"hostile"`, `"passive"`, `"living"`, or `"other"` |
| `health` | number | Current HP (living entities only) |
| `maxHealth` | number | Max HP (living entities only) |
| `distance` | number | Distance from player |

## Functions

### `entities.getAll(maxDist)`
Returns all entities within range. Default range: 128 blocks.

### `entities.getPlayers(maxDist)`
Returns only other players (excludes yourself).

### `entities.getMobs(maxDist)`
Returns only hostile mobs (zombies, skeletons, endermen, etc.).

### `entities.getPassive(maxDist)`
Returns only passive mobs (cows, sheep, villagers, etc.).

### `entities.getClosest(maxDist)`
Returns the single closest living entity, or `nil`.

### `entities.getById(id)`
Look up a specific entity by its numeric ID.

### `entities.count(maxDist)`
Returns the number of entities within range.

```lua
function onTick()
    local mobs = entities.getMobs(16)
    for _, mob in ipairs(mobs) do
        chat.info(mob.name .. " at distance " .. string.format("%.1f", mob.distance))
    end
end
```

## Example: Kill Aura

```lua
name = "Simple Aura"
description = "Attacks nearest mob"
category = "COMBAT"

settings.addFloat("Range", 4.0, 2.0, 6.0)
settings.addFloat("Smoothing", 0.4, 0.1, 1.0)

local cooldown = 0

function onTick()
    cooldown = cooldown - 1
    if cooldown > 0 then return end

    local range = settings.get("Range")
    local target = entities.getClosest(range)
    if target and target.type == "hostile" then
        rotation.lookAtEntity(target.id, settings.get("Smoothing"))
        if rotation.inFOV(target.id, 30) then
            action.attack(target.id)
            cooldown = 10  -- ~0.5 sec
        end
    end
end

function onDisable()
    rotation.clearTarget()
end
```
