package com.blockpainter.client.screen;

import com.blockpainter.VoxelData;
import com.blockpainter.client.render.VoxelMesh;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Rotatable 3D preview of the voxel grid with mouse picking. Supports a cutaway along one axis
 * and a highlighted editing slice so the player can paint inside the block.
 */
public final class VoxelView {
    private int x, y, size;
    private float yaw = -35f;
    private float pitch = 28f;
    private float zoom = 1f;

    public int sliceAxis = 1;
    public int sliceLayer = 15;
    public boolean cutaway = false;
    public boolean showSlice = true;

    private VoxelMesh mesh = VoxelMesh.empty();
    private int meshVersion = -1;

    // last pick result
    public boolean hasHit = false;
    public boolean planeHit = false;
    public int hitX, hitY, hitZ;
    public Direction hitFace = Direction.UP;

    private final float[] corners = new float[12];

    public void setBounds(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getSize() { return size; }

    public boolean contains(double mx, double my) {
        return mx >= x && my >= y && mx < x + size && my < y + size;
    }

    public void rotate(double dx, double dy) {
        yaw += (float) dx * 0.8f;
        pitch = Math.max(-89f, Math.min(89f, pitch + (float) dy * 0.8f));
    }

    public void zoom(double amount) {
        zoom = Math.max(0.4f, Math.min(3f, zoom * (float) Math.pow(1.15, amount)));
    }

    public void invalidate() {
        meshVersion = -1;
    }

    private void ensureMesh(int[] voxels, int version) {
        if (meshVersion != version) {
            mesh = VoxelMesh.build(voxels, cutaway ? sliceAxis : -1, sliceLayer);
            meshVersion = version;
        }
    }

    private Matrix4f modelMatrix() {
        float s = size / 30f * zoom;
        return new Matrix4f()
                .translate(x + size / 2f, y + size / 2f, 300f)
                .scale(s, -s, s)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-8f, -8f, -8f);
    }

    // ---------------------------------------------------------------- rendering

    public void render(DrawContext ctx, int[] voxels, int version) {
        ensureMesh(voxels, version);

        ctx.fill(x, y, x + size, y + size, 0xFF1B1B1F);
        ctx.drawBorder(x - 1, y - 1, size + 2, size + 2, 0xFF000000);
        ctx.draw();

        ctx.enableScissor(x, y, x + size, y + size);

        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.multiplyPositionMatrix(modelMatrix());
        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder bb = tess.getBuffer();

        // bounding box wire
        bb.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        wireBox(bb, m, 0, 0, 0, 16, 16, 16, 0xFF5A5A66);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        emitFaces(bb, m, mesh.opaque, mesh.opaqueCount);
        emitFaces(bb, m, mesh.translucent, mesh.translucentCount);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        // editing slice
        if (showSlice) {
            RenderSystem.depthMask(false);
            bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float lo = sliceLayer, hi = sliceLayer + 1;
            switch (sliceAxis) {
                case 0 -> { slab(bb, m, lo, 0, 0, hi, 16, 16, 0x3040A0FF); }
                case 1 -> { slab(bb, m, 0, lo, 0, 16, hi, 16, 0x3040A0FF); }
                default -> { slab(bb, m, 0, 0, lo, 16, 16, hi, 0x3040A0FF); }
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
            RenderSystem.depthMask(true);

            bb.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            switch (sliceAxis) {
                case 0 -> wireBox(bb, m, lo, 0, 0, hi, 16, 16, 0xFF60B0FF);
                case 1 -> wireBox(bb, m, 0, lo, 0, 16, hi, 16, 0xFF60B0FF);
                default -> wireBox(bb, m, 0, 0, lo, 16, 16, hi, 0xFF60B0FF);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }

        // hit highlight
        if (hasHit) {
            RenderSystem.disableDepthTest();
            bb.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            wireBox(bb, m, hitX - 0.02f, hitY - 0.02f, hitZ - 0.02f, hitX + 1.02f, hitY + 1.02f, hitZ + 1.02f, planeHit ? 0xFFFFD040 : 0xFFFFFFFF);
            BufferRenderer.drawWithGlobalProgram(bb.end());
            RenderSystem.enableDepthTest();
        }

        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        ms.pop();
        ctx.disableScissor();
    }

    private void emitFaces(BufferBuilder bb, Matrix4f m, int[] faces, int count) {
        for (int i = 0; i < count; i += 5) {
            Direction dir = Direction.byId(faces[i + 3]);
            int color = faces[i + 4];
            float shade = VoxelMesh.shade(dir);
            int a = VoxelData.alpha(color);
            int r = (int) (VoxelData.red(color) * shade);
            int g = (int) (VoxelData.green(color) * shade);
            int b = (int) (VoxelData.blue(color) * shade);
            VoxelMesh.faceCorners(faces[i], faces[i + 1], faces[i + 2], dir, 1f, corners);
            for (int v = 0; v < 4; v++) {
                bb.vertex(m, corners[v * 3], corners[v * 3 + 1], corners[v * 3 + 2]).color(r, g, b, a).next();
            }
        }
    }

    private static void slab(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, g = (color >>> 8) & 0xFF, b = color & 0xFF;
        // six faces of the slab
        quad(bb, m, r, g, b, a, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(bb, m, r, g, b, a, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(bb, m, r, g, b, a, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(bb, m, r, g, b, a, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(bb, m, r, g, b, a, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(bb, m, r, g, b, a, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, int r, int g, int b, int a, float... v) {
        for (int i = 0; i < 4; i++) {
            bb.vertex(m, v[i * 3], v[i * 3 + 1], v[i * 3 + 2]).color(r, g, b, a).next();
        }
    }

    private static void wireBox(BufferBuilder bb, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, g = (color >>> 8) & 0xFF, b = color & 0xFF;
        float[][] p = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}
        };
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : e) {
            float[] u = p[edge[0]], w = p[edge[1]];
            bb.vertex(m, u[0], u[1], u[2]).color(r, g, b, a).next();
            bb.vertex(m, w[0], w[1], w[2]).color(r, g, b, a).next();
        }
    }

    // ---------------------------------------------------------------- picking

    /** Casts a ray from the mouse into the voxel grid. Updates hit fields. */
    public void pick(int[] voxels, double mouseX, double mouseY) {
        hasHit = false;
        planeHit = false;
        if (!contains(mouseX, mouseY)) return;

        Matrix4f inv = modelMatrix().invert();
        Vector3f o = inv.transformPosition(new Vector3f((float) mouseX, (float) mouseY, 2000f));
        Vector3f d = inv.transformDirection(new Vector3f(0f, 0f, -1f)).normalize();

        double best = Double.MAX_VALUE;
        int[] entry = new int[1];

        for (int vy = 0; vy < 16; vy++) {
            for (int vz = 0; vz < 16; vz++) {
                for (int vx = 0; vx < 16; vx++) {
                    int c = voxels[VoxelData.index(vx, vy, vz)];
                    if (VoxelData.isEmpty(c)) continue;
                    if (cutaway) {
                        int along = sliceAxis == 0 ? vx : (sliceAxis == 1 ? vy : vz);
                        if (along > sliceLayer) continue;
                    }
                    double t = rayBox(o, d, vx, vy, vz, vx + 1, vy + 1, vz + 1, entry);
                    if (t >= 0 && t < best) {
                        best = t;
                        hasHit = true;
                        hitX = vx; hitY = vy; hitZ = vz;
                        hitFace = faceFromEntry(entry[0], d);
                    }
                }
            }
        }

        if (!hasHit && showSlice) {
            float lo = sliceLayer, hi = sliceLayer + 1;
            double t = switch (sliceAxis) {
                case 0 -> rayBox(o, d, lo, 0, 0, hi, 16, 16, entry);
                case 1 -> rayBox(o, d, 0, lo, 0, 16, hi, 16, entry);
                default -> rayBox(o, d, 0, 0, lo, 16, 16, hi, entry);
            };
            if (t >= 0) {
                float px = o.x + d.x * (float) t, py = o.y + d.y * (float) t, pz = o.z + d.z * (float) t;
                int cx = clamp16((int) Math.floor(px)), cy = clamp16((int) Math.floor(py)), cz = clamp16((int) Math.floor(pz));
                switch (sliceAxis) {
                    case 0 -> cx = sliceLayer;
                    case 1 -> cy = sliceLayer;
                    default -> cz = sliceLayer;
                }
                hasHit = true;
                planeHit = true;
                hitX = cx; hitY = cy; hitZ = cz;
                hitFace = faceFromEntry(entry[0], d);
            }
        }
    }

    private static int clamp16(int v) {
        return v < 0 ? 0 : (v > 15 ? 15 : v);
    }

    private static Direction faceFromEntry(int axis, Vector3f d) {
        return switch (axis) {
            case 0 -> d.x > 0 ? Direction.WEST : Direction.EAST;
            case 1 -> d.y > 0 ? Direction.DOWN : Direction.UP;
            default -> d.z > 0 ? Direction.NORTH : Direction.SOUTH;
        };
    }

    /** Slab test. Returns entry distance or -1. entryAxis[0] receives the axis of the entry face. */
    private static double rayBox(Vector3f o, Vector3f d, float x0, float y0, float z0, float x1, float y1, float z1, int[] entryAxis) {
        double tmin = -Double.MAX_VALUE, tmax = Double.MAX_VALUE;
        int axis = 0;
        float[] oo = {o.x, o.y, o.z};
        float[] dd = {d.x, d.y, d.z};
        float[] lo = {x0, y0, z0};
        float[] hi = {x1, y1, z1};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dd[i]) < 1e-7f) {
                if (oo[i] < lo[i] || oo[i] > hi[i]) return -1;
                continue;
            }
            double t1 = (lo[i] - oo[i]) / dd[i];
            double t2 = (hi[i] - oo[i]) / dd[i];
            double near = Math.min(t1, t2), far = Math.max(t1, t2);
            if (near > tmin) {
                tmin = near;
                axis = i;
            }
            tmax = Math.min(tmax, far);
            if (tmin > tmax) return -1;
        }
        if (tmax < 0) return -1;
        entryAxis[0] = axis;
        return tmin < 0 ? 0 : tmin;
    }
}
