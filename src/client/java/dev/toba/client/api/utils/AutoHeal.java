package dev.toba.client.api.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

/**
 * AutoHeal — scans the player's hotbar for healing items (Hypixel SkyBlock)
 * and automatically right-clicks them when health drops below a threshold.
 *
 * Usage: call {@link #tick(float)} once per client tick from whatever module
 * or event needs auto-healing. The threshold is a fraction (0.0–1.0) of max HP.
 *
 * Supported items (all triggered by right-click):
 *
 *   Healing Wands (heal over time, 7s duration, 1s cooldown):
 *     Wand of Atonement, Wand of Restoration, Wand of Mending, Wand of Healing
 *
 *   Zombie Swords (instant heal, charge-based):
 *     Florid Zombie Sword, Ornate Zombie Sword, Zombie Sword
 *
 *   Wand of Strength (heals +30 Strength, costs 10% max health — lower priority)
 *
 *   Power Orbs (AOE heal-over-time, placed on ground, 30-60s duration):
 *     Plasmaflux Power Orb, Overflux Power Orb, Mana Flux Power Orb, Radiant Power Orb
 */
public class AutoHeal {

    // Known healing item names in priority order (best first).
    // Instant/fast heals before slow heals, stronger items before weaker.
    private static final String[] HEALING_ITEMS = {
            // Zombie Swords — instant heal, best emergency response
            "Florid Zombie Sword",
            "Ornate Zombie Sword",
            "Zombie Sword",
            // Healing Wands — heal over time (7s)
            "Wand of Atonement",
            "Wand of Restoration",
            "Wand of Mending",
            "Wand of Healing",
            // Wand of Strength — costs 10% max HP so only use as last resort wand
            "Wand of Strength",
            // Power Orbs — AOE placed on ground, slowest.
            "Plasmaflux Power Orb",
            "Overflux Power Orb",
            "Mana Flux Power Orb",
            "Radiant Power Orb",
    };

    // Cooldown to prevent spamming right-click every tick
    private int cooldownTicks = 0;
    private static final int HEAL_COOLDOWN = 20; // 1 second between heal attempts

    // Remember the slot we switched FROM so we can switch back
    private int previousSlot = -1;
    private int switchBackDelay = 0;
    private static final int SWITCH_BACK_TICKS = 4; // wait a few ticks before switching back

    /**
     * Call once per tick. Checks health and heals if below threshold.
     *
     * @param threshold fraction of max HP (e.g., 0.10 for 10%)
     */
    public void tick(float threshold) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        ClientPlayerEntity player = client.player;

        // Handle switching back to original slot after healing
        if (switchBackDelay > 0) {
            switchBackDelay--;
            if (switchBackDelay == 0 && previousSlot >= 0) {
                player.getInventory().setSelectedSlot(previousSlot);
                previousSlot = -1;
            }
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0) return;

        float healthPercent = health / maxHealth;
        if (healthPercent >= threshold) return;

        // Find best healing item in hotbar (slots 0-8)
        int healSlot = findHealingSlot(player);
        if (healSlot < 0) return;

        // Switch to the healing item slot if needed
        int currentSlot = player.getInventory().getSelectedSlot();
        if (currentSlot != healSlot) {
            previousSlot = currentSlot;
            player.getInventory().setSelectedSlot(healSlot);
        }

        // Right-click to activate the healing item
        if (client.interactionManager != null) {
            client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        }

        cooldownTicks = HEAL_COOLDOWN;

        // Schedule switching back to original slot
        if (previousSlot >= 0) {
            switchBackDelay = SWITCH_BACK_TICKS;
        }
    }

    /**
     * Find the hotbar slot containing a healing item. Prioritizes items
     * earlier in the HEALING_ITEMS array (best items first).
     *
     * @return hotbar slot (0-8) or -1 if none found
     */
    private int findHealingSlot(ClientPlayerEntity player) {
        // Check each healing item name in priority order
        for (String healName : HEALING_ITEMS) {
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = player.getInventory().getStack(slot);
                if (stack.isEmpty()) continue;

                String itemName = Formatting.strip(stack.getName().getString());
                if (itemName != null && itemName.contains(healName)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /**
     * Reset state (call when the owning module is disabled).
     */
    public void reset() {
        cooldownTicks = 0;
        previousSlot = -1;
        switchBackDelay = 0;
    }
}
