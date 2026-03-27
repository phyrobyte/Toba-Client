name        = "Hub1Begger"
description = "Hub begger with trade acceptance"
category    = "MACRO"

-- ═══════════════════════════════════════════════════════════════════════════
-- State machine
-- ═══════════════════════════════════════════════════════════════════════════
local STATE_IDLE         = 0
local STATE_HUB_CMD      = 1   -- sent /hub, waiting to load
local STATE_WALK_NPC      = 2   -- pathfinding to hub selector NPC
local STATE_FACE_NPC      = 3   -- rotating to face NPC
local STATE_OPEN_MENU     = 4   -- right-clicking NPC to open hub menu
local STATE_READ_SERVERS  = 5   -- hovering over server slots
local STATE_PICK_SERVER   = 6   -- clicking chosen server slot
local STATE_WAIT_LOAD     = 7   -- waiting 10s for server to load
local STATE_WALK_BEG      = 8   -- pathfinding to begging spot
local STATE_FACE_BEG      = 9   -- rotating at begging spot
local STATE_BEGGING       = 10  -- sending beg messages in chat
local STATE_TRADE_ACCEPT  = 11  -- accepting incoming trade request
local STATE_TRADE_WAIT    = 12  -- waiting inside trade menu
local STATE_TRADE_CONFIRM = 13  -- clicking confirm in trade menu

local state = STATE_IDLE
local timer = 0      -- tick countdown for delays
local tickCount = 0  -- total ticks since enable

-- ═══════════════════════════════════════════════════════════════════════════
-- Config
-- ═══════════════════════════════════════════════════════════════════════════
local NPC_POS    = { x = -5,  y = 69, z = -21 }
local NPC_YAW    = 156
local NPC_PITCH  = 7
local BEG_POS    = { x = 0,   y = 69, z = -7 }
local BEG_YAW    = 176
local BEG_PITCH  = 5
local BEG_DELAY_MIN = 60   -- 3 seconds minimum between messages
local BEG_DELAY_MAX = 140  -- 7 seconds maximum
local YAW_RAND   = 3.0     -- random yaw jitter range (+/-)
local PITCH_RAND = 1.5     -- random pitch jitter range (+/-)

-- ═══════════════════════════════════════════════════════════════════════════
-- Beg messages — lots of variety to look human
-- ═══════════════════════════════════════════════════════════════════════════
local begMessages = {
    "can someone please give me coins im so broke",
    "any spare coins? just started playing",
    "pls donate coins or items im new",
    "does anyone have extra items they dont need?",
    "im begging for coins please help a noob out",
    "i lost all my coins can someone help",
    "any rich players wanna help a broke player?",
    "pls give me some coins i need to buy armor",
    "can anyone spare some items? anything helps",
    "im poor pls donate anything",
    "just got scammed and lost everything pls help",
    "any donations? coins or items accepted :)",
    "help a fellow player out? need coins badly",
    "looking for generous players to help me out",
    "can someone trade me some coins please",
    "need coins for a sword can anyone help?",
    "would really appreciate any spare items",
    "coins pls im desperate",
    "anyone feeling generous today?",
    "pls trade me some stuff i have nothing",
    "i need help getting started any coins?",
    "broke player here, any spare items?",
    "can somebody pls give coins? ill pay back later",
    "help me out please im so poor",
    "any old gear you dont need? pls trade me",
    "looking for donations of any kind <3",
    "im begging anyone pls give me coins or items",
    "does anyone have a spare set of armor?",
    "new player needs help, any coins?",
    "pls im broke as a joke give me stuff",
    "can someone help a struggling player?",
    "if you have extra coins pls trade me",
    "kind souls please donate",
    "ill literally take anything pls help",
    "can i get some coins for tools?",
    "not asking for much, just a few coins",
    "first time playing pls give me items",
    "any leftovers? ill take them lol",
    "pls anyone coins or gear",
    "i promise ill pay it forward just need some coins",
}

-- ═══════════════════════════════════════════════════════════════════════════
-- Trade tracking
-- ═══════════════════════════════════════════════════════════════════════════
local tradeRequester   = nil   -- name of player who sent trade request
local tradeEntity      = nil   -- entity table of the trade requester
local tradeOpenedAt    = 0     -- tick when trade menu opened
local lastBegTick      = 0     -- tick when last beg message was sent
local nextBegDelay     = 80    -- randomized delay for next message
local lastUsedMessages = {}    -- track recently used messages to avoid repeats

-- ═══════════════════════════════════════════════════════════════════════════
-- Helpers
-- ═══════════════════════════════════════════════════════════════════════════

local function randRange(min, max)
    return min + math.random() * (max - min)
end

local function jitteredYaw(yaw)
    return yaw + randRange(-YAW_RAND, YAW_RAND)
end

local function jitteredPitch(pitch)
    return pitch + randRange(-PITCH_RAND, PITCH_RAND)
end

local function distTo(pos)
    local dx = player.x() - pos.x
    local dz = player.z() - pos.z
    return math.sqrt(dx * dx + dz * dz)
end

local function pickBegMessage()
    -- Avoid repeating recent messages
    local tries = 0
    local idx
    repeat
        idx = math.random(1, #begMessages)
        tries = tries + 1
    until not lastUsedMessages[idx] or tries > 10

    lastUsedMessages[idx] = true
    -- Keep only last 8 messages tracked
    if tries > 5 then lastUsedMessages = {} end

    -- Add slight typo/variation randomly
    local msg = begMessages[idx]
    local r = math.random(1, 100)
    if r <= 15 then
        msg = msg:upper()
    elseif r <= 30 then
        msg = msg .. "!!"
    elseif r <= 40 then
        msg = msg .. " plsss"
    elseif r <= 50 then
        msg = msg .. " :("
    end
    return msg
end

-- Parse player count from a hub selector slot lore line
-- Expected formats: "Players: 24/80", "§7Players: §a24§7/§a80", etc.
local function parsePlayerCount(loreLines)
    if not loreLines then return nil, nil end
    for i = 1, #loreLines do
        local line = loreLines[i]
        -- Strip color codes
        line = line:gsub("§.", "")
        local current, max = line:match("(%d+)/(%d+)")
        if current and max then
            return tonumber(current), tonumber(max)
        end
    end
    return nil, nil
end

-- ═══════════════════════════════════════════════════════════════════════════
-- onChat — detect trade requests
-- ═══════════════════════════════════════════════════════════════════════════

function onChat(message)
    if state ~= STATE_BEGGING then return end

    -- Hypixel trade request format:
    -- "[PlayerName] has requested to trade!"
    -- or "Click here to /trade accept!"
    -- or "PlayerName wants to trade with you!"
    local requesterName = message:match("(%w+) has requested to trade")
        or message:match("(%w+) wants to trade")

    if requesterName then
        tradeRequester = requesterName
        state = STATE_TRADE_ACCEPT
        timer = 10  -- small delay before accepting
        chat.info("[Begger] Trade request from " .. requesterName)
    end
end

-- ═══════════════════════════════════════════════════════════════════════════
-- onEnable
-- ═══════════════════════════════════════════════════════════════════════════

function onEnable()
    math.randomseed(math.floor((player.x() or 0) * 1000) + math.floor((player.z() or 0) * 137) + math.floor(player.health() * 73))
    tickCount = 0
    lastBegTick = 0
    nextBegDelay = math.random(BEG_DELAY_MIN, BEG_DELAY_MAX)
    lastUsedMessages = {}
    tradeRequester = nil
    tradeEntity = nil

    -- Step 1: send /hub
    state = STATE_HUB_CMD
    timer = 20  -- 1 second delay before sending
    chat.info("[Begger] Starting hub begger sequence...")
end

-- ═══════════════════════════════════════════════════════════════════════════
-- onDisable
-- ═══════════════════════════════════════════════════════════════════════════

function onDisable()
    movement.stopAll()
    rotation.clearTarget()
    if pathfinder.isPathing() then pathfinder.stop() end
    state = STATE_IDLE
    chat.info("[Begger] Stopped.")
end

-- ═══════════════════════════════════════════════════════════════════════════
-- onTick — main state machine
-- ═══════════════════════════════════════════════════════════════════════════

function onTick()
    tickCount = tickCount + 1

    -- Timer countdown
    if timer > 0 then
        timer = timer - 1
        return
    end

    -- ── STATE_HUB_CMD: send /hub ──
    if state == STATE_HUB_CMD then
        chat.command("hub")
        state = STATE_WALK_NPC
        timer = 100  -- wait 5 seconds for hub to load
        chat.info("[Begger] Sent /hub, waiting to load...")

    -- ── STATE_WALK_NPC: pathfind to hub selector NPC ──
    elseif state == STATE_WALK_NPC then
        if pathfinder.isPathing() then
            -- Still walking
            if distTo(NPC_POS) < 3 then
                pathfinder.stop()
                state = STATE_FACE_NPC
                timer = 10
            end
        else
            if distTo(NPC_POS) < 3 then
                state = STATE_FACE_NPC
                timer = 10
            else
                pathfinder.goto(NPC_POS.x, NPC_POS.y, NPC_POS.z)
                chat.info("[Begger] Walking to hub selector NPC...")
            end
        end

    -- ── STATE_FACE_NPC: rotate to face the NPC ──
    elseif state == STATE_FACE_NPC then
        rotation.setTarget(jitteredYaw(NPC_YAW), jitteredPitch(NPC_PITCH), 0.4)
        state = STATE_OPEN_MENU
        timer = 15  -- wait for rotation to settle

    -- ── STATE_OPEN_MENU: right click to open hub selector ──
    elseif state == STATE_OPEN_MENU then
        -- Find and interact with the hub selector NPC entity
        local players = entities.getAll(5)
        local npc = nil
        for _, e in ipairs(players) do
            -- Hub selector NPCs are usually named "Hub Selector" or similar
            local eName = e.name:lower()
            if eName:find("hub") and eName:find("selector") then
                npc = e
                break
            end
        end

        if npc then
            action.interactEntity(npc.id)
        else
            -- Fallback: use item (right click) toward the NPC direction
            action.useItem()
        end

        state = STATE_READ_SERVERS
        timer = 20  -- wait for GUI to open

    -- ── STATE_READ_SERVERS: read first 5 server slots ──
    elseif state == STATE_READ_SERVERS then
        if not screen.isOpen() then
            -- Menu didn't open, retry
            state = STATE_OPEN_MENU
            timer = 20
            return
        end

        local bestSlot = -1
        local bestPlayers = -1

        -- Hub selector is typically a chest GUI
        -- Servers are usually in slots 10-14 (second row, columns 2-6)
        -- or slots 0-4 (first row) — we check both patterns
        local slotsToCheck = {10, 11, 12, 13, 14}  -- second row center
        local slotCount = screen.getSlotCount()

        -- If small chest (27 slots), try first row
        if slotCount <= 27 then
            slotsToCheck = {0, 1, 2, 3, 4}
        end

        for _, slot in ipairs(slotsToCheck) do
            local item = screen.getSlot(slot)
            if item then
                local current, max = parsePlayerCount(item.lore)
                if current and max then
                    local available = max - current
                    -- Must have at least 2 available spots
                    if available >= 2 and current > bestPlayers then
                        bestPlayers = current
                        bestSlot = slot
                    end
                end
            end
        end

        if bestSlot >= 0 then
            state = STATE_PICK_SERVER
            timer = 5
            chat.info("[Begger] Found server with " .. bestPlayers .. " players, slot " .. bestSlot)
            -- Store which slot to click
            _G._beggerPickedSlot = bestSlot
        else
            -- No suitable server found, try first available
            for _, slot in ipairs(slotsToCheck) do
                local item = screen.getSlot(slot)
                if item and item.displayName and item.displayName ~= "" then
                    _G._beggerPickedSlot = slot
                    state = STATE_PICK_SERVER
                    timer = 5
                    chat.info("[Begger] Picking first available server, slot " .. slot)
                    break
                end
            end

            if state ~= STATE_PICK_SERVER then
                -- Still nothing, close and retry
                screen.close()
                state = STATE_OPEN_MENU
                timer = 40
            end
        end

    -- ── STATE_PICK_SERVER: click the chosen server ──
    elseif state == STATE_PICK_SERVER then
        local slot = _G._beggerPickedSlot or 10
        screen.click(slot)
        state = STATE_WAIT_LOAD
        timer = 200  -- 10 seconds to load into the server
        chat.info("[Begger] Joining server, waiting 10s...")

    -- ── STATE_WAIT_LOAD: wait for server to load ──
    elseif state == STATE_WAIT_LOAD then
        -- Close any lingering screens
        if screen.isOpen() then screen.close() end
        state = STATE_WALK_BEG
        timer = 20

    -- ── STATE_WALK_BEG: pathfind to begging position ──
    elseif state == STATE_WALK_BEG then
        if pathfinder.isPathing() then
            if distTo(BEG_POS) < 3 then
                pathfinder.stop()
                state = STATE_FACE_BEG
                timer = 10
            end
        else
            if distTo(BEG_POS) < 3 then
                state = STATE_FACE_BEG
                timer = 10
            else
                pathfinder.goto(BEG_POS.x, BEG_POS.y, BEG_POS.z)
                chat.info("[Begger] Walking to begging spot...")
            end
        end

    -- ── STATE_FACE_BEG: rotate at begging position ──
    elseif state == STATE_FACE_BEG then
        rotation.setTarget(jitteredYaw(BEG_YAW), jitteredPitch(BEG_PITCH), 0.3)
        state = STATE_BEGGING
        timer = 40  -- wait 2 seconds before first message
        lastBegTick = tickCount
        chat.info("[Begger] In position, starting to beg...")

    -- ── STATE_BEGGING: send messages periodically ──
    elseif state == STATE_BEGGING then
        -- Check if it's time to send a beg message
        if tickCount - lastBegTick >= nextBegDelay then
            local msg = pickBegMessage()
            chat.send(msg)
            lastBegTick = tickCount
            nextBegDelay = math.random(BEG_DELAY_MIN, BEG_DELAY_MAX)

            -- Occasionally shift rotation slightly to look more natural
            if math.random(1, 3) == 1 then
                rotation.setTarget(jitteredYaw(BEG_YAW), jitteredPitch(BEG_PITCH), 0.2)
            end
        end

    -- ── STATE_TRADE_ACCEPT: accept incoming trade ──
    elseif state == STATE_TRADE_ACCEPT then
        if tradeRequester then
            -- Find the player entity who requested the trade
            local players = entities.getPlayers(10)
            for _, p in ipairs(players) do
                if p.name == tradeRequester then
                    tradeEntity = p
                    break
                end
            end

            -- Face the player
            if tradeEntity then
                rotation.lookAtEntity(tradeEntity.id, 0.5)
            end

            timer = 15  -- wait for rotation
            state = STATE_TRADE_WAIT
            _G._beggerTradePhase = "accepting"
        end

    -- ── STATE_TRADE_WAIT: handle trade menu phases ──
    elseif state == STATE_TRADE_WAIT then
        local phase = _G._beggerTradePhase or "accepting"

        if phase == "accepting" then
            -- Shift + right click the player to accept, or /trade accept
            if tradeRequester then
                chat.command("trade " .. tradeRequester)
            end
            _G._beggerTradePhase = "waiting_menu"
            timer = 30  -- wait for trade menu to open

        elseif phase == "waiting_menu" then
            if screen.isOpen() then
                local title = screen.getTitle() or ""
                if title:lower():find("trade") or title:lower():find("trading") then
                    tradeOpenedAt = tickCount
                    _G._beggerTradePhase = "in_trade"
                    timer = 10
                    chat.info("[Begger] Trade menu opened, waiting for items...")
                else
                    -- Wrong menu, close and retry
                    screen.close()
                    state = STATE_BEGGING
                    timer = 40
                end
            else
                -- Menu didn't open, go back to begging
                state = STATE_BEGGING
                timer = 40
            end

        elseif phase == "in_trade" then
            -- Look for the confirm button in the trade menu
            -- Hypixel trade: typically a 6-row chest (54 slots)
            -- The confirm button is usually green terracotta/stained glass
            -- We need to wait for the failsafe timer to expire
            -- The failsafe is ~5 seconds (100 ticks) from when trade menu opens
            -- We add 2 extra seconds (40 ticks) as requested
            local elapsed = tickCount - tradeOpenedAt

            if elapsed < 140 then
                -- Still waiting for failsafe + 2s buffer
                -- Every 20 ticks show a countdown
                if elapsed % 40 == 0 then
                    local remaining = math.ceil((140 - elapsed) / 20)
                    chat.info("[Begger] Trade confirm in " .. remaining .. "s...")
                end
            else
                state = STATE_TRADE_CONFIRM
                timer = 5
            end
        end

    -- ── STATE_TRADE_CONFIRM: click confirm in trade menu ──
    elseif state == STATE_TRADE_CONFIRM then
        if screen.isOpen() then
            -- Search for the confirm button by looking for green-colored items
            -- or items named "Confirm" / "Accept" / "Trading" confirm
            local slotCount = screen.getSlotCount()
            local confirmSlot = -1

            for i = 0, slotCount - 1 do
                local item = screen.getSlot(i)
                if item then
                    local name = item.displayName:lower()
                    if name:find("confirm") or name:find("accept")
                       or name:find("trade now") or name:find("ready") then
                        confirmSlot = i
                        break
                    end
                end
            end

            if confirmSlot >= 0 then
                screen.click(confirmSlot)
                chat.info("[Begger] Clicked confirm on slot " .. confirmSlot)
                timer = 40  -- wait for trade to process

                -- Check if we need to click again (some servers require double confirm)
                _G._beggerConfirmClicks = (_G._beggerConfirmClicks or 0) + 1
                if _G._beggerConfirmClicks >= 2 then
                    -- Done with trade, go back to begging
                    _G._beggerConfirmClicks = 0
                    state = STATE_BEGGING
                    timer = 60  -- wait 3s after trade
                    tradeRequester = nil
                    tradeEntity = nil
                    chat.info("[Begger] Trade completed! Resuming begging...")
                end
            else
                -- No confirm button found, might already be processing
                -- or trade was cancelled
                if not screen.isOpen() then
                    state = STATE_BEGGING
                    timer = 40
                    tradeRequester = nil
                    tradeEntity = nil
                end
                timer = 20  -- wait and retry
            end
        else
            -- Screen closed, trade might be done or cancelled
            state = STATE_BEGGING
            timer = 60
            _G._beggerConfirmClicks = 0
            tradeRequester = nil
            tradeEntity = nil
            chat.info("[Begger] Trade ended, resuming begging...")
        end
    end
end

-- ═══════════════════════════════════════════════════════════════════════════
-- onRender — status HUD
-- ═══════════════════════════════════════════════════════════════════════════

local stateNames = {
    [STATE_IDLE]         = "Idle",
    [STATE_HUB_CMD]      = "Sending /hub",
    [STATE_WALK_NPC]      = "Walking to NPC",
    [STATE_FACE_NPC]      = "Facing NPC",
    [STATE_OPEN_MENU]     = "Opening hub menu",
    [STATE_READ_SERVERS]  = "Reading servers",
    [STATE_PICK_SERVER]   = "Picking server",
    [STATE_WAIT_LOAD]     = "Loading server",
    [STATE_WALK_BEG]      = "Walking to beg spot",
    [STATE_FACE_BEG]      = "Facing beg direction",
    [STATE_BEGGING]       = "Begging",
    [STATE_TRADE_ACCEPT]  = "Accepting trade",
    [STATE_TRADE_WAIT]    = "In trade menu",
    [STATE_TRADE_CONFIRM] = "Confirming trade",
}

function onRender()
    local flags = imgui.Flags.AlwaysAutoResize
    imgui.setNextWindowPos(5, 5)
    if imgui.begin("Hub1Begger", flags) then
        local sName = stateNames[state] or "Unknown"
        if state == STATE_BEGGING then
            imgui.textColored(0, 1, 0, 1, "Status: " .. sName)
        elseif state >= STATE_TRADE_ACCEPT then
            imgui.textColored(1, 0.8, 0, 1, "Status: " .. sName)
        else
            imgui.textColored(0.6, 0.8, 1, 1, "Status: " .. sName)
        end

        if timer > 0 then
            imgui.text(string.format("Timer: %.1fs", timer / 20))
        end

        if tradeRequester then
            imgui.textColored(1, 1, 0, 1, "Trade: " .. tradeRequester)
        end

        local runtime = math.floor(tickCount / 20)
        local mins = math.floor(runtime / 60)
        local secs = runtime % 60
        imgui.text(string.format("Uptime: %d:%02d", mins, secs))
    end
    imgui.finish()
end
