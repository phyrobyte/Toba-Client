package dev.toba.client.api.pathfinding.navmesh;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Pathfinder that runs A* on the NavMesh polygon adjacency graph,
 * then applies the Simple Stupid Funnel Algorithm for path smoothing (string-pulling).
 * <p>
 * This produces diagonal shortcuts where possible, e.g., a right-angle path
 * A -> B(corner) -> C becomes a direct diagonal A -> C.
 */
public class NavMeshPathfinder {

    // Maximum distance for LOS-based shortcuts during path simplification
    private static final double LOS_MAX_DIST = 50.0;
    // Maximum waypoint look-ahead during simplification
    private static final int LOS_MAX_AHEAD = 40;

    // Configurable flags set by PathfinderModule before each pathfinding request
    private static volatile boolean preferFlatGround = true;
    private static volatile boolean enablePathCoarsening = true;

    public static void setPreferFlatGround(boolean prefer) { preferFlatGround = prefer; }
    public static void setEnablePathCoarsening(boolean enable) { enablePathCoarsening = enable; }

    /**
     * Find a smooth path through the NavMesh from start to goal.
     *
     * @param mesh        The navigation mesh
     * @param start       Start position in world coordinates
     * @param goal        Goal position in world coordinates
     * @param currentTick Current game tick (for blacklist checks)
     * @return NavMeshPath with smooth waypoints, or empty if no path found
     */
    public NavMeshPath findPath(NavMesh mesh, Vec3d start, Vec3d goal, long currentTick, ClientWorld world) {
        // Find start and goal polygons (exact containment first, nearest fallback second)
        NavPoly startPoly = mesh.getPolyAt(start);
        if (startPoly == null) {
            startPoly = mesh.getNearestPoly(start, 8.0);
        }

        NavPoly goalPoly = mesh.getPolyAt(goal);
        if (goalPoly == null) {
            goalPoly = mesh.getNearestPoly(goal, 8.0);
        }

        if (startPoly == null || goalPoly == null) {
            return NavMeshPath.empty();
        }

        // If start and goal are in the same polygon, direct path
        if (startPoly.equals(goalPoly)) {
            List<Vec3d> waypoints = new ArrayList<>();
            waypoints.add(start);
            waypoints.add(goal);
            List<NavPoly> corridor = new ArrayList<>();
            corridor.add(startPoly);
            return new NavMeshPath(waypoints, corridor, true);
        }

        // Run A* on polygon graph
        CorridorResult corridor = findCorridor(mesh, startPoly, goalPoly, currentTick);
        if (corridor == null) {
            return NavMeshPath.empty();
        }

        // Build a safe path using portal midpoints in corridor order,
        // then optimize by removing unnecessary intermediate waypoints
        // where line-of-sight allows direct shortcuts.
        List<Vec3d> waypoints = buildSafePath(start, goal, corridor.portals, corridor.polys, world);

        return new NavMeshPath(waypoints, corridor.polys, true);
    }

    // ──────────────────────────────────────────────────────────────
    //  A* on polygon graph
    // ──────────────────────────────────────────────────────────────

    private CorridorResult findCorridor(NavMesh mesh, NavPoly startPoly, NavPoly goalPoly, long currentTick) {
        PriorityQueue<PolyNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fCost));
        Map<Integer, PolyNode> allNodes = new HashMap<>();
        Set<Integer> closedSet = new HashSet<>();

        Vec3d goalCenter = goalPoly.getCenter();

        PolyNode startNode = new PolyNode(startPoly, null, null, 0,
                startPoly.getCenter().distanceTo(goalCenter));
        openSet.add(startNode);
        allNodes.put(startPoly.getId(), startNode);

        // Limit counts unique polygon visits only (not stale re-adds from cost updates).
        // Each polygon enters the closed set at most once, so polyCount is the hard upper
        // bound. The 4x multiplier is safety margin for disconnected subgraphs where A*
        // must exhaust all reachable nodes before concluding no path exists.
        int maxIterations = mesh.getPolyCount() * 4;
        int iterations = 0;

        while (!openSet.isEmpty()) {
            PolyNode current = openSet.poll();

            // Skip stale queue entries — cheaper path was found later.
            // Do NOT count these against the iteration budget; they are free skips.
            if (closedSet.contains(current.poly.getId())) continue;

            // Count only real work (unique polygon visits), not stale skips.
            if (++iterations > maxIterations) break;

            closedSet.add(current.poly.getId());

            // Reached goal
            if (current.poly.equals(goalPoly)) {
                return reconstructCorridor(current);
            }

            // Explore neighbors via edges
            for (NavEdge edge : current.poly.getEdges()) {
                NavPoly neighbor = edge.getOther(current.poly);

                if (closedSet.contains(neighbor.getId())) continue;

                // Blacklisted polygons are penalized via traversalCost (10x multiplier
                // applied in PathfindingService.applyBlacklistPenalties), NOT hard-skipped.
                // Hard-skipping can make paths unfindable when the blacklisted area is
                // the only way through.

                double edgeCost = current.poly.getCenter().distanceTo(neighbor.getCenter());

                // Wall-clearance penalty: two-part system to push paths away from walls.
                // 1. Multiplier: makes wall-adjacent edges proportionally more expensive
                // 2. Fixed additive: ensures even short edges (small polys) get penalized
                //
                // clearance=0 (touching wall) -> 2.0x mult + 1.5 fixed = very expensive
                // clearance=1 (1 block away)  -> 1.5x mult + 0.75 fixed
                // clearance=2 (2 blocks away) -> 1.33x mult + 0.5 fixed
                // clearance>=3 (corridor center) -> ~1.15x mult + ~0.35 fixed (minimal)
                double clearance = neighbor.getClearance();
                double clearanceMult = 1.0 + 1.0 / (1.0 + clearance);
                double clearanceFixed = 1.5 / (1.0 + clearance);

                double tentativeG = current.gCost + edgeCost * neighbor.getTraversalCost() * clearanceMult + clearanceFixed;

                // Flat ground preference: penalize polygons with fractional Y
                // (stairs/slabs). This gently steers A* toward flat paths when
                // available, while still allowing stair routes when necessary.
                if (preferFlatGround) {
                    double fracY = neighbor.getY() % 1.0;
                    if (fracY < 0) fracY += 1.0;
                    if (fracY > 0.001 && fracY < 0.999) {
                        tentativeG *= 1.3;
                    }
                }

                // Extra cost for Y transitions — direction-aware.
                //
                // Downward (fall/step-off): no jump required. Penalty is proportional
                // to the drop height — just enough to account for the fall arc time.
                // Keeping this small is critical for spiral descents: a heavy penalty
                // makes A* treat each step-down like a detour, causing it to waste
                // thousands of iterations exploring flat paths instead of the spiral.
                //
                // Upward (step-up/jump): requires the player to jump. Heavy penalty
                // forces A* to strongly prefer slab/stair ramp paths over jumping.
                // Slab/stair steps (≤0.5625) have isYTransition=false and pay no penalty.
                if (edge.isYTransition()) {
                    double neighborY = neighbor.getY();
                    double currentPolyY = current.poly.getY();
                    double heightDiff = Math.abs(neighborY - currentPolyY);

                    if (neighborY < currentPolyY) {
                        // Stepping/falling down — proportional to drop, no jump cost.
                        // 2 units per block keeps it negligible vs. the 20-unit jump penalty,
                        // so A* still prefers ramps over jumps but happily routes through
                        // staircase-style descents without treating each step as a detour.
                        tentativeG += 2.0 * heightDiff;
                    } else if (heightDiff <= 1.0) {
                        // Full block step-up: heavy penalty to prefer ramp paths.
                        tentativeG += 20.0;
                    } else {
                        // Multi-block jump up: even heavier, scaled by height.
                        tentativeG += 25.0 * heightDiff;
                    }
                }

                PolyNode neighborNode = allNodes.get(neighbor.getId());

                if (neighborNode == null) {
                    double h = neighbor.getCenter().distanceTo(goalCenter);
                    neighborNode = new PolyNode(neighbor, current, edge, tentativeG, tentativeG + h);
                    allNodes.put(neighbor.getId(), neighborNode);
                    openSet.add(neighborNode);
                } else if (tentativeG < neighborNode.gCost) {
                    neighborNode.parent = current;
                    neighborNode.entryEdge = edge;
                    neighborNode.gCost = tentativeG;
                    neighborNode.fCost = tentativeG + neighbor.getCenter().distanceTo(goalCenter);
                    openSet.add(neighborNode); // Re-add with updated cost
                }
            }
        }

        return null; // No path found
    }

    private CorridorResult reconstructCorridor(PolyNode goalNode) {
        List<NavPoly> polys = new ArrayList<>();
        List<Portal> portals = new ArrayList<>();

        // Walk back from goal to start
        List<PolyNode> nodes = new ArrayList<>();
        PolyNode current = goalNode;
        while (current != null) {
            nodes.add(current);
            current = current.parent;
        }
        Collections.reverse(nodes);

        for (PolyNode node : nodes) {
            polys.add(node.poly);
        }

        // Extract portals from edges between consecutive corridor polygons
        for (int i = 0; i < nodes.size() - 1; i++) {
            PolyNode next = nodes.get(i + 1);
            NavEdge edge = next.entryEdge;
            if (edge != null) {
                // Determine which side is "left" and "right" relative to movement direction
                Vec3d fromCenter = nodes.get(i).poly.getCenter();
                Vec3d toCenter = next.poly.getCenter();
                Vec3d moveDir = toCenter.subtract(fromCenter);

                Vec3d edgeLeft = edge.getLeftVertex();
                Vec3d edgeRight = edge.getRightVertex();

                // Ensure left/right are consistent with movement direction
                // Cross product to determine which side is left
                double cross = crossXZ(moveDir, edgeRight.subtract(edgeLeft));
                if (cross < 0) {
                    // Swap left and right
                    portals.add(new Portal(edgeRight, edgeLeft, edge));
                } else {
                    portals.add(new Portal(edgeLeft, edgeRight, edge));
                }
            }
        }

        return new CorridorResult(polys, portals);
    }

    // ──────────────────────────────────────────────────────────────
    //  Funnel Algorithm (Simple Stupid Funnel Algorithm)
    // ──────────────────────────────────────────────────────────────

    /**
     * Apply the funnel algorithm to smooth a path through portal edges.
     * Produces diagonal shortcuts where the direct line doesn't cross any polygon boundary.
     */
    private List<Vec3d> funnelSmooth(Vec3d start, Vec3d goal, List<Portal> portals, List<NavPoly> corridor) {
        List<Vec3d> path = new ArrayList<>();
        path.add(start);

        if (portals.isEmpty()) {
            path.add(goal);
            return path;
        }

        // Shrink portals to keep player well away from polygon edges/corners
        // and push the path toward corridor centers. Wider portals shrink more
        // aggressively so the funnel algorithm naturally centers the path.
        List<Portal> shrunkPortals = new ArrayList<>();
        for (Portal p : portals) {
            Vec3d mid = p.left.add(p.right).multiply(0.5);
            double portalWidth = p.left.distanceTo(p.right);
            // Shrink by up to 0.8 blocks or 30% of width, whichever is smaller.
            // This keeps waypoints well centered in corridors while leaving enough
            // opening (min 0.3 blocks) for the funnel to work in tight passages.
            double shrink = Math.min(0.8, portalWidth * 0.3);
            double halfWidth = portalWidth * 0.5;
            double shrunkHalf = Math.max(0.15, halfWidth - shrink);
            Vec3d dir = p.right.subtract(p.left);
            if (portalWidth > 0.01) {
                dir = dir.multiply(1.0 / portalWidth); // normalize
            }
            Vec3d shrunkLeft = mid.subtract(dir.multiply(shrunkHalf));
            Vec3d shrunkRight = mid.add(dir.multiply(shrunkHalf));
            shrunkPortals.add(new Portal(shrunkLeft, shrunkRight, p.edge));
        }

        Vec3d apex = start;
        Vec3d portalLeft = start;
        Vec3d portalRight = start;
        int apexIndex = 0;
        int leftIndex = 0;
        int rightIndex = 0;

        for (int i = 0; i < shrunkPortals.size(); i++) {
            Portal portal = shrunkPortals.get(i);
            Vec3d newLeft = portal.left;
            Vec3d newRight = portal.right;

            // Update right vertex
            if (triAreaXZ(apex, portalRight, newRight) <= 0) {
                if (vecEquals(apex, portalRight) || triAreaXZ(apex, portalLeft, newRight) > 0) {
                    portalRight = newRight;
                    rightIndex = i;
                } else {
                    // Right over left, funnel crossing. Add left as new apex.
                    path.add(withCorrectY(portalLeft, corridor, leftIndex));
                    apex = portalLeft;
                    apexIndex = leftIndex;
                    portalRight = apex;
                    rightIndex = apexIndex;

                    // Restart scan from the apex
                    i = apexIndex;
                    portalLeft = apex;
                    leftIndex = apexIndex;
                    continue;
                }
            }

            // Update left vertex
            if (triAreaXZ(apex, portalLeft, newLeft) >= 0) {
                if (vecEquals(apex, portalLeft) || triAreaXZ(apex, portalRight, newLeft) < 0) {
                    portalLeft = newLeft;
                    leftIndex = i;
                } else {
                    // Left over right, funnel crossing. Add right as new apex.
                    path.add(withCorrectY(portalRight, corridor, rightIndex));
                    apex = portalRight;
                    apexIndex = rightIndex;
                    portalLeft = apex;
                    leftIndex = apexIndex;

                    i = apexIndex;
                    portalRight = apex;
                    rightIndex = apexIndex;
                    continue;
                }
            }
        }

        path.add(goal);

        // Remove duplicate consecutive waypoints
        List<Vec3d> cleaned = new ArrayList<>();
        for (Vec3d wp : path) {
            if (cleaned.isEmpty() || !vecEquals(cleaned.get(cleaned.size() - 1), wp)) {
                cleaned.add(wp);
            }
        }

        // Post-process: nudge waypoints away from block corners to prevent clipping.
        // Block corners are at integer coordinates; if a waypoint is too close to one,
        // push it toward the center of the nearest open area.
        cleaned = nudgeAwayFromCorners(cleaned);

        return cleaned;
    }

    /**
     * Assign the correct Y value to a waypoint based on which corridor polygon it falls in.
     */
    private Vec3d withCorrectY(Vec3d point, List<NavPoly> corridor, int portalIndex) {
        // Portal at index i connects corridor poly i to poly i+1
        // Use the destination poly's Y
        if (portalIndex + 1 < corridor.size()) {
            return new Vec3d(point.x, corridor.get(portalIndex + 1).getY(), point.z);
        }
        return point;
    }

    // ──────────────────────────────────────────────────────────────
    //  Safe path building with LOS-based shortcutting
    // ──────────────────────────────────────────────────────────────

    /**
     * Build a path that is guaranteed to not pass through walls.
     * <p>
     * Step 1: Run the funnel algorithm to get smooth waypoints that follow
     *         the corridor shape. This naturally hugs walls in mazes and tight
     *         corridors instead of creating unnecessary diagonals.
     * <p>
     * Step 2: Validate each segment has line-of-sight and doesn't cross diagonal
     *         block corners. Fall back to portal midpoints for any segment that fails.
     * <p>
     * Step 3: Conservative LOS shortcutting — only skip waypoints when the shortcut
     *         doesn't create a diagonal through a corner.
     */
    private List<Vec3d> buildSafePath(Vec3d start, Vec3d goal, List<Portal> portals,
                                       List<NavPoly> corridor, ClientWorld world) {
        // Step 1: Use the funnel algorithm to get initial smooth path.
        // This follows corridor geometry and only creates diagonals where the funnel
        // naturally opens up (wide open spaces), not in tight corridors.
        List<Vec3d> funnelPath = funnelSmooth(start, goal, portals, corridor);

        if (funnelPath.size() <= 2) {
            // Direct path — still validate LOS
            if (funnelPath.size() == 2 && !hasLineOfSight(funnelPath.get(0), funnelPath.get(1), world)) {
                // Fall back to portal midpoint path
                return buildFallbackPath(start, goal, portals, corridor, world);
            }
            return nudgeAwayFromCorners(funnelPath);
        }

        // Step 2: Validate each funnel segment. If any segment fails LOS,
        // fall back to a safe portal midpoint path.
        boolean funnelValid = true;
        for (int i = 0; i < funnelPath.size() - 1; i++) {
            if (!hasLineOfSight(funnelPath.get(i), funnelPath.get(i + 1), world)) {
                funnelValid = false;
                break;
            }
        }

        List<Vec3d> basePath;
        if (funnelValid) {
            basePath = funnelPath;
        } else {
            // Funnel produced an invalid diagonal — fall back to portal midpoints
            basePath = buildFallbackPath(start, goal, portals, corridor, world);
        }

        // Step 3: Multi-pass greedy LOS shortcutting.
        //
        // From each waypoint, find the FARTHEST reachable waypoint with clear
        // line-of-sight and continuous ground. If you can walk from A to C in a
        // straight line, waypoint B is unnecessary.
        //
        // Run multiple passes: each pass produces longer segments, enabling the
        // NEXT pass to find even longer shortcuts that span the new segments.
        // Typically converges in 2-3 passes.
        for (int pass = 0; pass < 3 && basePath.size() > 2; pass++) {
            List<Vec3d> optimized = new ArrayList<>();
            optimized.add(basePath.get(0));

            int current = 0;
            while (current < basePath.size() - 1) {
                int farthest = current + 1;

                int maxAhead = Math.min(basePath.size() - 1, current + LOS_MAX_AHEAD);
                for (int ahead = maxAhead; ahead > current + 1; ahead--) {
                    Vec3d from = basePath.get(current);
                    Vec3d to = basePath.get(ahead);

                    double dist = from.distanceTo(to);
                    if (dist > LOS_MAX_DIST) continue;

                    if (hasLineOfSight(from, to, world)) {
                        farthest = ahead;
                        break;
                    }
                }
                optimized.add(basePath.get(farthest));
                current = farthest;
            }

            // If no waypoints were removed, further passes won't help
            if (optimized.size() >= basePath.size()) break;
            basePath = optimized;
        }

        // Step 4: Coarsen straight runs — collapse sequences of roughly collinear
        // waypoints into just their endpoints. Reduces waypoint count on long
        // straights without affecting turns or elevation changes.
        if (enablePathCoarsening) {
            basePath = coarsenStraightRuns(basePath, 0.3, 0.15);
        }

        return nudgeAwayFromCorners(basePath);
    }

    /**
     * Collapse sequences of roughly collinear waypoints into just their endpoints.
     * A waypoint is "collinear" if its perpendicular distance from the line between
     * the anchor and probe is below a threshold, AND its Y deviation from linear
     * interpolation is below a separate threshold.
     * <p>
     * Preserves waypoints at turns and Y changes (stairs/slopes).
     */
    private List<Vec3d> coarsenStraightRuns(List<Vec3d> path, double xzTolerance, double yTolerance) {
        if (path.size() <= 3) return path;

        List<Vec3d> result = new ArrayList<>();
        result.add(path.get(0));

        int anchor = 0;

        for (int probe = 2; probe < path.size(); probe++) {
            Vec3d a = path.get(anchor);
            Vec3d b = path.get(probe);

            boolean allCollinear = true;
            for (int mid = anchor + 1; mid < probe; mid++) {
                Vec3d p = path.get(mid);

                // Check Y deviation (preserve stair waypoints)
                double t = (double) (mid - anchor) / (probe - anchor);
                double expectedY = a.y + (b.y - a.y) * t;
                if (Math.abs(p.y - expectedY) > yTolerance) {
                    allCollinear = false;
                    break;
                }

                // Perpendicular distance from p to line a->b in XZ plane
                double abDx = b.x - a.x;
                double abDz = b.z - a.z;
                double abLen = Math.sqrt(abDx * abDx + abDz * abDz);
                if (abLen < 0.01) { allCollinear = false; break; }

                double apDx = p.x - a.x;
                double apDz = p.z - a.z;
                double perpDist = Math.abs(apDx * (-abDz / abLen) + apDz * (abDx / abLen));

                if (perpDist > xzTolerance) {
                    allCollinear = false;
                    break;
                }
            }

            if (!allCollinear) {
                // Emit the last collinear point and start a new run
                result.add(path.get(probe - 1));
                anchor = probe - 1;
            }
        }

        // Always add the last waypoint
        result.add(path.get(path.size() - 1));
        return result;
    }

    /**
     * Build a safe fallback path using portal midpoints when the funnel algorithm
     * produces segments that clip through walls or diagonal corners.
     */
    private List<Vec3d> buildFallbackPath(Vec3d start, Vec3d goal, List<Portal> portals,
                                           List<NavPoly> corridor, ClientWorld world) {
        List<Vec3d> fullPath = new ArrayList<>();
        fullPath.add(start);
        for (int i = 0; i < portals.size(); i++) {
            Portal p = portals.get(i);
            Vec3d mid = p.left.add(p.right).multiply(0.5);
            if (i + 1 < corridor.size()) {
                mid = new Vec3d(mid.x, corridor.get(i + 1).getY(), mid.z);
            }
            fullPath.add(mid);
        }
        fullPath.add(goal);

        // Remove consecutive duplicates
        List<Vec3d> deduped = new ArrayList<>();
        for (Vec3d wp : fullPath) {
            if (deduped.isEmpty() || !vecEquals(deduped.get(deduped.size() - 1), wp)) {
                deduped.add(wp);
            }
        }
        return deduped;
    }

    /**
     * Check if there's a clear, walkable line of sight between two points.
     * Ray-marches along the line checking:
     *   1. No solid blocks intersect the player's body at each step
     *   2. There IS ground beneath the player's feet at each step (prevents ledge falls)
     * Accounts for fractional Y heights (slabs/stairs) by checking actual collision shapes.
     * Checks multiple lateral offsets to account for the player's 0.6-block-wide hitbox.
     */
    private boolean hasLineOfSight(Vec3d from, Vec3d to, ClientWorld world) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        if (dist < 0.5) return true;

        // Player hitbox half-width (0.6 wide total, so 0.3 each side)
        double halfWidth = 0.3;

        // Perpendicular direction for lateral offset checks
        double perpX = -dz / dist;
        double perpZ = dx / dist;

        double step = 0.3;
        int steps = (int) Math.ceil(dist / step);
        double stepX = dx / steps;
        double stepZ = dz / steps;
        double dy = to.y - from.y;
        double stepY = dy / steps;

        // Check center line plus both edges of the player hitbox
        double[] offsets = {0.0, -halfWidth, halfWidth};

        for (int i = 1; i < steps; i++) {
            double cx = from.x + stepX * i;
            double cy = from.y + stepY * i; // interpolated feet Y (estimate only)
            double cz = from.z + stepZ * i;

            // === Ground check on center line ===
            // Find the actual walkable ground surface at this XZ position.
            // On rugged slab/stair terrain, the interpolated Y (cy) diverges from
            // the real ground, so we search a vertical window and use the ACTUAL
            // ground height for the body clearance check below.
            // Search ±2 blocks to handle slopes, stairs, and uneven terrain.
            double actualFeetY = cy; // fallback to interpolated if ground not found
            boolean hasGround = false;
            for (int yOff = 2; yOff >= -2 && !hasGround; yOff--) {
                BlockPos gp = BlockPos.ofFloored(cx, cy - 0.05 + yOff, cz);
                if (!world.isChunkLoaded(gp.getX() >> 4, gp.getZ() >> 4)) return false;
                net.minecraft.block.BlockState gs = world.getBlockState(gp);
                net.minecraft.util.shape.VoxelShape gShape = gs.getCollisionShape(world, gp);
                if (!gShape.isEmpty()) {
                    double groundTop = gp.getY() + gShape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                    if (groundTop >= cy - 2.0 && groundTop <= cy + 1.5) {
                        hasGround = true;
                        actualFeetY = groundTop; // use real ground height
                    }
                }
            }
            if (!hasGround) return false;

            // === Body clearance check on all hitbox offsets ===
            // Use actualFeetY (the real ground surface) instead of the interpolated cy.
            // This prevents false collisions on diagonal staircases where the interpolated
            // Y is below the actual walkable surface, causing step-up blocks to be
            // incorrectly flagged as body obstructions.
            for (double offset : offsets) {
                double x = cx + perpX * offset;
                double z = cz + perpZ * offset;

                // Check blocks that the player body occupies (feet to head = 1.8 blocks tall).
                int minBlockY = (int) Math.floor(actualFeetY);
                int maxBlockY = (int) Math.floor(actualFeetY + 1.8);

                for (int blockY = minBlockY; blockY <= maxBlockY; blockY++) {
                    BlockPos checkPos = BlockPos.ofFloored(x, blockY, z);
                    if (!world.isChunkLoaded(checkPos.getX() >> 4, checkPos.getZ() >> 4)) return false;

                    net.minecraft.block.BlockState state = world.getBlockState(checkPos);
                    net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(world, checkPos);

                    if (!shape.isEmpty()) {
                        double shapeBottom = blockY + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                        double shapeTop = blockY + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);

                        // Ground block (player stands on top) is OK
                        // Blocking: collision intrudes into the player body space
                        if (shapeTop > actualFeetY + 0.01 && shapeBottom < actualFeetY + 1.8) {
                            return false;
                        }
                    }

                    // Non-solid obstacles
                    if (NavMeshGenerator.isNonSolidObstacle(state) || NavMeshGenerator.isHazardous(state)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // ──────────────────────────────────────────────────────────────
    //  Diagonal corner detection
    // ──────────────────────────────────────────────────────────────

    /**
     * Check if a path segment crosses a diagonal block corner where solid blocks
     * would clip the player's 0.6-wide hitbox.
     * Uses collision shape checks to handle slabs/stairs correctly.
     */
    private boolean crossesDiagonalCorner(Vec3d from, Vec3d to, ClientWorld world) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.5) return false;

        // Only check if movement is actually diagonal (both X and Z change significantly)
        if (Math.abs(dx) < 0.1 || Math.abs(dz) < 0.1) return false;

        double step = 0.1;
        int steps = (int) Math.ceil(dist / step);
        double stepX = dx / steps;
        double stepZ = dz / steps;
        double dy = to.y - from.y;
        double stepY = dy / steps;

        int prevBlockX = (int) Math.floor(from.x);
        int prevBlockZ = (int) Math.floor(from.z);

        for (int i = 1; i <= steps; i++) {
            double x = from.x + stepX * i;
            double y = from.y + stepY * i; // feet Y (fractional)
            double z = from.z + stepZ * i;

            int blockX = (int) Math.floor(x);
            int blockZ = (int) Math.floor(z);

            // Check if we crossed into a new block diagonally (both X and Z changed)
            if (blockX != prevBlockX && blockZ != prevBlockZ) {
                BlockPos[] corners = {
                    new BlockPos(prevBlockX, (int) Math.floor(y), blockZ),
                    new BlockPos(blockX, (int) Math.floor(y), prevBlockZ)
                };

                for (BlockPos corner : corners) {
                    // Check both feet and head level blocks
                    for (int yOff = 0; yOff <= 1; yOff++) {
                        BlockPos checkPos = yOff == 0 ? corner : corner.up();
                        net.minecraft.block.BlockState state = world.getBlockState(checkPos);
                        net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(world, checkPos);
                        if (!shape.isEmpty()) {
                            double shapeBottom = checkPos.getY() + shape.getMin(net.minecraft.util.math.Direction.Axis.Y);
                            double shapeTop = checkPos.getY() + shape.getMax(net.minecraft.util.math.Direction.Axis.Y);
                            // Check if collision intersects the player body
                            if (shapeTop > y + 0.01 && shapeBottom < y + 1.8) {
                                return true;
                            }
                        }
                    }
                }
            }

            prevBlockX = blockX;
            prevBlockZ = blockZ;
        }

        return false;
    }

    // ──────────────────────────────────────────────────────────────
    //  Corner nudging (post-processing)
    // ──────────────────────────────────────────────────────────────

    /**
     * Nudge waypoints that are too close to block corners (integer XZ coordinates).
     * Block edges in Minecraft are at integer boundaries; the player's hitbox is 0.6
     * wide, so waypoints within ~0.35 blocks of a corner will cause clipping.
     * <p>
     * For each interior waypoint, if its fractional XZ is near 0.0 or 1.0 on either axis,
     * push it 0.35 blocks toward the center of the block.
     */
    private List<Vec3d> nudgeAwayFromCorners(List<Vec3d> path) {
        if (path.size() <= 2) return path;

        List<Vec3d> result = new ArrayList<>(path.size());
        result.add(path.get(0)); // don't nudge start

        double MARGIN = 0.35; // how close to a corner triggers a nudge
        double PUSH = 0.4;    // how far to push away from the corner

        for (int i = 1; i < path.size() - 1; i++) {
            Vec3d wp = path.get(i);
            double fx = wp.x - Math.floor(wp.x); // fractional X [0, 1)
            double fz = wp.z - Math.floor(wp.z); // fractional Z [0, 1)

            double nx = wp.x;
            double nz = wp.z;

            // Check if X is near a block boundary (0.0 or 1.0)
            if (fx < MARGIN) {
                nx = Math.floor(wp.x) + PUSH;
            } else if (fx > 1.0 - MARGIN) {
                nx = Math.floor(wp.x) + 1.0 - PUSH;
            }

            // Check if Z is near a block boundary
            if (fz < MARGIN) {
                nz = Math.floor(wp.z) + PUSH;
            } else if (fz > 1.0 - MARGIN) {
                nz = Math.floor(wp.z) + 1.0 - PUSH;
            }

            result.add(new Vec3d(nx, wp.y, nz));
        }

        result.add(path.get(path.size() - 1)); // don't nudge goal
        return result;
    }

    // ──────────────────────────────────────────────────────────────
    //  Math helpers
    // ──────────────────────────────────────────────────────────────

    /**
     * Signed 2D triangle area in XZ plane (positive = counter-clockwise).
     */
    private static double triAreaXZ(Vec3d a, Vec3d b, Vec3d c) {
        return (b.x - a.x) * (c.z - a.z) - (c.x - a.x) * (b.z - a.z);
    }

    /**
     * Cross product Z-component (for determining left/right of a direction vector).
     */
    private static double crossXZ(Vec3d dir, Vec3d edge) {
        return dir.x * edge.z - dir.z * edge.x;
    }

    /**
     * Check if two Vec3d are approximately equal (within 0.001).
     */
    private static boolean vecEquals(Vec3d a, Vec3d b) {
        return Math.abs(a.x - b.x) < 0.001 && Math.abs(a.z - b.z) < 0.001;
    }

    // ──────────────────────────────────────────────────────────────
    //  Internal data structures
    // ──────────────────────────────────────────────────────────────

    private static class PolyNode {
        final NavPoly poly;
        PolyNode parent;
        NavEdge entryEdge;
        double gCost;
        double fCost;

        PolyNode(NavPoly poly, PolyNode parent, NavEdge entryEdge, double gCost, double fCost) {
            this.poly = poly;
            this.parent = parent;
            this.entryEdge = entryEdge;
            this.gCost = gCost;
            this.fCost = fCost;
        }
    }

    /**
     * A portal (gateway) between two corridor polygons with left/right endpoints.
     */
    static class Portal {
        final Vec3d left;
        final Vec3d right;
        final NavEdge edge;

        Portal(Vec3d left, Vec3d right, NavEdge edge) {
            this.left = left;
            this.right = right;
            this.edge = edge;
        }
    }

    /**
     * Result of the corridor A* search: ordered polygons and portal edges.
     */
    static class CorridorResult {
        final List<NavPoly> polys;
        final List<Portal> portals;

        CorridorResult(List<NavPoly> polys, List<Portal> portals) {
            this.polys = polys;
            this.portals = portals;
        }
    }
}
