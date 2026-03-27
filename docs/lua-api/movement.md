# Movement API

The `movement` table controls player movement by simulating key presses. Call these in `onTick()` — key states reset each tick.

## Functions

### `movement.forward(pressed)` / `movement.backward(pressed)`
Set W/S key state.

### `movement.left(pressed)` / `movement.right(pressed)`
Set A/D key state.

### `movement.jump(pressed)`
Set Space key state.

### `movement.sneak(pressed)`
Set Shift (sneak) key state.

### `movement.sprint(pressed)`
Set Ctrl (sprint) key state.

### `movement.stopAll()`
Release all movement keys at once.

## Example

```lua
name = "Auto Walk"
description = "Walks forward and sprints"
category = "MACRO"

settings.addBoolean("Sprint", true)

function onTick()
    movement.forward(true)
    if settings.get("Sprint") then
        movement.sprint(true)
    end
end

function onDisable()
    movement.stopAll()
end
```

## Full Reference

| Function | Description |
|----------|-------------|
| `forward(pressed)` | W key |
| `backward(pressed)` | S key |
| `left(pressed)` | A key |
| `right(pressed)` | D key |
| `jump(pressed)` | Space key |
| `sneak(pressed)` | Shift key |
| `sprint(pressed)` | Ctrl key |
| `stopAll()` | Release all |
