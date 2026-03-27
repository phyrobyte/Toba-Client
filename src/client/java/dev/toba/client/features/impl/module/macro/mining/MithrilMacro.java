/**
 * @author Fogma
 * @2026-02-22
 */
package dev.toba.client.features.impl.module.macro.mining;

import dev.toba.client.api.utils.RotationUtil;
import dev.toba.client.features.settings.Module;
import dev.toba.client.features.settings.Module.Setting.RangeValue;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MithrilMacro extends Module {

    private static final float RANGE = 3.0f;

    public final Setting<RangeValue>   aimSpeed  = addSetting(new Setting<>("aiming speed",    new RangeValue(0.70f, 0.90f), Setting.SettingType.MULTI_RANGE).range(0.01, 1.0));
    public final Setting<List<String>> blockList = addSetting(new Setting<>("selected blocks", new ArrayList<>(),            Setting.SettingType.BLOCK_LIST));

    private final Random rng         = new Random();
    private BlockPos     lockedBlock = null;
    private Vec3d        aimPoint    = null;
    private float        speed       = 0.80f;
    private int          ticks       = 0;
    private int          lostTicks   = 0;
    private boolean      settled     = false;

    public MithrilMacro() {
        super("NukerMacro", "can be configured to mine any block", Category.MACRO);
    }

    @Override
    protected void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) mc.options.attackKey.setPressed(false);
        reset();
        RotationUtil.clearTarget();
    }

    @Override
    public void onTick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.currentScreen != null) return;

        mc.options.attackKey.setPressed(true);


        if (lockedBlock != null && !isTargetBlock(mc, lockedBlock)) {
            reset();
            RotationUtil.clearTarget();
        }

        boolean onBlock = mc.crosshairTarget instanceof BlockHitResult bhr
                && lockedBlock != null && bhr.getBlockPos().equals(lockedBlock);

        if (onBlock)      { settled = true; lostTicks = 0; }
        else if (settled)   lostTicks++;

        boolean needNew = lockedBlock == null || (settled && lostTicks >= 3) || (!settled && ticks > 10);
        BlockPos target = needNew ? findBlock(mc, mc.player) : lockedBlock;

        if (target == null) {
            reset();
            RotationUtil.clearTarget();
            return;
        }

        if (!target.equals(lockedBlock)) {
            lockedBlock = target;
            speed       = aimSpeed.getValue().random();
            ticks       = 0;
            settled     = false;
            lostTicks   = 0;
            onBlock     = false;
            aimPoint    = buildAimPoint(lockedBlock);
        }

        ticks++;

        if (settled && onBlock) return;

        Vec3d eyes = mc.player.getEyePos();
        BlockPos inTheWay = getBlockInTheWay(mc, eyes, aimPoint);
        Vec3d actualAim = (inTheWay != null) ? buildAimPoint(inTheWay) : aimPoint;

        float[] r = RotationUtil.getRotations(actualAim);
        float tickSpeed = speed + (float)(rng.nextGaussian() * 0.04);
        RotationUtil.setTarget(r[0], r[1], Math.max(0.1f, Math.min(1.0f, tickSpeed)));
    }


    private boolean isTargetBlock(MinecraftClient mc, BlockPos pos) {
        List<String> ids = blockList.getValue();
        if (ids == null || ids.isEmpty()) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (state.getHardness(mc.world, pos) < 0) return false;
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        return ids.contains(id.toString());
    }

    private BlockPos getBlockInTheWay(MinecraftClient mc, Vec3d from, Vec3d to) {
        Vec3d  dir  = to.subtract(from);
        double dist = dir.length();
        Vec3d  step = dir.normalize().multiply(0.2);
        Vec3d  cur  = from;

        for (double d = 0; d < dist; d += 0.2) {
            cur = cur.add(step);
            BlockPos   pos   = BlockPos.ofFloored(cur);
            BlockState state = mc.world.getBlockState(pos);
            if (state.isAir() || state.getHardness(mc.world, pos) < 0) continue;
            if (!pos.equals(lockedBlock)) return pos;
        }
        return null;
    }

    private Vec3d buildAimPoint(BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        return c.add(
                (rng.nextDouble() * 2 - 1) * 0.28,
                (rng.nextDouble() * 2 - 1) * 0.28,
                (rng.nextDouble() * 2 - 1) * 0.28
        );
    }

    private void reset() {
        lockedBlock = null;
        aimPoint    = null;
        ticks       = 0;
        settled     = false;
        lostTicks   = 0;
    }

    private BlockPos findBlock(MinecraftClient mc, ClientPlayerEntity player) {
        List<String> ids = blockList.getValue();
        if (ids == null || ids.isEmpty()) return null;

        Vec3d    eyes      = player.getEyePos();
        BlockPos origin    = player.getBlockPos();
        BlockPos best      = null;
        double   bestScore = Double.MAX_VALUE;
        int      rad       = (int) Math.ceil(RANGE);

        for (int dx = -rad; dx <= rad; dx++)
            for (int dy = -rad; dy <= rad; dy++)
                for (int dz = -rad; dz <= rad; dz++) {
                    BlockPos   pos   = origin.add(dx, dy, dz);
                    double     dist  = eyes.distanceTo(Vec3d.ofCenter(pos));
                    if (dist > RANGE) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    if (state.getHardness(mc.world, pos) < 0) continue;

                    Identifier id = Registries.BLOCK.getId(state.getBlock());
                    if (!ids.contains(id.toString())) continue;

                    double score = dist + (rng.nextDouble() * 1.2);
                    if (pos.equals(lockedBlock)) score -= 0.5;
                    if (score < bestScore) { bestScore = score; best = pos; }
                }
        return best;
    }
}