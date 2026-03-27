# Player API

The `player` table provides read-only access to the local player's state. All functions return `nil` if the player entity does not exist yet (e.g., still on the title screen).

## Position

### `player.x()`
Returns the player's X coordinate (double).

### `player.y()`
Returns the player's Y coordinate (double).

### `player.z()`
Returns the player's Z coordinate (double).

```lua
function onTick()
    local x = player.x()
    local y = player.y()
    local z = player.z()
    if x then
        chat.info(string.format("Position: %.1f, %.1f, %.1f", x, y, z))
    end
end
```

## Rotation

### `player.yaw()`
Returns the player's horizontal rotation in degrees (float).

### `player.pitch()`
Returns the player's vertical rotation in degrees (float).

```lua
local yaw   = player.yaw()    -- -180 to 180
local pitch = player.pitch()  -- -90 (up) to 90 (down)
```

## Vitals

### `player.health()`
Returns current health points (float, 0-20 where 20 = 10 hearts).

### `player.maxHealth()`
Returns maximum health points (float).

### `player.hunger()`
Returns current food level (integer, 0-20).

```lua
function onTick()
    local hp = player.health()
    local max = player.maxHealth()
    if hp and hp < max * 0.25 then
        chat.error("Health critical: " .. math.floor(hp) .. "/" .. math.floor(max))
    end
end
```

## Additional Vitals

### `player.absorption()`
Returns absorption hearts (golden hearts) as a float.

### `player.armorValue()`
Returns total armor value (integer, 0-20).

## XP

### `player.xpLevel()`
Returns the player's experience level (integer).

### `player.xpProgress()`
Returns progress toward the next level (float, 0-1).

```lua
chat.info("Level " .. player.xpLevel() .. " (" .. math.floor(player.xpProgress() * 100) .. "%)")
```

## Movement State

### `player.isOnGround()`
Returns `true` if the player is standing on a solid block.

### `player.isSneaking()`
Returns `true` if the player is currently sneaking.

### `player.isSprinting()`
Returns `true` if the player is currently sprinting.

### `player.isSwimming()`
Returns `true` if the player is swimming.

### `player.isFlying()`
Returns `true` if the player is in creative/spectator flight.

### `player.isInWater()`
Returns `true` if the player is touching water.

### `player.isInLava()`
Returns `true` if the player is in lava.

## Physical

### `player.eyeHeight()`
Returns the player's standing eye height (float).

## Velocity

### `player.velocityX()` / `player.velocityY()` / `player.velocityZ()`
Returns the player's current velocity components.

```lua
local speed = math.sqrt(player.velocityX()^2 + player.velocityZ()^2)
chat.info(string.format("Speed: %.2f blocks/tick", speed))
```

## Status Effects

### `player.statusEffects()`
Returns a table of active status effects. Each entry has:

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Effect translation key |
| `duration` | number | Remaining ticks |
| `amplifier` | number | Effect level (0 = level I) |

```lua
local effects = player.statusEffects()
if effects then
    for i = 1, #effects do
        chat.info(effects[i].name .. " " .. (effects[i].amplifier + 1))
    end
end
```

## Identity

### `player.name()`
Returns the player's username as a string.

### `player.dimension()`
Returns the current dimension identifier (e.g., `"minecraft:overworld"`, `"minecraft:the_nether"`).

```lua
function onEnable()
    chat.info("Hello, " .. (player.name() or "unknown") .. "!")
    chat.info("Dimension: " .. (player.dimension() or "unknown"))
end
```

## Inventory

### `player.heldItem()`
Returns the name of the item in the player's main hand. Returns `"air"` if the hand is empty.

```lua
function onTick()
    local item = player.heldItem()
    if item == "air" then
        chat.info("Your hand is empty")
    end
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `player.name()` | string | Player username |
| `player.x()` | number | X coordinate |
| `player.y()` | number | Y coordinate |
| `player.z()` | number | Z coordinate |
| `player.yaw()` | number | Horizontal rotation (degrees) |
| `player.pitch()` | number | Vertical rotation (degrees) |
| `player.health()` | number | Current HP (0-20) |
| `player.maxHealth()` | number | Maximum HP |
| `player.hunger()` | number | Food level (0-20) |
| `player.absorption()` | number | Absorption hearts |
| `player.armorValue()` | number | Armor value (0-20) |
| `player.xpLevel()` | number | XP level |
| `player.xpProgress()` | number | XP progress (0-1) |
| `player.isOnGround()` | boolean | Standing on solid block |
| `player.isSneaking()` | boolean | Currently sneaking |
| `player.isSprinting()` | boolean | Currently sprinting |
| `player.isSwimming()` | boolean | Currently swimming |
| `player.isFlying()` | boolean | Creative/spectator flight |
| `player.isInWater()` | boolean | Touching water |
| `player.isInLava()` | boolean | In lava |
| `player.eyeHeight()` | number | Standing eye height |
| `player.velocityX()` | number | X velocity |
| `player.velocityY()` | number | Y velocity |
| `player.velocityZ()` | number | Z velocity |
| `player.statusEffects()` | table | Active status effects |
| `player.heldItem()` | string | Main hand item name |
| `player.dimension()` | string | Dimension ID |
