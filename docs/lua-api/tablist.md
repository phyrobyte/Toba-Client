# Tab List API

The `tablist` table exposes parsed data from the Hypixel SkyBlock tab list. All functions return strings (empty string `""` if not available).

## General Info

### `tablist.getArea()`
Current SkyBlock area (e.g. `"Hub"`, `"The Barn"`).

### `tablist.getServerInfo()`
Server identifier (e.g. `"m23CK"`).

### `tablist.getProfileType()`
Profile type (e.g. `"Normal"`, `"Ironman"`).

## Currency

### `tablist.getPurse()`
Current purse coins as a string.

### `tablist.getBankBalance()`
Bank balance as a string.

### `tablist.getGems()`
Gem count.

### `tablist.getCopper()`
Copper count (Garden currency).

## SkyBlock Level

### `tablist.getSbLevel()`
Current SkyBlock level.

### `tablist.getSbLevelProgress()`
Progress toward next SkyBlock level.

## Skills

### `tablist.getSkillLevel(name)`
Get a skill's level by name (e.g. `"Mining"`, `"Farming"`).

### `tablist.getSkillProgress(name)`
Get a skill's XP progress string.

### `tablist.getFarmingLevel()`
Farming skill level.

```lua
function onEnable()
    local farming = tablist.getFarmingLevel()
    local purse = tablist.getPurse()
    chat.info("Farming: " .. farming .. " | Purse: " .. purse)
end
```

## Garden

### `tablist.getGardenLevel()`
Garden level.

### `tablist.getPestCount()`
Active pest count.

### `tablist.getVisitorCount()`
Number of visitors waiting.

## Jacob's Contest

### `tablist.getJacobCrop()`
Current Jacob contest crop.

### `tablist.getJacobScore()`
Your contest score.

### `tablist.getJacobRank()`
Your contest rank.

### `tablist.getJacobTimeRemaining()`
Time remaining in the contest.

```lua
function onTick()
    local crop = tablist.getJacobCrop()
    if crop ~= "" then
        chat.info("Jacob's: " .. crop .. " | Score: " .. tablist.getJacobScore())
    end
end
```

## Pets

### `tablist.getPetName()`
Active pet name.

### `tablist.getPetLevel()`
Active pet level.

### `tablist.getPetRarity()`
Active pet rarity.

## Mayor

### `tablist.getCurrentMayor()`
Current SkyBlock mayor name.

## Collections & Minions

### `tablist.getCollectionName()`
Tracked collection name.

### `tablist.getCollectionProgress()`
Tracked collection progress.

### `tablist.getMinionCount()`
Number of active minions.

### `tablist.getMinionSlots()`
Total minion slots.

## Bestiary

### `tablist.getBestiaryArea()`
Current bestiary area.

### `tablist.getBestiaryProgress()`
Bestiary progress in current area.

## Other

### `tablist.getStat(name)`
Get a named stat value from the tab list.

### `tablist.getTimer(name)`
Get a named timer value from the tab list.

## Full Reference

| Function | Returns | Description |
|----------|---------|-------------|
| `getArea()` | string | Current area |
| `getServerInfo()` | string | Server ID |
| `getPurse()` | string | Coins in purse |
| `getBankBalance()` | string | Bank balance |
| `getProfileType()` | string | Profile type |
| `getSbLevel()` | string | SkyBlock level |
| `getSbLevelProgress()` | string | SB level progress |
| `getGems()` | string | Gem count |
| `getCopper()` | string | Copper count |
| `getFarmingLevel()` | string | Farming level |
| `getGardenLevel()` | string | Garden level |
| `getPestCount()` | string | Pest count |
| `getVisitorCount()` | string | Visitor count |
| `getJacobCrop()` | string | Contest crop |
| `getJacobScore()` | string | Contest score |
| `getJacobRank()` | string | Contest rank |
| `getJacobTimeRemaining()` | string | Contest time left |
| `getPetName()` | string | Pet name |
| `getPetLevel()` | string | Pet level |
| `getPetRarity()` | string | Pet rarity |
| `getCurrentMayor()` | string | Mayor name |
| `getCollectionName()` | string | Tracked collection |
| `getCollectionProgress()` | string | Collection progress |
| `getMinionCount()` | string | Active minions |
| `getMinionSlots()` | string | Minion slots |
| `getBestiaryArea()` | string | Bestiary area |
| `getBestiaryProgress()` | string | Bestiary progress |
| `getSkillLevel(name)` | string | Skill level by name |
| `getSkillProgress(name)` | string | Skill XP progress |
| `getStat(name)` | string | Named stat value |
| `getTimer(name)` | string | Named timer value |
