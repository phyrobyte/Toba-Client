package dev.toba.client.features.impl.module.macro.foraging;

import dev.toba.client.features.impl.module.macro.misc.PathfinderModule;
import dev.toba.client.features.settings.ModuleManager;
import dev.toba.client.features.render.uptime.ScriptModule;
import dev.toba.client.features.settings.Module;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import dev.toba.client.api.utils.TobaChat;
import dev.toba.client.api.render.ESPRenderer;
import dev.toba.client.api.rotation.RotationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Automatic tree cutting module.
 * Finds nearby trees, delegates pathfinding to PathfinderModule, equips an axe,
 * and chops bottom-to-top with ESP highlighting on the current target block.
 * <p>
 * Uses RotationManager for all rotation (smooth during pathing, instant during mining).
 */
public class TreeCutterModule extends Module implements ScriptModule {

    private final Setting<Integer> searchRadius;
    private final Setting<Integer> maxTreeHeight;
    private final Setting<Integer> highlightColor;
    private final Setting<Integer> pathColor;
    private final Setting<Boolean> sprint;
    private final Setting<Boolean> breakLeaves;

    private enum State {
        SEARCHING, PATHING, EQUIPPING, CHOPPING, CLEARING_LEAVES, IDLE
    }

    private State state = State.IDLE;

    // Tree state
    private List<BlockPos> treeBlocks = null;
    private int chopIndex = 0;
    private BlockPos currentTarget = null;
    private boolean miningStarted = false;
    private int miningTicks = 0;
    private int searchCooldown = 0;

    // Leaves clearing state
    private List<BlockPos> leavesToClear = null;
    private int clearIndex = 0;

    // Position tracking
    private BlockPos standPosition = null;
    private boolean isStandingUnderTree = false;

    // Track which tree columns we've already cut
    private final Set<Long> cutTrees = new HashSet<>();
    // Track which base positions we already evaluated this search to avoid re-tracing
    private final Set<Long> evaluatedBases = new HashSet<>();

    // Stats tracking
    private long startTimeMillis = 0;
    private int treesChopped = 0;
    private int logsChopped = 0;
    private int leavesBroken = 0;

    public TreeCutterModule() {
        super("Tree Cutter", "Automatically finds and chops trees", Category.MACRO);
        setIsScript(true);

        searchRadius = addSetting(new Setting<>("Search Radius", 32, Setting.SettingType.INTEGER));
        searchRadius.range(8, 64);

        maxTreeHeight = addSetting(new Setting<>("Max Tree Height", 10, Setting.SettingType.INTEGER));
        maxTreeHeight.range(4, 20);

        highlightColor = addSetting(new Setting<>("Highlight Color", 0xFFFFFF00, Setting.SettingType.COLOR));
        pathColor = addSetting(new Setting<>("Path Color", 0xFF00FF00, Setting.SettingType.COLOR));

        sprint = addSetting(new Setting<>("Sprint", false, Setting.SettingType.BOOLEAN));
        breakLeaves = addSetting(new Setting<>("Break Leaves", true, Setting.SettingType.BOOLEAN));
    }

    @Override
    protected void onEnable() {
        state = State.SEARCHING;
        cutTrees.clear();
        evaluatedBases.clear();
        startTimeMillis = System.currentTimeMillis();
        treesChopped = 0;
        logsChopped = 0;
        leavesBroken = 0;
        isStandingUnderTree = false;
        standPosition = null;
        TobaChat.send("Tree Cutter started — searching for trees...");
    }

    @Override
    protected void onDisable() {
        resetState();
        RotationManager.getInstance().release("treecutter");
        // Stop pathfinder if we started it
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null && pf.isNavigating()) {
            pf.stop();
        }
        ESPRenderer.getInstance().beginBlockScan();
        TobaChat.send("Tree Cutter stopped.");
    }

    private void resetState() {
        state = State.IDLE;
        treeBlocks = null;
        chopIndex = 0;
        currentTarget = null;
        miningStarted = false;
        miningTicks = 0;
        searchCooldown = 0;
        leavesToClear = null;
        clearIndex = 0;
        isStandingUnderTree = false;
        standPosition = null;
    }

    // ---- ScriptModule interface ----

    @Override
    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    @Override
    public String[] getStatsLines() {
        long elapsed = System.currentTimeMillis() - startTimeMillis;
        double hours = elapsed / 3600000.0;

        int treesPerHour = hours > 0.001 ? (int) (treesChopped / hours) : 0;
        int logsPerHour = hours > 0.001 ? (int) (logsChopped / hours) : 0;
        int leavesPerHour = hours > 0.001 ? (int) (leavesBroken / hours) : 0;

        return new String[]{
                "Trees       " + treesChopped,
                "Trees/hr    " + treesPerHour,
                "Logs        " + logsChopped,
                "Logs/hr     " + logsPerHour,
                "Leaves      " + leavesBroken,
                "Leaves/hr   " + leavesPerHour
        };
    }

    public int getTreesChopped() { return treesChopped; }
    public int getLogsChopped() { return logsChopped; }
    public int getLeavesBroken() { return leavesBroken; }

    /**
     * Called each tick from TobaClient.
     */
    public void onTick() {
        if (!isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // Highlight current target block
        ESPRenderer espRenderer = ESPRenderer.getInstance();
        espRenderer.beginBlockScan();

        if (currentTarget != null) {
            float[] c = PathfinderModule.argbToFloats(highlightColor.getValue());
            espRenderer.addBlock(currentTarget, c[0], c[1], c[2], 0.8f);

            if (isStandingUnderTree && standPosition != null) {
                float[] uc = PathfinderModule.argbToFloats(0xFFFF0000);
                espRenderer.addBlock(standPosition, uc[0], uc[1], uc[2], 0.5f);
            }
        }

        switch (state) {
            case SEARCHING -> tickSearching(client, player);
            case PATHING -> tickPathing(client, player);
            case EQUIPPING -> tickEquipping(client, player);
            case CHOPPING -> tickChopping(client, player);
            case CLEARING_LEAVES -> tickClearingLeaves(client, player);
            case IDLE -> {}
        }
    }

    // ---- SEARCHING ----

    private void tickSearching(MinecraftClient client, ClientPlayerEntity player) {
        if (searchCooldown > 0) {
            searchCooldown--;
            return;
        }

        BlockPos playerPos = player.getBlockPos();
        int radius = searchRadius.getValue();
        int maxHeight = maxTreeHeight.getValue();

        int verticalScan = 20;
        int minY = Math.max(0, playerPos.getY() - verticalScan);
        int maxY = Math.min(client.world.getHeight(), playerPos.getY() + verticalScan);

        BlockPos bestBase = null;
        double bestDist = Double.MAX_VALUE;
        List<BlockPos> bestLogs = null;
        evaluatedBases.clear();

        for (BlockPos pos : BlockPos.iterate(
                playerPos.add(-radius, -radius, -radius),
                playerPos.add(radius, radius, radius))) {

            if (pos.getY() < minY || pos.getY() > maxY) continue;

            BlockState blockState = client.world.getBlockState(pos);
            if (!blockState.isIn(BlockTags.LOGS)) continue;

            BlockState belowState = client.world.getBlockState(pos.down());
            if (belowState.isIn(BlockTags.LOGS)) continue;

            BlockPos base = pos.toImmutable();
            long key = columnKey(base);
            if (cutTrees.contains(key)) continue;
            if (evaluatedBases.contains(key)) continue;
            evaluatedBases.add(key);

            List<BlockPos> logs = traceUp(client, base, maxHeight);
            if (logs == null || logs.size() < 2) continue;

            double dist = base.getSquaredDistance(playerPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestBase = base;
                bestLogs = logs;
            }
        }

        if (bestBase == null) {
            TobaChat.send("No trees found in radius " + radius);
            searchCooldown = 60;
            return;
        }

        treeBlocks = bestLogs;
        chopIndex = 0;
        currentTarget = treeBlocks.get(0);

        TobaChat.send("Found tree (" + treeBlocks.size() + " logs) at " + bestBase);

        standPosition = findStandPosUnderTree(client, bestBase);
        isStandingUnderTree = (standPosition != null);

        if (!isStandingUnderTree) {
            TobaChat.send("Can't stand under tree, trying adjacent...");
            standPosition = findStandPosAdjacent(client, bestBase);
        }

        if (standPosition == null) {
            TobaChat.send("Can't find any stand position, skipping tree...");
            cutTrees.add(columnKey(bestBase));
            searchCooldown = 10;
            return;
        }

        if (isStandingUnderTree) {
            TobaChat.send("Standing UNDER tree at: " + standPosition);
        } else {
            TobaChat.send("Standing adjacent at: " + standPosition);
        }

        // Check if already close enough
        double distToTree = player.getEntityPos().squaredDistanceTo(
                standPosition.getX() + 0.5,
                standPosition.getY(),
                standPosition.getZ() + 0.5
        );

        if (distToTree <= 4.0) {
            state = State.EQUIPPING;
            return;
        }

        // Delegate pathfinding to PathfinderModule
        state = State.PATHING;
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);
        if (pf != null) {
            // Configure pathfinder sprint to match our setting
            pf.startPathTo(standPosition, () -> {
                // Arrival callback - transition to equipping
                if (state == State.PATHING) {
                    state = State.EQUIPPING;
                }
            });
        } else {
            TobaChat.send("Pathfinder module not available!");
            state = State.SEARCHING;
            searchCooldown = 20;
        }
    }

    private List<BlockPos> traceUp(MinecraftClient client, BlockPos base, int maxHeight) {
        List<BlockPos> logs = new ArrayList<>();
        BlockPos current = base;
        while (true) {
            BlockState blockState = client.world.getBlockState(current);
            if (blockState.isIn(BlockTags.LOGS)) {
                logs.add(current.toImmutable());
                if (logs.size() > maxHeight) return null;
                current = current.up();
            } else {
                break;
            }
        }
        return logs.isEmpty() ? null : logs;
    }

    private BlockPos findStandPosUnderTree(MinecraftClient client, BlockPos base) {
        ClientPlayerEntity player = client.player;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int yOffset = -5; yOffset <= 0; yOffset++) {
            BlockPos underPos = base.add(0, yOffset, 0);
            if (isPassableForStanding(client, underPos)) {
                double dist = underPos.getSquaredDistance(player.getBlockPos());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = underPos;
                }
            }
        }

        if (best == null) {
            for (int yOffset = -5; yOffset <= 0; yOffset++) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int zOffset = -1; zOffset <= 1; zOffset++) {
                        if (xOffset == 0 && zOffset == 0) continue;

                        BlockPos underPos = base.add(xOffset, yOffset, zOffset);
                        if (isPassableForStanding(client, underPos)) {
                            double dist = underPos.getSquaredDistance(player.getBlockPos());
                            if (dist < bestDist) {
                                bestDist = dist;
                                best = underPos;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findStandPosAdjacent(MinecraftClient client, BlockPos base) {
        ClientPlayerEntity player = client.player;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int yOffset = -2; yOffset <= 2; yOffset++) {
            BlockPos adjustedBase = base.add(0, yOffset, 0);

            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && zOffset == 0) continue;

                    BlockPos candidate = adjustedBase.add(xOffset, 0, zOffset);

                    if (isPassableForStanding(client, candidate)) {
                        double dist = candidate.getSquaredDistance(player.getBlockPos());
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = candidate;
                        }
                    }

                    BlockPos candidateDown = candidate.down();
                    if (isPassableForStanding(client, candidateDown)) {
                        double dist = candidateDown.getSquaredDistance(player.getBlockPos());
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = candidateDown;
                        }
                    }
                }
            }
        }

        return best;
    }

    private boolean isPassableForStanding(MinecraftClient client, BlockPos pos) {
        BlockState below = client.world.getBlockState(pos.down());
        BlockState feet = client.world.getBlockState(pos);
        BlockState head = client.world.getBlockState(pos.up());

        if (!below.isSolidBlock(client.world, pos.down())) return false;
        if (isTrulyBlocking(client, feet, pos)) return false;
        if (!(head.getBlock() instanceof LeavesBlock) && head.isSolidBlock(client.world, pos.up())) {
            return false;
        }

        return true;
    }

    private boolean isTrulyBlocking(MinecraftClient client, BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getBlock() instanceof LeavesBlock) return false;
        if (!state.isSolidBlock(client.world, pos)) return false;
        return true;
    }

    private long columnKey(BlockPos pos) {
        return ((long) pos.getX() << 32) | (pos.getZ() & 0xFFFFFFFFL);
    }

    // ---- PATHING (delegated to PathfinderModule) ----
    private void tickPathing(MinecraftClient client, ClientPlayerEntity player) {
        PathfinderModule pf = ModuleManager.getInstance().getModule(PathfinderModule.class);

        // If pathfinder finished (arrived or stopped), transition to equipping
        if (pf == null || !pf.isNavigating()) {
            state = State.EQUIPPING;
            return;
        }

        // Also check: are we close enough to the TREE itself to start chopping early?
        if (treeBlocks != null && !treeBlocks.isEmpty()) {
            BlockPos treeBase = treeBlocks.get(0);

            Vec3d playerPos = player.getSyncedPos(); // correct method

            double distToTree = playerPos.squaredDistanceTo(
                    treeBase.getX() + 0.5,
                    treeBase.getY() + 0.5,
                    treeBase.getZ() + 0.5
            );

            // 4.5 blocks radius (squared distance comparison)
            if (distToTree <= 4.5 * 4.5) {
                pf.stop();
                state = State.EQUIPPING;
            }
        }
    }

    // ---- EQUIPPING ----

    private void tickEquipping(MinecraftClient client, ClientPlayerEntity player) {
        boolean hasAxe = false;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                player.getInventory().setSelectedSlot(i);
                hasAxe = true;
                break;
            }
        }

        if (!hasAxe) {
            TobaChat.send("No axe in hotbar! Stopping...");
            setEnabled(false);
            return;
        }

        // Look toward the first log
        if (treeBlocks != null && !treeBlocks.isEmpty()) {
            Vec3d blockCenter = new Vec3d(
                    treeBlocks.get(0).getX() + 0.5,
                    treeBlocks.get(0).getY() + 0.5,
                    treeBlocks.get(0).getZ() + 0.5);
            RotationManager.getInstance().request("treecutter", blockCenter,
                    RotationManager.Style.SMOOTH, RotationManager.Priority.NORMAL);
        }

        // Check if we need to clear leaves first
        if (breakLeaves.getValue() && treeBlocks != null && !treeBlocks.isEmpty()) {
            leavesToClear = findLeavesToClear(client, treeBlocks.get(0));
            if (leavesToClear != null && !leavesToClear.isEmpty()) {
                state = State.CLEARING_LEAVES;
                clearIndex = 0;
                TobaChat.send("Clearing " + leavesToClear.size() + " leaves first...");
                return;
            }
        }

        state = State.CHOPPING;
        miningStarted = false;
        miningTicks = 0;
        TobaChat.send("Chopping tree...");
    }

    // ---- CLEARING LEAVES ----

    private void tickClearingLeaves(MinecraftClient client, ClientPlayerEntity player) {
        if (leavesToClear == null || clearIndex >= leavesToClear.size()) {
            leavesToClear = null;
            clearIndex = 0;
            state = State.CHOPPING;
            miningStarted = false;
            miningTicks = 0;
            return;
        }

        currentTarget = leavesToClear.get(clearIndex);

        // Check if leaf is already broken
        BlockState blockState = client.world.getBlockState(currentTarget);
        if (!(blockState.getBlock() instanceof LeavesBlock)) {
            clearIndex++;
            leavesBroken++;
            miningStarted = false;
            miningTicks = 0;
            return;
        }

        // Face EXACTLY at the center of the block (instant rotation for mining)
        faceBlockInstant(player, currentTarget);

        // Check if within reach
        double dist = player.getEyePos().squaredDistanceTo(
                currentTarget.getX() + 0.5, currentTarget.getY() + 0.5, currentTarget.getZ() + 0.5);
        if (dist > 4.5 * 4.5) {
            miningTicks++;
            if (miningTicks > 50) {
                clearIndex++;
                miningStarted = false;
                miningTicks = 0;
            }
            return;
        }

        // Check if this leaf is ACTUALLY blocking line of sight to the current log
        if (!isLeafBlockingLog(client, player, currentTarget)) {
            clearIndex++;
            miningStarted = false;
            miningTicks = 0;
            return;
        }

        // Break the leaf
        if (!miningStarted) {
            faceBlockInstant(player, currentTarget);
            Direction hitDirection = calculateHitDirection(player, currentTarget);
            client.interactionManager.attackBlock(currentTarget, hitDirection);
            miningStarted = true;
            miningTicks = 0;
        } else {
            Direction hitDirection = calculateHitDirection(player, currentTarget);
            client.interactionManager.updateBlockBreakingProgress(currentTarget, hitDirection);
            miningTicks++;

            if (miningTicks > 30) {
                client.interactionManager.cancelBlockBreaking();
                clearIndex++;
                miningStarted = false;
                miningTicks = 0;
            }
        }
    }

    private List<BlockPos> findLeavesToClear(MinecraftClient client, BlockPos treeBase) {
        List<BlockPos> leaves = new ArrayList<>();

        if (treeBlocks == null || treeBlocks.size() < 1) return leaves;

        ClientPlayerEntity player = client.player;
        if (player == null) return leaves;

        int logsToCheck = Math.min(3, treeBlocks.size());

        for (int i = 0; i < logsToCheck; i++) {
            BlockPos logPos = treeBlocks.get(i);

            Vec3d playerEyePos = player.getEyePos();
            Vec3d logCenter = new Vec3d(
                    logPos.getX() + 0.5, logPos.getY() + 0.5, logPos.getZ() + 0.5);

            Vec3d direction = logCenter.subtract(playerEyePos);
            double distance = direction.length();
            Vec3d step = direction.normalize().multiply(0.1);

            Vec3d currentPos = playerEyePos;
            for (double d = 0; d < distance; d += 0.1) {
                BlockPos checkPos = new BlockPos(
                        (int) Math.floor(currentPos.x),
                        (int) Math.floor(currentPos.y),
                        (int) Math.floor(currentPos.z));

                if (checkPos.equals(player.getBlockPos()) || checkPos.equals(logPos)) {
                    currentPos = currentPos.add(step);
                    continue;
                }

                BlockState bState = client.world.getBlockState(checkPos);

                if (bState.getBlock() instanceof LeavesBlock) {
                    if (!leaves.contains(checkPos)) {
                        leaves.add(checkPos);
                    }
                } else if (bState.isSolidBlock(client.world, checkPos) &&
                        !bState.isIn(BlockTags.LOGS) &&
                        !(bState.getBlock() instanceof LeavesBlock)) {
                    break;
                }

                currentPos = currentPos.add(step);
            }
        }

        return leaves;
    }

    private boolean isLeafBlockingLog(MinecraftClient client, ClientPlayerEntity player, BlockPos leafPos) {
        if (treeBlocks == null) return false;

        Vec3d playerEyePos = player.getEyePos();

        for (BlockPos logPos : treeBlocks) {
            if (chopIndex < treeBlocks.indexOf(logPos)) continue;

            Vec3d logCenter = new Vec3d(
                    logPos.getX() + 0.5, logPos.getY() + 0.5, logPos.getZ() + 0.5);

            if (isPointBetween(playerEyePos, logCenter, leafPos)) {
                if (isLineOfSightBlocked(client, playerEyePos, logCenter, leafPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isPointBetween(Vec3d start, Vec3d end, BlockPos point) {
        Vec3d pointCenter = new Vec3d(
                point.getX() + 0.5, point.getY() + 0.5, point.getZ() + 0.5);

        double totalDist = start.distanceTo(end);
        double distToPoint = start.distanceTo(pointCenter);
        double distFromPoint = pointCenter.distanceTo(end);

        return Math.abs((distToPoint + distFromPoint) - totalDist) < 0.5;
    }

    private boolean isLineOfSightBlocked(MinecraftClient client, Vec3d start, Vec3d end, BlockPos leafPos) {
        Vec3d direction = end.subtract(start);
        double distance = direction.length();
        Vec3d step = direction.normalize().multiply(0.1);

        Vec3d currentPos = start;
        boolean foundLeaf = false;

        for (double d = 0; d < distance; d += 0.1) {
            BlockPos checkPos = new BlockPos(
                    (int) Math.floor(currentPos.x),
                    (int) Math.floor(currentPos.y),
                    (int) Math.floor(currentPos.z));

            if (d < 0.5) {
                currentPos = currentPos.add(step);
                continue;
            }

            if (checkPos.equals(leafPos)) {
                foundLeaf = true;
            }

            if (!foundLeaf) {
                BlockState bState = client.world.getBlockState(checkPos);
                if (bState.isSolidBlock(client.world, checkPos) &&
                        !(bState.getBlock() instanceof LeavesBlock) &&
                        !bState.isIn(BlockTags.LOGS)) {
                    return false;
                }
            }

            if (foundLeaf && !checkPos.equals(leafPos)) {
                BlockState bState = client.world.getBlockState(checkPos);
                if (bState.isSolidBlock(client.world, checkPos) &&
                        !(bState.getBlock() instanceof LeavesBlock)) {
                    return true;
                }
            }

            currentPos = currentPos.add(step);
        }

        return foundLeaf;
    }

    // ---- CHOPPING ----

    private void tickChopping(MinecraftClient client, ClientPlayerEntity player) {
        if (treeBlocks == null || chopIndex >= treeBlocks.size()) {
            finishTree();
            return;
        }

        currentTarget = treeBlocks.get(chopIndex);

        // Check if block is already air (broken)
        BlockState blockState = client.world.getBlockState(currentTarget);
        if (!blockState.isIn(BlockTags.LOGS)) {
            logsChopped++;
            chopIndex++;
            miningStarted = false;
            miningTicks = 0;

            if (breakLeaves.getValue() && chopIndex < treeBlocks.size()) {
                BlockPos nextLog = treeBlocks.get(chopIndex);
                faceBlockInstant(player, nextLog);

                List<BlockPos> newLeaves = findLeavesBlockingCurrentView(client, player, nextLog);
                if (newLeaves != null && !newLeaves.isEmpty()) {
                    leavesToClear = newLeaves;
                    clearIndex = 0;
                    state = State.CLEARING_LEAVES;
                    TobaChat.send("Clearing newly exposed leaves...");
                    return;
                }
            }

            return;
        }

        // Face EXACTLY at the center of the block (instant rotation for mining)
        faceBlockInstant(player, currentTarget);

        // Check if leaves are blocking THIS specific log
        if (breakLeaves.getValue() && isLeafBlockingLog(client, player, currentTarget)) {
            List<BlockPos> blockingLeaves = findLeavesBlockingCurrentView(client, player, currentTarget);
            if (blockingLeaves != null && !blockingLeaves.isEmpty()) {
                leavesToClear = blockingLeaves;
                clearIndex = 0;
                state = State.CLEARING_LEAVES;
                TobaChat.send("Leaf blocking log, clearing first...");
                return;
            }
        }

        // Jump to reach higher blocks
        if (isStandingUnderTree) {
            double dy = (currentTarget.getY() + 0.5) - player.getEyePos().y;
            if (dy > 2.0 && player.isOnGround()) {
                player.jump();
            }
        } else {
            double dy = (currentTarget.getY() + 0.5) - player.getEyePos().y;
            if (dy > 1.5 && player.isOnGround()) {
                player.jump();
            }
        }

        // Check if within reach
        double dist = player.getEyePos().squaredDistanceTo(
                currentTarget.getX() + 0.5, currentTarget.getY() + 0.5, currentTarget.getZ() + 0.5);
        if (dist > 4.5 * 4.5) {
            miningTicks++;
            if (miningTicks > 100) {
                chopIndex++;
                miningStarted = false;
                miningTicks = 0;
            }
            return;
        }

        // Start or continue mining
        if (!miningStarted) {
            faceBlockInstant(player, currentTarget);
            Direction hitDirection = calculateHitDirection(player, currentTarget);
            client.interactionManager.attackBlock(currentTarget, hitDirection);
            miningStarted = true;
            miningTicks = 0;
        } else {
            Direction hitDirection = calculateHitDirection(player, currentTarget);
            client.interactionManager.updateBlockBreakingProgress(currentTarget, hitDirection);
            miningTicks++;

            if (miningTicks > 200) {
                TobaChat.send("Block taking too long, skipping...");
                client.interactionManager.cancelBlockBreaking();
                chopIndex++;
                miningStarted = false;
                miningTicks = 0;
            }
        }
    }

    private List<BlockPos> findLeavesBlockingCurrentView(MinecraftClient client, ClientPlayerEntity player, BlockPos logPos) {
        List<BlockPos> leaves = new ArrayList<>();

        Vec3d playerEyePos = player.getEyePos();
        Vec3d logCenter = new Vec3d(
                logPos.getX() + 0.5, logPos.getY() + 0.5, logPos.getZ() + 0.5);

        Vec3d direction = logCenter.subtract(playerEyePos);
        double distance = direction.length();
        Vec3d step = direction.normalize().multiply(0.1);

        Vec3d currentPos = playerEyePos;
        for (double d = 0; d < distance; d += 0.1) {
            BlockPos checkPos = new BlockPos(
                    (int) Math.floor(currentPos.x),
                    (int) Math.floor(currentPos.y),
                    (int) Math.floor(currentPos.z));

            if (checkPos.equals(player.getBlockPos()) || checkPos.equals(logPos)) {
                currentPos = currentPos.add(step);
                continue;
            }

            BlockState bState = client.world.getBlockState(checkPos);

            if (bState.getBlock() instanceof LeavesBlock) {
                if (!leaves.contains(checkPos)) {
                    leaves.add(checkPos);
                }
                break;
            } else if (bState.isSolidBlock(client.world, checkPos) &&
                    !bState.isIn(BlockTags.LOGS) &&
                    !(bState.getBlock() instanceof LeavesBlock)) {
                return leaves;
            }

            currentPos = currentPos.add(step);
        }

        return leaves;
    }

    private void finishTree() {
        if (treeBlocks != null) {
            cutTrees.add(columnKey(treeBlocks.get(0)));
            treesChopped++;
            TobaChat.send("Tree chopped! (" + treesChopped + " total)");
        }

        treeBlocks = null;
        currentTarget = null;
        miningStarted = false;
        state = State.SEARCHING;
        searchCooldown = 5;
        isStandingUnderTree = false;
        standPosition = null;
        RotationManager.getInstance().release("treecutter");
    }

    // ===== ROTATION METHODS (using RotationManager) =====

    /**
     * Face EXACTLY at the center of a block using instant rotation (for mining).
     * Uses RotationManager with INSTANT style and HIGH priority.
     */
    private void faceBlockInstant(ClientPlayerEntity player, BlockPos pos) {
        Vec3d blockCenter = new Vec3d(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        RotationManager.getInstance().request("treecutter", blockCenter,
                RotationManager.Style.INSTANT, RotationManager.Priority.HIGH);

        // Also apply immediately for this tick (mining needs precise aim NOW)
        float[] angles = RotationManager.calcAngles(player.getEyePos(), blockCenter);
        player.setYaw(angles[0]);
        player.setPitch(angles[1]);
    }

    /**
     * Calculate which face of the block is closest to the player for hit registration.
     */
    private Direction calculateHitDirection(ClientPlayerEntity player, BlockPos pos) {
        Vec3d eyePos = player.getEyePos();
        Vec3d blockCenter = new Vec3d(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        double dx = eyePos.x - blockCenter.x;
        double dy = eyePos.y - blockCenter.y;
        double dz = eyePos.z - blockCenter.z;

        double absX = Math.abs(dx);
        double absY = Math.abs(dy);
        double absZ = Math.abs(dz);

        if (absX > absY && absX > absZ) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (absY > absX && absY > absZ) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
}
