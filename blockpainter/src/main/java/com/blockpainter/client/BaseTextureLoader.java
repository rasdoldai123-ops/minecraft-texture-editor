package com.blockpainter.client;

import com.blockpainter.BlockPainterMod;
import com.blockpainter.VoxelData;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Reads vanilla / resource-pack textures so the player can use them as a starting point.
 */
public final class BaseTextureLoader {
    private static final int FALLBACK = 0xFF8F8F8F;

    private BaseTextureLoader() {}

    /** 16x16 ARGB texture of the item's model (first animation frame). */
    public static int[] itemTexture(ItemStack stack) {
        MinecraftClient client = MinecraftClient.getInstance();
        int[] out = new int[VoxelData.AREA];
        try {
            BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
            Sprite sprite = model.getParticleSprite();
            int[] tex = readSprite(sprite);
            if (tex != null) {
                if (model.hasDepth()) {
                    return tex;
                }
                // Items with tint (leather, potions, spawn eggs) use layer colors; apply layer 0 tint.
                int tint = client.getItemColors().getColor(stack, 0);
                if ((tint & 0xFFFFFF) != 0xFFFFFF) {
                    for (int i = 0; i < tex.length; i++) tex[i] = multiply(tex[i], tint);
                }
                return tex;
            }
        } catch (Exception e) {
            BlockPainterMod.LOGGER.warn("Failed to read item texture", e);
        }
        java.util.Arrays.fill(out, FALLBACK);
        return out;
    }

    /** 16^3 voxel grid built from the block model's six face textures. */
    public static int[] blockVoxels(BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        int[] voxels = new int[VoxelData.VOLUME];
        int[][] faces = new int[6][];
        long sumR = 0, sumG = 0, sumB = 0, n = 0;

        try {
            BakedModel model = client.getBlockRenderManager().getModel(state);
            Random random = Random.create(42L);
            for (Direction dir : Direction.values()) {
                List<BakedQuad> quads = model.getQuads(state, dir, random);
                BakedQuad quad = quads.isEmpty() ? findQuad(model.getQuads(state, null, random), dir) : quads.get(0);
                Sprite sprite = quad != null ? quad.getSprite() : model.getParticleSprite();
                int[] tex = readSprite(sprite);
                if (tex == null) {
                    tex = new int[VoxelData.AREA];
                    java.util.Arrays.fill(tex, FALLBACK);
                }
                if (quad != null && quad.hasColor()) {
                    int tint = client.getBlockColors().getColor(state, null, null, quad.getColorIndex());
                    for (int i = 0; i < tex.length; i++) tex[i] = multiply(tex[i], tint);
                }
                faces[dir.getId()] = tex;
                for (int c : tex) {
                    if (VoxelData.isEmpty(c)) continue;
                    sumR += VoxelData.red(c); sumG += VoxelData.green(c); sumB += VoxelData.blue(c); n++;
                }
            }
        } catch (Exception e) {
            BlockPainterMod.LOGGER.warn("Failed to read block textures", e);
            for (int i = 0; i < 6; i++) {
                faces[i] = new int[VoxelData.AREA];
                java.util.Arrays.fill(faces[i], FALLBACK);
            }
            n = 0;
        }

        int interior = n == 0 ? FALLBACK : VoxelData.argb(255, (int) (sumR / n), (int) (sumG / n), (int) (sumB / n));
        boolean fillInterior = state.isOpaque();

        if (fillInterior) {
            java.util.Arrays.fill(voxels, interior);
        }

        for (int a = 0; a < 16; a++) {
            for (int b = 0; b < 16; b++) {
                // UP: u=x, v=z (north edge is texture top)
                put(voxels, a, 15, b, faces[Direction.UP.getId()], a, b);
                // DOWN: u=x, v=z
                put(voxels, a, 0, b, faces[Direction.DOWN.getId()], a, b);
                // NORTH (z=0): u = 15-x, v = 15-y
                put(voxels, 15 - a, b, 0, faces[Direction.NORTH.getId()], a, 15 - b);
                // SOUTH (z=15): u = x, v = 15-y
                put(voxels, a, b, 15, faces[Direction.SOUTH.getId()], a, 15 - b);
                // WEST (x=0): u = z, v = 15-y
                put(voxels, 0, b, a, faces[Direction.WEST.getId()], a, 15 - b);
                // EAST (x=15): u = 15-z, v = 15-y
                put(voxels, 15, b, 15 - a, faces[Direction.EAST.getId()], a, 15 - b);
            }
        }
        return voxels;
    }

    private static void put(int[] voxels, int x, int y, int z, int[] tex, int u, int v) {
        int c = tex[VoxelData.pixelIndex(u, v)];
        int idx = VoxelData.index(x, y, z);
        if (VoxelData.isEmpty(c)) {
            // transparent texel: keep whatever the interior fill decided unless the block is non-opaque
            if (VoxelData.isEmpty(voxels[idx])) return;
            return;
        }
        voxels[idx] = c;
    }

    @Nullable
    private static BakedQuad findQuad(List<BakedQuad> quads, Direction dir) {
        for (BakedQuad q : quads) {
            if (q.getFace() == dir) return q;
        }
        return quads.isEmpty() ? null : quads.get(0);
    }

    /** Reads the sprite's source PNG and resamples the first frame to 16x16. */
    @Nullable
    public static int[] readSprite(@Nullable Sprite sprite) {
        if (sprite == null) return null;
        Identifier id = sprite.getContents().getId();
        Identifier tex = new Identifier(id.getNamespace(), "textures/" + id.getPath() + ".png");
        Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(tex);
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int frame = Math.min(img.getHeight(), w);
            int[] out = new int[VoxelData.AREA];
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    int sx = x * w / 16;
                    int sy = y * frame / 16;
                    out[VoxelData.pixelIndex(x, y)] = VoxelData.abgrToArgb(img.getColor(sx, sy));
                }
            }
            return out;
        } catch (Exception e) {
            BlockPainterMod.LOGGER.warn("Failed to read texture {}", tex, e);
            return null;
        }
    }

    private static int multiply(int argb, int tint) {
        if (VoxelData.isEmpty(argb)) return argb;
        int r = VoxelData.red(argb) * VoxelData.red(tint) / 255;
        int g = VoxelData.green(argb) * VoxelData.green(tint) / 255;
        int b = VoxelData.blue(argb) * VoxelData.blue(tint) / 255;
        return VoxelData.argb(VoxelData.alpha(argb), r, g, b);
    }
}
