package com.blockpainter.client.render;

import com.blockpainter.VoxelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Renders a 16x16 ARGB texture as a flat item, the same way vanilla "generated" item models look:
 * every opaque pixel becomes a thin box with front, back and edge faces.
 */
public final class PaintedItemRenderer {
    private static final float PX = 1.0f / 16.0f;
    private static final float Z0 = 7.5f / 16.0f;
    private static final float Z1 = 8.5f / 16.0f;

    private PaintedItemRenderer() {}

    public static void render(int[] pixels, MatrixStack matrices, VertexConsumerProvider vcp, int light, int overlay) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        Matrix3f nrm = entry.getNormalMatrix();

        VertexConsumer solid = null;
        VertexConsumer translucent = null;

        for (int py = 0; py < 16; py++) {
            for (int px = 0; px < 16; px++) {
                int color = pixels[VoxelData.pixelIndex(px, py)];
                if (VoxelData.isEmpty(color)) continue;

                VertexConsumer vc;
                if (VoxelData.alpha(color) >= 255) {
                    if (solid == null) solid = vcp.getBuffer(RenderLayer.getEntitySolid(PaintedBlockRenderer.WHITE_TEXTURE));
                    vc = solid;
                } else {
                    if (translucent == null) translucent = vcp.getBuffer(RenderLayer.getEntityTranslucent(PaintedBlockRenderer.WHITE_TEXTURE));
                    vc = translucent;
                }

                float x0 = px * PX, x1 = x0 + PX;
                float y0 = (15 - py) * PX, y1 = y0 + PX;

                // front / back
                quad(vc, pos, nrm, color, Direction.SOUTH, light, overlay, x0, y0, Z1, x1, y0, Z1, x1, y1, Z1, x0, y1, Z1);
                quad(vc, pos, nrm, color, Direction.NORTH, light, overlay, x1, y0, Z0, x0, y0, Z0, x0, y1, Z0, x1, y1, Z0);

                // edges only where the neighbor pixel is empty
                if (isEmpty(pixels, px - 1, py)) quad(vc, pos, nrm, color, Direction.WEST, light, overlay, x0, y0, Z0, x0, y0, Z1, x0, y1, Z1, x0, y1, Z0);
                if (isEmpty(pixels, px + 1, py)) quad(vc, pos, nrm, color, Direction.EAST, light, overlay, x1, y0, Z1, x1, y0, Z0, x1, y1, Z0, x1, y1, Z1);
                if (isEmpty(pixels, px, py - 1)) quad(vc, pos, nrm, color, Direction.UP, light, overlay, x0, y1, Z1, x1, y1, Z1, x1, y1, Z0, x0, y1, Z0);
                if (isEmpty(pixels, px, py + 1)) quad(vc, pos, nrm, color, Direction.DOWN, light, overlay, x0, y0, Z0, x1, y0, Z0, x1, y0, Z1, x0, y0, Z1);
            }
        }
    }

    private static boolean isEmpty(int[] pixels, int x, int y) {
        if (x < 0 || y < 0 || x >= 16 || y >= 16) return true;
        return VoxelData.isEmpty(pixels[VoxelData.pixelIndex(x, y)]);
    }

    private static void quad(VertexConsumer vc, Matrix4f pos, Matrix3f nrm, int color, Direction dir, int light, int overlay,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        float shade = dir == Direction.SOUTH || dir == Direction.NORTH ? 1.0f : VoxelMesh.shade(dir);
        int a = VoxelData.alpha(color);
        int r = (int) (VoxelData.red(color) * shade);
        int g = (int) (VoxelData.green(color) * shade);
        int b = (int) (VoxelData.blue(color) * shade);
        float nx = dir.getOffsetX(), ny = dir.getOffsetY(), nz = dir.getOffsetZ();

        vc.vertex(pos, ax, ay, az).color(r, g, b, a).texture(0, 0).overlay(overlay).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(pos, bx, by, bz).color(r, g, b, a).texture(1, 0).overlay(overlay).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(pos, cx, cy, cz).color(r, g, b, a).texture(1, 1).overlay(overlay).light(light).normal(nrm, nx, ny, nz).next();
        vc.vertex(pos, dx, dy, dz).color(r, g, b, a).texture(0, 1).overlay(overlay).light(light).normal(nrm, nx, ny, nz).next();
    }
}
