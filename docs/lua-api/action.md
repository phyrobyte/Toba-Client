# Action API

The `action` table provides combat and interaction functions.

## Functions

### `action.attack(entityId)`
Attack an entity (left click). Includes a hand swing animation. Use entity IDs from the entities API.

```lua
local target = entities.getClosest(4)
if target then
    action.attack(target.id)
end
```

### `action.useItem()`
Use the held item (right click). Works for food, bows, potions, etc.

### `action.swingHand()`
Play the hand swing animation without attacking.

### `action.interactEntity(entityId)`
Right-click an entity (open NPC dialog, trade with villager, etc.).

```lua
local npcs = entities.getPassive(5)
for _, npc in ipairs(npcs) do
    if npc.name:find("Villager") then
        action.interactEntity(npc.id)
        break
    end
end
```

### `action.interactBlock(x, y, z, face)`
Right-click a block. `face` is the side to click: `"up"`, `"down"`, `"north"`, `"south"`, `"east"`, `"west"`. Defaults to `"up"`.

```lua
action.interactBlock(100, 64, 200, "up")  -- place/interact on top of block
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `attack(entityId)` | nil | Left click entity |
| `useItem()` | nil | Right click (use held item) |
| `swingHand()` | nil | Swing animation only |
| `interactEntity(entityId)` | nil | Right click entity |
| `interactBlock(x, y, z, face)` | nil | Right click block |
