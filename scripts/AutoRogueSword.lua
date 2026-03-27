name        = "AutoRogueSword"
description = "Auto-uses Rogue Sword for speed boost"
category    = "COMBAT"

settings.addString("Sword Name", "rogue sword")
settings.addInteger("Cooldown (sec)", 30, 10, 60)
settings.addBoolean("Only When Moving", true)

local cooldownTicks = 0
local swapBackAt    = 0
local originalSlot  = -1
local tickCount     = 0

-- Check if player has Speed effect active
local function hasSpeed()
    local effects = player.statusEffects()
    if not effects then return false end
    for i = 1, #effects do
        local name = effects[i].name:lower()
        if name:find("speed") then
            return true, effects[i].duration
        end
    end
    return false, 0
end

-- Check if player is actively moving
local function isMoving()
    local vx = player.velocityX()
    local vz = player.velocityZ()
    if not vx then return false end
    local speed = math.sqrt(vx * vx + vz * vz)
    return speed > 0.01
end

function onTick()
    tickCount = tickCount + 1

    -- Handle swap-back after using rogue sword
    if originalSlot >= 0 and tickCount >= swapBackAt then
        inventory.selectSlot(originalSlot)
        originalSlot = -1
        return
    end

    -- Still mid-swap
    if originalSlot >= 0 then return end

    -- Cooldown
    if cooldownTicks > 0 then
        cooldownTicks = cooldownTicks - 1
        return
    end

    -- Skip if we already have speed
    local speedActive, remaining = hasSpeed()
    if speedActive and remaining > 20 then return end  -- >1 second left

    -- Skip if not moving and setting says only when moving
    if settings.get("Only When Moving") and not isMoving() then return end

    -- Find rogue sword in hotbar
    local searchName = settings.get("Sword Name"):lower()
    local swordSlot = -1

    for i = 0, 8 do
        local item = inventory.getSlot(i)
        if item and item.name ~= "air" then
            local displayName = item.displayName:lower()
            if displayName:find(searchName) then
                swordSlot = i
                break
            end
        end
    end

    if swordSlot < 0 then return end

    -- Save slot, switch, right-click, schedule swap-back
    originalSlot = inventory.getSelectedSlot()

    -- Don't swap if already holding it
    if originalSlot == swordSlot then
        action.useItem()
        cooldownTicks = settings.get("Cooldown (sec)") * 20
        originalSlot = -1  -- no swap needed
        return
    end

    inventory.selectSlot(swordSlot)
    action.useItem()
    swapBackAt = tickCount + 3
    cooldownTicks = settings.get("Cooldown (sec)") * 20
end

function onRender()
    local flags = imgui.Flags.NoTitleBar + imgui.Flags.AlwaysAutoResize + imgui.Flags.NoBackground
    imgui.setNextWindowPos(5, 170)
    if imgui.begin("RogueSwordHUD", flags) then
        local speedActive, remaining = hasSpeed()
        if speedActive then
            local secs = math.floor(remaining / 20)
            imgui.textColored(0.3, 0.8, 1, 1, "Speed: " .. secs .. "s")
        else
            if cooldownTicks > 0 then
                imgui.textColored(1, 0.6, 0.2, 1,
                    string.format("Rogue CD: %.1fs", cooldownTicks / 20))
            else
                imgui.textColored(0.5, 0.5, 0.5, 1, "Rogue: Ready")
            end
        end
    end
    imgui.finish()
end

function onDisable()
    if originalSlot >= 0 then
        inventory.selectSlot(originalSlot)
        originalSlot = -1
    end
end
