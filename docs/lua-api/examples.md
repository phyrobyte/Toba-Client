# Examples

Complete working scripts you can drop into your scripts folder.

---

## Coordinates HUD

Displays a small overlay showing your current position and dimension.

```lua
name        = "CoordsHUD"
description = "Shows your coordinates on screen"
category    = "RENDER"

function onRender()
    local flags = imgui.Flags.NoTitleBar
               + imgui.Flags.AlwaysAutoResize
               + imgui.Flags.NoBackground

    imgui.setNextWindowPos(5, 30)
    if imgui.begin("CoordsHUD", flags) then
        local x = player.x()
        if x then
            imgui.textColored(0.6, 1, 0.6, 1,
                string.format("X: %.1f  Y: %.1f  Z: %.1f", x, player.y(), player.z()))
        else
            imgui.text("Waiting for player...")
        end
    end
    imgui.finish()
end
```

---

## Low Health Alert

Sends a warning when health drops below a threshold.

```lua
name        = "LowHealthAlert"
description = "Warns you when health is low"
category    = "COMBAT"

local warned = false
local threshold = 6  -- 3 hearts

function onTick()
    local hp = player.health()
    if hp == nil then return end

    if hp <= threshold and not warned then
        chat.error("LOW HEALTH: " .. math.floor(hp / 2) .. " hearts remaining!")
        warned = true
    elseif hp > threshold then
        warned = false
    end
end
```

---

## Module Dashboard

An ImGui window that lists all modules and lets you toggle them.

```lua
name        = "Dashboard"
description = "Toggle any module from one window"
category    = "CLIENT"

function onRender()
    imgui.setNextWindowSize(250, 300)
    if imgui.begin("Module Dashboard") then
        local names = modules.list()
        for i = 1, #names do
            local mod = modules.get(names[i])
            if mod then
                local status = mod.isEnabled() and "[ON]" or "[OFF]"
                if mod.isEnabled() then
                    imgui.textColored(0, 1, 0, 1, status)
                else
                    imgui.textColored(1, 0.3, 0.3, 1, status)
                end
                imgui.sameLine()
                if imgui.button(names[i] .. "##" .. i) then
                    mod.toggle()
                end
            end
        end
    end
    imgui.finish()
end
```

---

## Block Inspector

Shows what block you're standing on.

```lua
name        = "BlockInspector"
description = "Shows the block beneath your feet"
category    = "MISC"

function onRender()
    local flags = imgui.Flags.NoTitleBar + imgui.Flags.AlwaysAutoResize

    imgui.setNextWindowPos(5, 50)
    if imgui.begin("BlockInspector", flags) then
        local x = player.x()
        if x then
            local bx = math.floor(x)
            local by = math.floor(player.y()) - 1
            local bz = math.floor(player.z())
            local block = world.getBlock(bx, by, bz)
            imgui.text("Standing on: " .. (block or "unknown"))
        end
    end
    imgui.finish()
end
```

---

## FPS Monitor

Displays FPS with color coding.

```lua
name        = "FPSMonitor"
description = "Color-coded FPS display"
category    = "RENDER"

function onRender()
    local flags = imgui.Flags.NoTitleBar
               + imgui.Flags.AlwaysAutoResize
               + imgui.Flags.NoBackground

    imgui.setNextWindowPos(5, 70)
    if imgui.begin("FPSMonitor", flags) then
        local fps = game.fps()
        local r, g
        if fps >= 60 then
            r, g = 0, 1
        elseif fps >= 30 then
            r, g = 1, 1
        else
            r, g = 1, 0
        end
        imgui.textColored(r, g, 0, 1, "FPS: " .. fps)
    end
    imgui.finish()
end
```

---

## Weather Notifier

Notifies you when weather changes.

```lua
name        = "WeatherNotifier"
description = "Chat alerts when weather changes"
category    = "MISC"

local wasRaining    = false
local wasThundering = false

function onTick()
    local raining    = world.isRaining()
    local thundering = world.isThundering()
    if raining == nil then return end

    if raining and not wasRaining then
        chat.info("It started raining")
    elseif not raining and wasRaining then
        chat.info("Rain stopped")
    end

    if thundering and not wasThundering then
        chat.error("Thunderstorm incoming!")
    elseif not thundering and wasThundering then
        chat.info("Thunderstorm passed")
    end

    wasRaining    = raining
    wasThundering = thundering
end
```

---

## Auto-Toggle on Server

Automatically enables a module when joining a specific server.

```lua
name        = "AutoESP"
description = "Enables ESP on Hypixel"
category    = "MISC"

local TARGET_SERVER = "mc.hypixel.net"
local lastServer    = nil

function onTick()
    local server = game.serverAddress()

    if server ~= lastServer then
        lastServer = server
        if server and string.find(server, TARGET_SERVER) then
            local esp = modules.get("ESP")
            if esp and not esp.isEnabled() then
                esp.enable()
                chat.info("ESP auto-enabled for " .. server)
            end
        end
    end
end
```

---

## Kill Aura

A complete kill aura with configurable range, target filtering, and rotation.

```lua
name = "LuaKillAura"
description = "Attacks nearby hostile mobs"
category = "COMBAT"

settings.addFloat("Range", 4.0, 1.0, 6.0)
settings.addBoolean("Players", false)
settings.addBoolean("Rotate", true)

local cooldown = 0

function onTick()
    if cooldown > 0 then
        cooldown = cooldown - 1
        return
    end

    local range = settings.get("Range")
    local target = nil

    if settings.get("Players") then
        local players = entities.getPlayers(range)
        if #players > 0 then target = players[1] end
    end

    if not target then
        local mobs = entities.getMobs(range)
        if #mobs > 0 then target = mobs[1] end
    end

    if target then
        if settings.get("Rotate") then
            rotation.lookAtEntity(target.id, 0.5)
        end
        action.attack(target.id)
        cooldown = 10 -- 10 ticks between attacks
    end
end

function onDisable()
    rotation.clearTarget()
end
```

---

## Mob ESP

Highlights nearby entities with color-coded boxes.

```lua
name = "MobESP"
description = "Color-coded entity ESP"
category = "RENDER"

settings.addFloat("Range", 32.0, 8.0, 128.0)
settings.addBoolean("Players", true)
settings.addBoolean("Hostile", true)
settings.addBoolean("Passive", false)

function onRender()
    local range = settings.get("Range")

    if settings.get("Players") then
        for _, e in ipairs(entities.getPlayers(range)) do
            esp.addEntity(e.id, 0, 1, 0, 0.4)  -- green
        end
    end

    if settings.get("Hostile") then
        for _, e in ipairs(entities.getMobs(range)) do
            esp.addEntity(e.id, 1, 0.2, 0.2, 0.4)  -- red
        end
    end

    if settings.get("Passive") then
        for _, e in ipairs(entities.getPassive(range)) do
            esp.addEntity(e.id, 0.5, 0.5, 1, 0.3)  -- blue
        end
    end
end
```

---

## Auto Walk to Coordinate

Uses pathfinding to walk to a target, with an ImGui display.

```lua
name = "AutoWalk"
description = "Pathfind to a coordinate"
category = "MACRO"

settings.addInteger("X", 0, -30000, 30000)
settings.addInteger("Y", 64, -64, 320)
settings.addInteger("Z", 0, -30000, 30000)

local walking = false

function onTick()
    if input.isKeyDown(input.KEY_G) and not walking then
        local x = settings.get("X")
        local y = settings.get("Y")
        local z = settings.get("Z")
        pathfinder.goto(x, y, z)
        walking = true
        chat.info(string.format("Walking to %d, %d, %d", x, y, z))
    end

    if walking and not pathfinder.isPathing() then
        walking = false
        chat.info("Arrived!")
    end
end

function onRender()
    local flags = imgui.Flags.AlwaysAutoResize
    if imgui.begin("AutoWalk", flags) then
        if walking then
            imgui.textColored(0, 1, 0, 1, "Walking...")
        else
            imgui.text("Press G to start")
        end
        imgui.text(string.format("Target: %d, %d, %d",
            settings.get("X"), settings.get("Y"), settings.get("Z")))
    end
    imgui.finish()
end

function onDisable()
    if walking then pathfinder.stop() end
end
```

---

## Bazaar Price Overlay

Shows live bazaar prices for farming crops.

```lua
name = "BazaarOverlay"
description = "Live bazaar crop prices"
category = "RENDER"

function onRender()
    if not bazaar.isReady() then return end

    local flags = imgui.Flags.AlwaysAutoResize
    imgui.setNextWindowPos(5, 100)
    if imgui.begin("Bazaar Prices", flags) then
        local crops = {
            {"Melon",     "ENCHANTED_MELON"},
            {"Pumpkin",   "ENCHANTED_PUMPKIN"},
            {"Carrot",    "ENCHANTED_CARROT"},
            {"Potato",    "ENCHANTED_POTATO"},
            {"Cane",      "ENCHANTED_SUGAR_CANE"},
            {"Wart",      "ENCHANTED_NETHER_STALK"},
        }

        imgui.textColored(1, 0.8, 0, 1, "Crop Prices (Sell)")
        for _, crop in ipairs(crops) do
            local price = bazaar.getSellPrice(crop[2])
            imgui.text(string.format("%-8s %.1f", crop[1], price))
        end
    end
    imgui.finish()
end
```

---

## Scoreboard Logger

Logs scoreboard changes to chat — useful for tracking objectives.

```lua
name = "ScoreLogger"
description = "Logs scoreboard changes"
category = "MISC"

local lastLines = {}

function onTick()
    local lines = scoreboard.getLines()
    if not lines then return end

    for i = 1, #lines do
        if lines[i] ~= lastLines[i] then
            chat.info("[SB " .. i .. "] " .. lines[i])
        end
    end
    lastLines = lines
end
```

---

## Status Effect Monitor

HUD showing active potion effects with remaining duration.

```lua
name = "EffectHUD"
description = "Shows active status effects"
category = "RENDER"

function onRender()
    local effects = player.statusEffects()
    if not effects or #effects == 0 then return end

    local flags = imgui.Flags.NoTitleBar + imgui.Flags.AlwaysAutoResize
    imgui.setNextWindowPos(5, 200)
    if imgui.begin("EffectHUD", flags) then
        for i = 1, #effects do
            local e = effects[i]
            local secs = math.floor(e.duration / 20)
            local mins = math.floor(secs / 60)
            secs = secs % 60
            local level = e.amplifier + 1
            imgui.textColored(0.6, 0.9, 1, 1,
                string.format("%s %d  %d:%02d", e.name, level, mins, secs))
        end
    end
    imgui.finish()
end
```
