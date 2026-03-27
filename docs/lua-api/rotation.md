# Rotation API

The `rotation` table controls player aim using Toba's GCD-based rotation system. Rotations are smoothed and emulate real mouse movement to avoid detection.

## Functions

### `rotation.setTarget(yaw, pitch, smoothing)`
Set a target rotation. The player's view will smoothly rotate toward it.
- `yaw` — horizontal angle in degrees
- `pitch` — vertical angle in degrees
- `smoothing` — speed from 0.01 (very slow) to 1.0 (instant). Default: 0.3

```lua
rotation.setTarget(90, 0, 0.3)  -- look east, smoothly
```

### `rotation.lookAt(x, y, z, smoothing)`
Calculate and set rotation to look at a world position. Smoothing is optional (default 0.3).

```lua
rotation.lookAt(100, 65, 200, 0.4)  -- look at coordinates
```

### `rotation.lookAtEntity(entityId, smoothing)`
Look at an entity by its numeric ID (from the entities API).

```lua
local mobs = entities.getMobs(16)
if #mobs > 0 then
    rotation.lookAtEntity(mobs[1].id, 0.3)
end
```

### `rotation.clearTarget()`
Stop rotating. Returns control to the player's mouse.

### `rotation.isActive()`
Returns `true` if a rotation target is currently set.

### `rotation.getYaw()` / `rotation.getPitch()`
Returns the player's current yaw/pitch.

### `rotation.getRotationsTo(x, y, z)`
Calculate the yaw and pitch needed to look at a position without actually rotating.

```lua
local rot = rotation.getRotationsTo(100, 65, 200)
chat.info("Yaw: " .. rot.yaw .. " Pitch: " .. rot.pitch)
```

### `rotation.inFOV(entityId, fov)`
Returns `true` if an entity is within the player's field of view. `fov` defaults to 90 degrees.

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `setTarget(yaw, pitch, smoothing)` | nil | Set smooth rotation target |
| `lookAt(x, y, z, smoothing)` | nil | Look at world position |
| `lookAtEntity(entityId, smoothing)` | nil | Look at entity |
| `clearTarget()` | nil | Stop rotating |
| `isActive()` | boolean | Rotation target active |
| `getYaw()` | number | Current player yaw |
| `getPitch()` | number | Current player pitch |
| `getRotationsTo(x, y, z)` | table | Calculate angles to position |
| `inFOV(entityId, fov)` | boolean | Entity within FOV |
