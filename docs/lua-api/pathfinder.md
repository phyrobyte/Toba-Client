# Pathfinder API

The `pathfinder` table provides access to Toba's NavMesh pathfinding system. Scripts can command the player to walk to any reachable position automatically.

## Functions

### `pathfinder.goto(x, y, z)`
Start pathfinding to the given block coordinates. The player will walk there automatically, navigating around obstacles.

```lua
pathfinder.goto(100, 65, 200)
```

### `pathfinder.stop()`
Stop pathfinding and movement.

### `pathfinder.isPathing()`
Returns `true` if the pathfinder is currently active.

```lua
function onTick()
    if not pathfinder.isPathing() then
        chat.info("Arrived at destination!")
    end
end
```

## Example: Patrol Script

```lua
name = "Patrol"
description = "Walk between two waypoints"
category = "MACRO"

settings.addInteger("X1", 100, -30000, 30000)
settings.addInteger("Z1", 200, -30000, 30000)
settings.addInteger("X2", 150, -30000, 30000)
settings.addInteger("Z2", 250, -30000, 30000)

local goingToFirst = true

function onEnable()
    local x = goingToFirst and settings.get("X1") or settings.get("X2")
    local z = goingToFirst and settings.get("Z1") or settings.get("Z2")
    pathfinder.goto(x, 65, z)
end

function onTick()
    if not pathfinder.isPathing() then
        goingToFirst = not goingToFirst
        local x = goingToFirst and settings.get("X1") or settings.get("X2")
        local z = goingToFirst and settings.get("Z1") or settings.get("Z2")
        pathfinder.goto(x, 65, z)
    end
end

function onDisable()
    pathfinder.stop()
end
```
