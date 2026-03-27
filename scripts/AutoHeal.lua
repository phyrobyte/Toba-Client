name        = "AutoHeal"
description = "Auto-switches to healing item when low HP"
category    = "COMBAT"

settings.addFloat("Health Threshold", 10.0, 1.0, 19.0)
settings.addString("Item Name", "wand")
settings.addInteger("Cooldown (ticks)", 40, 10, 200)

local cooldown    = 0
local swapBackAt  = 0       -- tick to swap back to original slot
local originalSlot = -1     -- slot we were on before healing
local tickCount   = 0

function onTick()
    tickCount = tickCount + 1

    -- Handle swap-back after using healing item
    if originalSlot >= 0 and tickCount >= swapBackAt then
        inventory.selectSlot(originalSlot)
        originalSlot = -1
        return
    end

    -- Still mid-swap, don't do anything else
    if originalSlot >= 0 then return end

    -- Cooldown
    if cooldown > 0 then
        cooldown = cooldown - 1
        return
    end

    local hp = player.health()
    if not hp then return end

    local threshold = settings.get("Health Threshold")
    if hp >= threshold then return end

    -- Find healing item in hotbar (slots 0-8)
    local searchName = settings.get("Item Name"):lower()
    local healSlot = -1

    for i = 0, 8 do
        local item = inventory.getSlot(i)
        if item and item.name ~= "air" then
            local itemName = item.name:lower()
            local displayName = item.displayName:lower()
            if itemName:find(searchName) or displayName:find(searchName) then
                healSlot = i
                break
            end
        end
    end

    if healSlot < 0 then return end  -- no healing item found

    -- Save current slot, switch, use, schedule swap-back
    originalSlot = inventory.getSelectedSlot()
    inventory.selectSlot(healSlot)
    action.useItem()
    swapBackAt = tickCount + 3  -- swap back after 3 ticks (enough for use to register)
    cooldown = settings.get("Cooldown (ticks)")

    chat.info("[AutoHeal] Used " .. searchName .. " at " .. string.format("%.0f", hp) .. " HP")
end

function onRender()
    local flags = imgui.Flags.NoTitleBar + imgui.Flags.AlwaysAutoResize + imgui.Flags.NoBackground
    imgui.setNextWindowPos(5, 140)
    if imgui.begin("AutoHealHUD", flags) then
        local hp = player.health() or 0
        local threshold = settings.get("Health Threshold")
        if hp < threshold then
            imgui.textColored(1, 0.2, 0.2, 1, string.format("HP: %.0f / %.0f", hp, threshold))
        else
            imgui.textColored(0.5, 1, 0.5, 1, string.format("HP: %.0f / %.0f", hp, threshold))
        end
        if cooldown > 0 then
            imgui.textColored(0.7, 0.7, 0.7, 1, string.format("CD: %.1fs", cooldown / 20))
        end
    end
    imgui.finish()
end

function onDisable()
    -- Make sure we swap back if disabled mid-heal
    if originalSlot >= 0 then
        inventory.selectSlot(originalSlot)
        originalSlot = -1
    end
end
