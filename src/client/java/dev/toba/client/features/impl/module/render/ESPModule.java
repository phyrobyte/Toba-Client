package dev.toba.client.features.impl.module.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.toba.client.features.settings.Module;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import dev.toba.client.api.render.ESPRenderer;

public class ESPModule extends Module {
    private final List<EntityESPType> entityTypes = new ArrayList<>();
    private final Map<Block, Setting<Boolean>> blockSettings = new HashMap<>();

    // Sub-Categories
    private final Setting<Boolean> entitySub;
    private final Setting<Boolean> blockSub;
    private final Setting<Boolean> colorSub;
    private final Setting<Boolean> miscSub;

    // Custom entity (armor stand / named mob) settings
    private final Setting<Boolean> showCustomEntities;
    private final Setting<Integer> customEntityColor;
    private final Setting<String> customEntityFilter;

    // Block logic settings
    private final Setting<Boolean> showBlocks;
    private final Setting<Integer> scanRadius;
    private final Setting<Integer> transparency;

    // Colors
    private final Setting<Integer> diamondColor, emeraldColor, ancientDebrisColor, coalColor,
            ironColor, copperColor, goldColor, lapisColor,
            redstoneColor, quartzColor;

    public ESPModule() {
        super("ESP", "highlights blocks & entities", Category.RENDER);

        // 1. Entity Settings Section
        entitySub = addSetting(new Setting<>("Entity Settings", true, Setting.SettingType.SUB_CONFIG));
        registerEntityType("Show Players", 0xFFFF3333, PlayerEntity.class);
        registerEntityType("Show Hostiles", 0xFFFF9900, HostileEntity.class);
        registerEntityType("Show Passives", 0xFF33FF33, PassiveEntity.class);

        // Custom entities: armor stands with custom names, named mobs, etc.
        // Catches Hypixel Skyblock pests, NPCs, bosses, and other custom entities.
        showCustomEntities = new Setting<>("Show Custom Entities", true, Setting.SettingType.BOOLEAN);
        customEntityColor = new Setting<>("Custom Entity Color", 0xFFFF00FF, Setting.SettingType.COLOR);
        customEntityFilter = new Setting<>("Name Filter", "", Setting.SettingType.STRING);
        showCustomEntities.child(customEntityColor);
        showCustomEntities.child(customEntityFilter);
        entitySub.child(showCustomEntities);

        // 2. Block Settings Section
        blockSub = addSetting(new Setting<>("Block Settings", true, Setting.SettingType.SUB_CONFIG));
        showBlocks = new Setting<>("Show Blocks", true, Setting.SettingType.BOOLEAN);
        scanRadius = new Setting<>("Scan Radius", 32, Setting.SettingType.INTEGER);
        scanRadius.range(8, 64);

        blockSub.child(showBlocks);
        blockSub.child(scanRadius);

        addTrackedBlock(Blocks.DIAMOND_ORE, "Diamond Ore", true);
        addTrackedBlock(Blocks.DEEPSLATE_DIAMOND_ORE, "Deepslate Diamond", true);
        addTrackedBlock(Blocks.EMERALD_ORE, "Emerald Ore", false);
        addTrackedBlock(Blocks.DEEPSLATE_EMERALD_ORE, "Deepslate Emerald", false);
        addTrackedBlock(Blocks.ANCIENT_DEBRIS, "Ancient Debris", true);
        addTrackedBlock(Blocks.COAL_ORE, "Coal Ore", false);
        addTrackedBlock(Blocks.IRON_ORE, "Iron Ore", false);
        addTrackedBlock(Blocks.GOLD_ORE, "Gold Ore", false);
        addTrackedBlock(Blocks.NETHER_QUARTZ_ORE, "Nether Quartz Ore", false);

        // 3. Color Settings Section
        colorSub = addSetting(new Setting<>("Color Settings", true, Setting.SettingType.SUB_CONFIG));
        diamondColor = createColorSetting("Diamond Color", 0xFF00FFFF);
        emeraldColor = createColorSetting("Emerald Color", 0xFF00FF00);
        ancientDebrisColor = createColorSetting("Ancient Debris Color", 0xFFFF6600);
        coalColor = createColorSetting("Coal Color", 0xFF404040);
        ironColor = createColorSetting("Iron Color", 0xFFD8D8D8);
        copperColor = createColorSetting("Copper Color", 0xFFFF7F50);
        goldColor = createColorSetting("Gold Color", 0xFFFFD700);
        lapisColor = createColorSetting("Lapis Color", 0xFF1E90FF);
        redstoneColor = createColorSetting("Redstone Color", 0xFFFF0000);
        quartzColor = createColorSetting("Quartz Color", 0xFFFFFFFF);

        // 4. Misc Settings
        miscSub = addSetting(new Setting<>("Misc Settings", true, Setting.SettingType.SUB_CONFIG));
        transparency = new Setting<>("Transparency", 80, Setting.SettingType.INTEGER);
        transparency.range(10, 100);
        miscSub.child(transparency);
    }

    private void registerEntityType(String name, int defaultColor, Class<? extends Entity> entityClass) {
        Setting<Boolean> toggle = new Setting<>(name, true, Setting.SettingType.BOOLEAN);
        Setting<Integer> color = new Setting<>(name.replace("Show ", "") + " Color", defaultColor, Setting.SettingType.COLOR);

        toggle.child(color);
        entitySub.child(toggle); // Add to Entity Sub-category

        entityTypes.add(new EntityESPType(toggle, color, entityClass));
    }

    private void addTrackedBlock(Block block, String name, boolean defaultEnabled) {
        Setting<Boolean> setting = new Setting<>(name, defaultEnabled, Setting.SettingType.BOOLEAN);
        setting.icon(new ItemStack(block.asItem()));

        blockSub.child(setting); // Add to Block Sub-category
        blockSettings.put(block, setting);
    }

    private Setting<Integer> createColorSetting(String name, int color) {
        Setting<Integer> s = new Setting<>(name, color, Setting.SettingType.COLOR);
        colorSub.child(s); // Add to Color Sub-category
        return s;
    }

    // ... (rest of your getColorForBlock, onFrameUpdate, and argbToFloats logic remains the same)

    @Override
    protected void onDisable() {
        ESPRenderer.getInstance().beginBlockScan();
    }

    public void onFrameUpdate(float tickDelta) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null) return;

        ESPRenderer renderer = ESPRenderer.getInstance();
        float alpha = transparency.getValue() / 100.0f;

        for (Entity entity : world.getEntities()) {
            if (entity == client.player) continue;

            // Standard entity type matching (players, hostiles, passives)
            boolean matched = false;
            for (EntityESPType type : entityTypes) {
                if (type.toggle.getValue() && type.entityClass.isInstance(entity)) {
                    float[] c = argbToFloats(type.color.getValue());
                    renderer.addEntity(entity, tickDelta, c[0], c[1], c[2], alpha);
                    matched = true;
                    break;
                }
            }

            // Custom entity detection for Hypixel Skyblock pests, NPCs, etc.
            // Skyblock "custom entities" are vanilla entities with custom names.
            // Pests can be actual mobs (silverfish, etc.) OR invisible armor stands
            // with equipment. We check ALL entities for name matches, even ones
            // already matched by standard filters, so we can draw a proper-sized box.
            if (showCustomEntities.getValue()) {
                // Gather the display name — check custom name first, then the entity's
                // default display name (which includes mob type for named mobs)
                String name = null;
                if (entity.hasCustomName() && entity.getCustomName() != null) {
                    name = entity.getCustomName().getString();
                }
                if (name == null || name.isEmpty()) continue;

                // Name filter is REQUIRED to avoid showing every named armor stand
                // (books, decorations, item displays, etc.)
                String filter = customEntityFilter.getValue();
                if (filter == null || filter.isEmpty()) continue;

                boolean passes = false;
                for (String part : filter.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty() && name.toLowerCase().contains(trimmed.toLowerCase())) {
                        passes = true;
                        break;
                    }
                }
                if (!passes) continue;

                // For armor stands, use a larger box since the visual model is
                // bigger than the armor stand's tiny hitbox (0.5x1.975).
                // Also check nearby entities — Skyblock often stacks an armor stand
                // nametag above the actual mob entity.
                float[] c = argbToFloats(customEntityColor.getValue());
                if (entity instanceof ArmorStandEntity) {
                    // Draw a 1x1x1 box centered on the armor stand position
                    // This better represents the visual size of custom models
                    Vec3d pos = entity.getLerpedPos(tickDelta);
                    Box customBox = new Box(
                            pos.x - 0.5, pos.y, pos.z - 0.5,
                            pos.x + 0.5, pos.y + 1.0, pos.z + 0.5
                    );
                    renderer.addBox(customBox, c[0], c[1], c[2], alpha);
                } else {
                    renderer.addEntity(entity, tickDelta, c[0], c[1], c[2], alpha);
                }
            }
        }

        if (showBlocks.getValue()) {
            renderer.beginBlockScan();
            int radius = scanRadius.getValue();
            BlockPos playerPos = client.player.getBlockPos();
            for (BlockPos pos : BlockPos.iterate(playerPos.add(-radius, -radius, -radius), playerPos.add(radius, radius, radius))) {
                Block block = world.getBlockState(pos).getBlock();
                Setting<Boolean> blockSetting = blockSettings.get(block);
                if (blockSetting != null && blockSetting.getValue()) {
                    int color = getColorForBlock(block);
                    float[] bc = argbToFloats(color);
                    renderer.addBlock(pos.toImmutable(), bc[0], bc[1], bc[2], alpha);
                }
            }
        }
    }

    private int getColorForBlock(Block block) {
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return diamondColor.getValue();
        if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) return emeraldColor.getValue();
        if (block == Blocks.ANCIENT_DEBRIS) return ancientDebrisColor.getValue();
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) return coalColor.getValue();
        if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) return ironColor.getValue();
        if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) return copperColor.getValue();
        if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) return goldColor.getValue();
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) return lapisColor.getValue();
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) return redstoneColor.getValue();
        if (block == Blocks.NETHER_QUARTZ_ORE) return quartzColor.getValue();
        return 0xFF00FFFF;
    }

    private static float[] argbToFloats(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255.0f, ((argb >> 8) & 0xFF) / 255.0f, (argb & 0xFF) / 255.0f};
    }

    private static class EntityESPType {
        final Setting<Boolean> toggle;
        final Setting<Integer> color;
        final Class<? extends Entity> entityClass;
        EntityESPType(Setting<Boolean> toggle, Setting<Integer> color, Class<? extends Entity> entityClass) {
            this.toggle = toggle; this.color = color; this.entityClass = entityClass;
        }
    }
}