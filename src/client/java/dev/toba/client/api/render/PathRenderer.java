package dev.toba.client.api.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders pathfinding lines on the ground, visible through walls.
 * Supports both Vec3d waypoints (NavMesh diagonal paths) and legacy BlockPos paths.
 */
/**
 * Renders pathfinding lines on the ground, visible through walls.
 * Each segment is an independent quad with uniform width.
 */
public class PathRenderer {
    private static PathRenderer instance;

    private List<Vec3d> currentPath = null;
    private float red = 0, green = 1, blue = 1, alpha = 0.8f;

    // The index of the waypoint the player is currently heading toward.
    // Segments before (activeIndex - 1) are not rendered.
    private int activeIndex = 0;

    // Half-width of the line in blocks (0.01 = 0.02 total, very thin string)
    private static final float LINE_HALF_WIDTH = 0.01f;

    private static final RenderPipeline PATH_QUAD_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.POSITION_COLOR_SNIPPET
            )
            .withLocation(Identifier.of("toba", "pipeline/path_quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer PATH_QUAD_LAYER = RenderLayer.of(
            "toba_path_quads",
            RenderSetup.builder(PATH_QUAD_PIPELINE).translucent().build()
    );

    public static PathRenderer getInstance() {
        if (instance == null) instance = new PathRenderer();
        return instance;
    }

    public void setPathVec3d(List<Vec3d> path) {
        this.currentPath = path != null ? new ArrayList<>(path) : null;
        this.activeIndex = 0;
    }

    public void setPath(List<BlockPos> path) {
        if (path == null) {
            this.currentPath = null;
            return;
        }
        List<Vec3d> vec3dPath = new ArrayList<>(path.size());
        for (BlockPos pos : path) {
            vec3dPath.add(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        }
        this.currentPath = vec3dPath;
        this.activeIndex = 0;
    }

    public void setActiveIndex(int index) {
        this.activeIndex = index;
    }

    public void setColor(float r, float g, float b, float a) {
        this.red = r;
        this.green = g;
        this.blue = b;
        this.alpha = a;
    }

    public void clearPath() {
        this.currentPath = null;
        this.activeIndex = 0;
    }

    public void render(WorldRenderContext context) {
        if (currentPath == null || currentPath.size() < 2) return;

        int renderStart = Math.max(0, activeIndex - 1);
        if (renderStart >= currentPath.size() - 1) return;

        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        int r = (int) (red * 255);
        int g = (int) (green * 255);
        int b = (int) (blue * 255);
        int a = (int) (alpha * 255);

        for (int i = renderStart; i < currentPath.size() - 1; i++) {
            Vec3d from = currentPath.get(i);
            Vec3d to = currentPath.get(i + 1);

            float x1 = (float) (from.x - cameraPos.x);
            float y1 = (float) (from.y + 0.01 - cameraPos.y);
            float z1 = (float) (from.z - cameraPos.z);
            float x2 = (float) (to.x - cameraPos.x);
            float y2 = (float) (to.y + 0.01 - cameraPos.y);
            float z2 = (float) (to.z - cameraPos.z);

            float dx = x2 - x1;
            float dz = z2 - z1;
            float hLen = (float) Math.sqrt(dx * dx + dz * dz);
            if (hLen < 1e-6f) continue;

            // Perpendicular offset for uniform width
            float perpX = (-dz / hLen) * LINE_HALF_WIDTH;
            float perpZ = (dx / hLen) * LINE_HALF_WIDTH;

            // Independent quad per segment — no shared vertices, uniform width
            buffer.vertex(x1 - perpX, y1, z1 - perpZ).color(r, g, b, a);
            buffer.vertex(x1 + perpX, y1, z1 + perpZ).color(r, g, b, a);
            buffer.vertex(x2 + perpX, y2, z2 + perpZ).color(r, g, b, a);
            buffer.vertex(x2 - perpX, y2, z2 - perpZ).color(r, g, b, a);
        }

        BuiltBuffer built = buffer.endNullable();
        if (built != null) {
            PATH_QUAD_LAYER.draw(built);
        }
    }
}