package com.blockpainter.client.render;

import com.blockpainter.BlockPainterMod;
import com.blockpainter.VoxelData;
import com.blockpainter.block.PaintedBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class PaintedBlockRenderer implements BlockEntityRenderer<PaintedBlockEntity> {
    public static final Identifier WHITE_TEXTURE = BlockPainterMod.id("textures/block/white.png");
    private static final float VOXEL = 1.0f / VoxelData.SIZE;

    private final float[] corners = new float[12];

    public PaintedBlockRenderer(BlockEntityRendererFactory.Context ctx) {
    }

    private record Cache(int version, VoxelMesh mesh) {}

    @Override
    public void render(PaintedBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        VoxelMesh mesh;
        if (be.renderCache instanceof Cache cache && cache.version() == be.getVersion()) {
            mesh = cache.mesh();
        } else {
            mesh = VoxelMesh.build(be.getVoxels(), -1, 0);
            be.renderCache = new Cache(be.getVersion(), mesh);
        }
        if (mesh.opaqueCount == 0 && mesh.translucentCount == 0) return;

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f pos = entry.getPositionMatrix();
        Matrix3f nrm = entry.getNormalMatrix();

        if (mesh.opaqueCount > 0) {
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(WHITE_TEXTURE));
            emit(vc, mesh.opaque, mesh.opaqueCount, pos, nrm, light, overlay);
        }
        if (mesh.translucentCount > 0) {
            VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(WHITE_TEXTURE));
            emit(vc, mesh.translucent, mesh.translucentCount, pos, nrm, light, overlay);
        }
    }

    private void emit(VertexConsumer vc, int[] faces, int count, Matrix4f pos, Matrix3f nrm, int light, int overlay) {
        for (int i = 0; i < count; i += 5) {
            Direction dir = Direction.byId(faces[i + 3]);
            int color = faces[i + 4];
            float shade = VoxelMesh.shade(dir);
            int a = VoxelData.alpha(color);
            int r = (int) (VoxelData.red(color) * shade);
            int g = (int) (VoxelData.green(color) * shade);
            int b = (int) (VoxelData.blue(color) * shade);

            VoxelMesh.faceCorners(faces[i], faces[i + 1], faces[i + 2], dir, VOXEL, corners);
            float nx = dir.getOffsetX(), ny = dir.getOffsetY(), nz = dir.getOffsetZ();
            for (int v = 0; v < 4; v++) {
                float u = (v == 1 || v == 2) ? 1f : 0f;
                float t = (v >= 2) ? 1f : 0f;
                vc.vertex(pos, corners[v * 3], corners[v * 3 + 1], corners[v * 3 + 2])
                        .color(r, g, b, a)
                        .texture(u, t)
                        .overlay(overlay)
                        .light(light)
                        .normal(nrm, nx, ny, nz)
                        .next();
            }
        }
    }
}
