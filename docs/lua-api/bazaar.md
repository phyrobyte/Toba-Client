# Bazaar API

The `bazaar` table provides real-time Hypixel SkyBlock Bazaar pricing data.

## Functions

### `bazaar.getSellPrice(productId)`
Returns the instant-sell price for a product (what you get selling to buy orders).

### `bazaar.getBuyPrice(productId)`
Returns the instant-buy price for a product (what you pay buying from sell orders).

### `bazaar.getSellVolume(productId)`
Returns the sell order volume (number of items in sell orders).

### `bazaar.getBuyVolume(productId)`
Returns the buy order volume (number of items in buy orders).

### `bazaar.isReady()`
Returns `true` if Bazaar data has been fetched and is available.

### `bazaar.getProductCount()`
Returns the total number of tracked products.

```lua
function onEnable()
    if not bazaar.isReady() then
        chat.error("Bazaar data not loaded yet")
        return
    end

    local sell = bazaar.getSellPrice("ENCHANTED_DIAMOND")
    local buy = bazaar.getBuyPrice("ENCHANTED_DIAMOND")
    chat.info(string.format("Enchanted Diamond: Buy %.1f / Sell %.1f", buy, sell))
end
```

## Product ID Constants

The bazaar table includes built-in constants for common product IDs. Use these instead of typing raw strings.

### Enchanted Farming

| Constant | Product |
|----------|---------|
| `bazaar.ENCHANTED_MELON` | Enchanted Melon |
| `bazaar.ENCHANTED_PUMPKIN` | Enchanted Pumpkin |
| `bazaar.ENCHANTED_SUGAR_CANE` | Enchanted Sugar Cane |
| `bazaar.ENCHANTED_CACTUS` | Enchanted Cactus |
| `bazaar.ENCHANTED_COCOA` | Enchanted Cocoa |
| `bazaar.ENCHANTED_CARROT` | Enchanted Carrot |
| `bazaar.ENCHANTED_POTATO` | Enchanted Potato |
| `bazaar.ENCHANTED_NETHER_STALK` | Enchanted Nether Wart |
| `bazaar.ENCHANTED_HAY_BALE` | Enchanted Hay Bale |

### Enchanted Mining

| Constant | Product |
|----------|---------|
| `bazaar.ENCHANTED_DIAMOND` | Enchanted Diamond |
| `bazaar.ENCHANTED_GOLD` | Enchanted Gold |
| `bazaar.ENCHANTED_IRON` | Enchanted Iron |
| `bazaar.ENCHANTED_COAL` | Enchanted Coal |
| `bazaar.ENCHANTED_COBBLESTONE` | Enchanted Cobblestone |
| `bazaar.ENCHANTED_LAPIS_LAZULI` | Enchanted Lapis |
| `bazaar.ENCHANTED_REDSTONE` | Enchanted Redstone |
| `bazaar.ENCHANTED_EMERALD` | Enchanted Emerald |
| `bazaar.ENCHANTED_MITHRIL` | Enchanted Mithril |

### Enchanted Combat

| Constant | Product |
|----------|---------|
| `bazaar.ENCHANTED_ROTTEN_FLESH` | Enchanted Rotten Flesh |
| `bazaar.ENCHANTED_BONE` | Enchanted Bone |
| `bazaar.ENCHANTED_ENDER_PEARL` | Enchanted Ender Pearl |
| `bazaar.ENCHANTED_BLAZE_ROD` | Enchanted Blaze Rod |
| `bazaar.ENCHANTED_SPIDER_EYE` | Enchanted Spider Eye |
| `bazaar.ENCHANTED_STRING` | Enchanted String |
| `bazaar.ENCHANTED_GUNPOWDER` | Enchanted Gunpowder |
| `bazaar.ENCHANTED_SLIME_BALL` | Enchanted Slime Ball |

### Slayer Drops

| Constant | Product |
|----------|---------|
| `bazaar.REVENANT_FLESH` | Revenant Flesh |
| `bazaar.TARANTULA_WEB` | Tarantula Web |
| `bazaar.WOLF_TOOTH` | Wolf Tooth |
| `bazaar.NULL_SPHERE` | Null Sphere |

### Raw Materials

| Constant | Product |
|----------|---------|
| `bazaar.WHEAT` | Wheat |
| `bazaar.MELON` | Melon |
| `bazaar.PUMPKIN` | Pumpkin |
| `bazaar.SUGAR_CANE` | Sugar Cane |
| `bazaar.CACTUS` | Cactus |
| `bazaar.COCOA_BEANS` | Cocoa Beans |
| `bazaar.CARROT_ITEM` | Carrot |
| `bazaar.POTATO_ITEM` | Potato |
| `bazaar.NETHER_STALK` | Nether Wart |
| `bazaar.DIAMOND` | Diamond |
| `bazaar.GOLD_INGOT` | Gold Ingot |
| `bazaar.IRON_INGOT` | Iron Ingot |
| `bazaar.COAL` | Coal |
| `bazaar.COBBLESTONE` | Cobblestone |
| `bazaar.LAPIS_LAZULI` | Lapis Lazuli |
| `bazaar.REDSTONE` | Redstone |
| `bazaar.EMERALD` | Emerald |
| `bazaar.MITHRIL_ORE` | Mithril Ore |

You can also pass any string product ID directly:

```lua
local price = bazaar.getSellPrice("ENCHANTED_DIAMOND")
-- equivalent to:
local price = bazaar.getSellPrice(bazaar.ENCHANTED_DIAMOND)
```

## Example: Profit Calculator

```lua
name = "BazaarProfit"
description = "Shows profit margins for farming crops"
category = "MISC"

settings.addMode("Crop", {"Melon", "Pumpkin", "Carrot", "Potato"})

function onTick()
    if not bazaar.isReady() then return end

    local crops = {
        Melon   = { raw = "MELON",        ench = "ENCHANTED_MELON" },
        Pumpkin = { raw = "PUMPKIN",       ench = "ENCHANTED_PUMPKIN" },
        Carrot  = { raw = "CARROT_ITEM",   ench = "ENCHANTED_CARROT" },
        Potato  = { raw = "POTATO_ITEM",   ench = "ENCHANTED_POTATO" },
    }

    local crop = crops[settings.get("Crop")]
    if crop then
        local rawPrice = bazaar.getSellPrice(crop.raw)
        local enchPrice = bazaar.getSellPrice(crop.ench)
        chat.info(string.format("Raw: %.1f | Enchanted: %.1f", rawPrice, enchPrice))
    end
end
```

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `getSellPrice(id)` | number | Instant-sell price |
| `getBuyPrice(id)` | number | Instant-buy price |
| `getSellVolume(id)` | number | Sell order volume |
| `getBuyVolume(id)` | number | Buy order volume |
| `isReady()` | boolean | Data loaded? |
| `getProductCount()` | number | Total tracked products |
