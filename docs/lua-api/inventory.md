# Inventory API

The `inventory` table provides access to the player's inventory, hotbar, and armor.

## Item Object

Functions that return items give a table with:

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Item registry name (e.g. `"diamond_sword"`) |
| `count` | number | Stack count |
| `displayName` | string | Human-readable name (e.g. `"Diamond Sword"`) |

## Functions

### `inventory.getSlot(index)`
Get the item in a specific inventory slot. Slots 0-8 are the hotbar, 9-35 are the main inventory, 36-39 are armor.

```lua
local item = inventory.getSlot(0)
chat.info("Slot 0: " .. item.displayName .. " x" .. item.count)
```

### `inventory.getSelectedSlot()`
Returns the currently selected hotbar slot (0-8).

### `inventory.selectSlot(index)`
Switch to a hotbar slot (0-8).

```lua
inventory.selectSlot(0)  -- switch to first slot
```

### `inventory.getArmor(slot)`
Get an armor piece. Slot 0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet.

### `inventory.findItem(name)`
Search inventory for an item by name (partial match, case-insensitive). Returns the slot index, or -1 if not found.

```lua
local slot = inventory.findItem("diamond_sword")
if slot >= 0 then
    inventory.selectSlot(slot)
    chat.info("Switched to diamond sword")
end
```

### `inventory.getSize()`
Returns total inventory size (usually 41).

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `getSlot(index)` | item table | Item at slot |
| `getSelectedSlot()` | number | Active hotbar slot (0-8) |
| `selectSlot(index)` | nil | Switch hotbar slot |
| `getArmor(slot)` | item table | Armor piece (0-3) |
| `findItem(name)` | number | Find item slot by name |
| `getSize()` | number | Total inventory size |
