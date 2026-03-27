package dev.toba.client.api.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.gl.RenderPipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class ESPRenderer {
    private static ESPRenderer instance;

    private boolean enableEntityESP = true;
    private boolean enableBlockESP = true;
    private ESPMode espMode = ESPMode.BOTH;

    private final List<ESPTarget> entityTargets = new ArrayList<>();
    private final List<ESPTarget> blockTargets = new ArrayList<>();
    private final List<ESPTarget> pathTargets = new ArrayList<>();
    private final List<PathLineSegment> pathLineSegments = new ArrayList<>();

    private static final RenderPipeline ESP_FILLED_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.POSITION_COLOR_SNIPPET
            )
            .withLocation(Identifier.of("toba", "pipeline/esp_filled"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderPipeline ESP_LINE_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.RENDERTYPE_LINES_SNIPPET
            )
            .withLocation(Identifier.of("toba", "pipeline/esp_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthWrite(false)
            .withCull(false)
            .build();

    private static final RenderLayer ESP_FILLED_LAYER = RenderLayer.of(
            "toba_esp_filled",
            RenderSetup.builder(ESP_FILLED_PIPELINE).translucent().build()
    );

    private static final RenderLayer ESP_LINE_LAYER = RenderLayer.of(
            "toba_esp_lines",
            RenderSetup.builder(ESP_LINE_PIPELINE).translucent().build()
    );

    public static ESPRenderer getInstance() {
        if (instance == null) {
            instance = new ESPRenderer();
        }
        return instance;
    }

    public void addEntity(Entity entity, float tickDelta, float red, float green, float blue, float alpha) {
        if (!enableEntityESP) return;
        Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
        Box baseBox = entity.getBoundingBox();
        Vec3d entityPos = entity.getEntityPos();
        Box box = baseBox.offset(lerpedPos.subtract(entityPos));
        entityTargets.add(new ESPTarget(box, red, green, blue, alpha, TargetType.ENTITY));
    }

    public void beginBlockScan() {
        blockTargets.clear();
    }

    public void addBlock(BlockPos pos, float red, float green, float blue, float alpha) {
        if (!enableBlockESP) return;
        Box box = new Box(pos);
        blockTargets.add(new ESPTarget(box, red, green, blue, alpha, TargetType.BLOCK));
    }

    public void addBox(Box box, float red, float green, float blue, float alpha) {
        entityTargets.add(new ESPTarget(box, red, green, blue, alpha, TargetType.CUSTOM));
    }

    public void beginPathScan() {
        pathTargets.clear();
        pathLineSegments.clear();
    }

    public void addPathBlock(BlockPos pos, float red, float green, float blue, float alpha) {
        Box box = new Box(pos);
        pathTargets.add(new ESPTarget(box, red, green, blue, alpha, TargetType.BLOCK));
    }

    public void addPathWaypoint(Vec3d pos, float red, float green, float blue, float alpha) {
        double size = 0.1;
        Box box = new Box(
                pos.x - size, pos.y - size, pos.z - size,
                pos.x + size, pos.y + size, pos.z + size
        );
        pathTargets.add(new ESPTarget(box, red, green, blue, alpha, TargetType.BLOCK));
    }

    public void addPathLine(Vec3d from, Vec3d to, float red, float green, float blue, float alpha) {
        pathLineSegments.add(new PathLineSegment(from, to, red, green, blue, alpha));
    }

    public void render(WorldRenderContext context) {
        if (entityTargets.isEmpty() && blockTargets.isEmpty() && pathTargets.isEmpty() && pathLineSegments.isEmpty()) return;

        Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        Tessellator tessellator = Tessellator.getInstance();

        if (espMode == ESPMode.FILLED || espMode == ESPMode.BOTH) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (ESPTarget target : entityTargets) {
                drawFilledBox(buffer, target, cameraPos);
            }
            for (ESPTarget target : blockTargets) {
                drawFilledBox(buffer, target, cameraPos);
            }
            for (ESPTarget target : pathTargets) {
                drawFilledBox(buffer, target, cameraPos);
            }
            BuiltBuffer built = buffer.endNullable();
            if (built != null) {
                ESP_FILLED_LAYER.draw(built);
            }
        }

        if (espMode == ESPMode.OUTLINED || espMode == ESPMode.BOTH) {
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL);
            for (ESPTarget target : entityTargets) {
                drawOutlinedBox(buffer, target, cameraPos);
            }
            for (ESPTarget target : blockTargets) {
                drawOutlinedBox(buffer, target, cameraPos);
            }
            for (ESPTarget target : pathTargets) {
                drawOutlinedBox(buffer, target, cameraPos);
            }
            BuiltBuffer built = buffer.endNullable();
            if (built != null) {
                ESP_LINE_LAYER.draw(built);
            }
        }

        if (!pathLineSegments.isEmpty()) {
            BufferBuilder ribbonBuffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (PathLineSegment seg : pathLineSegments) {
                drawFloorRibbon(ribbonBuffer, seg, cameraPos);
            }
            BuiltBuffer ribbonBuilt = ribbonBuffer.endNullable();
            if (ribbonBuilt != null) {
                ESP_FILLED_LAYER.draw(ribbonBuilt);
            }
        }

        entityTargets.clear();
    }

    private void drawFilledBox(BufferBuilder buffer, ESPTarget target, Vec3d cam) {
        float x1 = (float) (target.box.minX - cam.x);
        float y1 = (float) (target.box.minY - cam.y);
        float z1 = (float) (target.box.minZ - cam.z);
        float x2 = (float) (target.box.maxX - cam.x);
        float y2 = (float) (target.box.maxY - cam.y);
        float z2 = (float) (target.box.maxZ - cam.z);
        int r = (int) (target.red * 255);
        int g = (int) (target.green * 255);
        int b = (int) (target.blue * 255);
        int a = (int) (target.alpha * 0.3f * 255);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
        buffer.vertex(x1, y1, z2).color(r, g, b, a);

        buffer.vertex(x1, y2, z1).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x1, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y1, z1).color(r, g, b, a);

        buffer.vertex(x1, y1, z2).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);

        buffer.vertex(x1, y1, z1).color(r, g, b, a);
        buffer.vertex(x1, y1, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z2).color(r, g, b, a);
        buffer.vertex(x1, y2, z1).color(r, g, b, a);

        buffer.vertex(x2, y1, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z1).color(r, g, b, a);
        buffer.vertex(x2, y2, z2).color(r, g, b, a);
        buffer.vertex(x2, y1, z2).color(r, g, b, a);
    }

    private void drawOutlinedBox(BufferBuilder buffer, ESPTarget target, Vec3d cam) {
        float x1 = (float) (target.box.minX - cam.x);
        float y1 = (float) (target.box.minY - cam.y);
        float z1 = (float) (target.box.minZ - cam.z);
        float x2 = (float) (target.box.maxX - cam.x);
        float y2 = (float) (target.box.maxY - cam.y);
        float z2 = (float) (target.box.maxZ - cam.z);
        int r = (int) (target.red * 255);
        int g = (int) (target.green * 255);
        int b = (int) (target.blue * 255);
        int a = (int) (target.alpha * 255);

        line(buffer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(buffer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(buffer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void drawFloorRibbon(BufferBuilder buffer, PathLineSegment seg, Vec3d cam) {
        double dx = seg.to.x - seg.from.x;
        double dz = seg.to.z - seg.from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.001) return;

        double halfWidth = 0.04;
        double perpX = (-dz / len) * halfWidth;
        double perpZ = (dx / len) * halfWidth;

        float y1 = (float) (seg.from.y + 0.02 - cam.y);
        float y2 = (float) (seg.to.y + 0.02 - cam.y);

        float ax = (float) (seg.from.x + perpX - cam.x);
        float az = (float) (seg.from.z + perpZ - cam.z);
        float bx = (float) (seg.from.x - perpX - cam.x);
        float bz = (float) (seg.from.z - perpZ - cam.z);
        float cx = (float) (seg.to.x - perpX - cam.x);
        float cz = (float) (seg.to.z - perpZ - cam.z);
        float ex = (float) (seg.to.x + perpX - cam.x);
        float ez = (float) (seg.to.z + perpZ - cam.z);

        int r = (int) (seg.red * 255);
        int g = (int) (seg.green * 255);
        int b = (int) (seg.blue * 255);
        int a = (int) (seg.alpha * 255);

        buffer.vertex(ax, y1, az).color(r, g, b, a);
        buffer.vertex(bx, y1, bz).color(r, g, b, a);
        buffer.vertex(cx, y2, cz).color(r, g, b, a);
        buffer.vertex(ex, y2, ez).color(r, g, b, a);
    }

    private void line(BufferBuilder buffer, float x1, float y1, float z1, float x2, float y2, float z2,
                      int r, int g, int b, int a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return;
        float nx = dx / len;
        float ny = dy / len;
        float nz = dz / len;

        buffer.vertex(x1, y1, z1).color(r, g, b, a).normal(nx, ny, nz);
        buffer.vertex(x2, y2, z2).color(r, g, b, a).normal(nx, ny, nz);
    }

    public void setEntityESP(boolean enabled) { this.enableEntityESP = enabled; }
    public void setBlockESP(boolean enabled) { this.enableBlockESP = enabled; }
    public void setESPMode(ESPMode mode) { this.espMode = mode; }
    public boolean isEntityESPEnabled() { return enableEntityESP; }
    public boolean isBlockESPEnabled() { return enableBlockESP; }
    public ESPMode getESPMode() { return espMode; }

    public enum ESPMode {
        FILLED,
        OUTLINED,
        BOTH
    }

    public enum TargetType {
        ENTITY,
        BLOCK,
        CUSTOM
    }

    private static class ESPTarget {
        final Box box;
        final float red, green, blue, alpha;
        final TargetType type;

        ESPTarget(Box box, float red, float green, float blue, float alpha, TargetType type) {
            this.box = box;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.type = type;
        }
    }

    private static class PathLineSegment {
        final Vec3d from, to;
        final float red, green, blue, alpha;

        PathLineSegment(Vec3d from, Vec3d to, float red, float green, float blue, float alpha) {
            this.from = from;
            this.to = to;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }
}