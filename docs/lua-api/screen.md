# Screen API

The `screen` table lets scripts interact with open container GUIs (chests, trade menus, NPC menus, etc.). This enables automation of inventory-based interactions like hub selectors, trade menus, and shop GUIs.

## Functions

### `screen.isOpen()`
Returns `true` if a handled screen (container GUI) is currently open.

```lua
function onTick()
    if screen.isOpen() then
        chat.info("A menu is open: " .. (screen.getTitle() or "unknown"))
    end
end
```

### `screen.getTitle()`
Returns the title of the currently open screen as a string, or `nil` if no screen is open.

```lua
local title = screen.getTitle()
if title and title:find("Trade") then
    chat.info("Trade menu detected!")
end
```

### `screen.close()`
Closes the currently open screen.

### `screen.getSlotCount()`
Returns the total number of slots in the current screen handler. Returns `0` if no screen is open.

### `screen.getSlot(index)`
Returns information about the item in a specific slot, or `nil` if the slot is empty or invalid. Slots are 0-indexed.

The returned table contains:

| Field | Type | Description |
|-------|------|-------------|
| `name` | string | Internal item ID (e.g. `"minecraft:diamond"`) |
| `displayName` | string | Display name shown in-game |
| `count` | number | Stack size |
| `lore` | table | Array of tooltip lines (strings) |

```lua
local item = screen.getSlot(0)
if item then
    chat.info("Slot 0: " .. item.displayName .. " x" .. item.count)
    -- Read lore lines
    for i = 1, #item.lore do
        chat.info("  " .. item.lore[i])
    end
end
```

### `screen.click(slot, button?)`
Left-clicks a slot in the current screen. Optionally pass `button`: 0 = left (default), 1 = right.

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `slot` | integer | required | Slot index (0-based) |
| `button` | integer | 0 | Mouse button (0=left, 1=right) |

```lua
-- Left-click slot 13
screen.click(13)

-- Right-click slot 13
screen.click(13, 1)
```

### `screen.shiftClick(slot)`
Shift-clicks a slot (quick-move action).

```lua
screen.shiftClick(0)  -- Quick-move slot 0
```

## Slot Layout

Minecraft chest GUIs use the following slot numbering:

**Small Chest (3 rows, 27 slots):**
```
Row 0:  [ 0] [ 1] [ 2] [ 3] [ 4] [ 5] [ 6] [ 7] [ 8]
Row 1:  [ 9] [10] [11] [12] [13] [14] [15] [16] [17]
Row 2:  [18] [19] [20] [21] [22] [23] [24] [25] [26]
```

**Large Chest (6 rows, 54 slots):**
```
Row 0:  [ 0] [ 1] [ 2] [ 3] [ 4] [ 5] [ 6] [ 7] [ 8]
Row 1:  [ 9] [10] [11] [12] [13] [14] [15] [16] [17]
Row 2:  [18] [19] [20] [21] [22] [23] [24] [25] [26]
Row 3:  [27] [28] [29] [30] [31] [32] [33] [34] [35]
Row 4:  [36] [37] [38] [39] [40] [41] [42] [43] [44]
Row 5:  [45] [46] [47] [48] [49] [50] [51] [52] [53]
```

Player inventory slots follow after the container slots.

## Chat Callback

Scripts can react to chat messages using the `onChat(message)` callback:

```lua
function onChat(message)
    if message:find("trade") then
        chat.info("Someone mentioned trading!")
    end
end
```

This fires for every chat message received, including system messages. The `message` parameter is the plain text content with color codes stripped.

## Example: Auto-Accept Menu

```lua
name = "AutoAccept"
description = "Clicks confirm buttons in menus"
category = "MACRO"

function onTick()
    if not screen.isOpen() then return end

    local count = screen.getSlotCount()
    for i = 0, count - 1 do
        local item = screen.getSlot(i)
        if item and item.displayName:lower():find("confirm") then
            screen.click(i)
            return
        end
    end
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `screen.isOpen()` | boolean | Container GUI is open |
| `screen.getTitle()` | string or nil | Screen title |
| `screen.close()` | nil | Close current screen |
| `screen.getSlotCount()` | number | Total slot count |
| `screen.getSlot(index)` | table or nil | Slot item info + lore |
| `screen.click(slot, button?)` | nil | Click a slot |
| `screen.shiftClick(slot)` | nil | Shift-click a slot |
