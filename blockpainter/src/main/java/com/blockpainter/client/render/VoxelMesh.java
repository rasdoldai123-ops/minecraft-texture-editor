package com.blockpainter.client.render;

import com.blockpainter.VoxelData;
import net.minecraft.util.math.Direction;

/**
 * Compact list of visible voxel faces. Each face stores voxel x/y/z, the direction id and the ARGB color.
 * Faces are split into opaque and translucent groups so they can go to different render layers.
 */
public final class VoxelMesh {
    public final int[] opaque;
    public final int[] translucent;
    public final int opaqueCount;
    public final int translucentCount;

    private VoxelMesh(int[] opaque, int opaqueCount, int[] translucent, int translucentCount) {
        this.opaque = opaque;
        this.opaqueCount = opaqueCount;
        this.translucent = translucent;
        this.translucentCount = translucentCount;
    }

    public static VoxelMesh empty() {
        return new VoxelMesh(new int[0], 0, new int[0], 0);
    }

    /**
     * @param voxels     16^3 ARGB voxel grid
     * @param clipAxis   axis for cutaway (0=X,1=Y,2=Z) or -1 for none
     * @param clipLayer  voxels with coordinate along clipAxis greater than this are hidden
     */
    public static VoxelMesh build(int[] voxels, int clipAxis, int clipLayer) {
        int[] op = new int[6 * 5 * 512];
        int[] tr = new int[6 * 5 * 512];
        int oc = 0, tc = 0;

        for (int y = 0; y < VoxelData.SIZE; y++) {
            for (int z = 0; z < VoxelData.SIZE; z++) {
                for (int x = 0; x < VoxelData.SIZE; x++) {
                    int color = voxels[VoxelData.index(x, y, z)];
                    if (VoxelData.isEmpty(color)) continue;
                    if (isClipped(x, y, z, clipAxis, clipLayer)) continue;

                    int alpha = VoxelData.alpha(color);
                    for (Direction dir : Direction.values()) {
                        int nx = x + dir.getOffsetX();
                        int ny = y + dir.getOffsetY();
                        int nz = z + dir.getOffsetZ();
                        if (!faceVisible(voxels, color, nx, ny, nz, clipAxis, clipLayer)) continue;

                        if (alpha >= 255) {
                            if (oc + 5 > op.length) op = grow(op);
                            op[oc++] = x; op[oc++] = y; op[oc++] = z; op[oc++] = dir.getId(); op[oc++] = color;
                        } else {
                            if (tc + 5 > tr.length) tr = grow(tr);
                            tr[tc++] = x; tr[tc++] = y; tr[tc++] = z; tr[tc++] = dir.getId(); tr[tc++] = color;
                        }
                    }
                }
            }
        }
        return new VoxelMesh(op, oc, tr, tc);
    }

    private static boolean isClipped(int x, int y, int z, int axis, int layer) {
        if (axis < 0) return false;
        int c = axis == 0 ? x : (axis == 1 ? y : z);
        return c > layer;
    }

    private static boolean faceVisible(int[] voxels, int self, int nx, int ny, int nz, int clipAxis, int clipLayer) {
        if (!VoxelData.inBounds(nx, ny, nz)) return true;
        if (isClipped(nx, ny, nz, clipAxis, clipLayer)) return true;
        int neighbor = voxels[VoxelData.index(nx, ny, nz)];
        if (VoxelData.isEmpty(neighbor)) return true;
        // Show the face through a translucent neighbor of a different color.
        return VoxelData.alpha(neighbor) < 255 && neighbor != self;
    }

    private static int[] grow(int[] arr) {
        int[] n = new int[arr.length * 2];
        System.arraycopy(arr, 0, n, 0, arr.length);
        return n;
    }

    /** Per-face brightness matching vanilla block shading. */
    public static float shade(Direction dir) {
        return switch (dir) {
            case UP -> 1.0f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.6f;
        };
    }

    /**
     * Fills {@code out} (12 floats) with the 4 corners of the given face of the unit cube at (x,y,z) scaled by {@code s}.
     * Winding is counter-clockwise when viewed from outside.
     */
    public static void faceCorners(int x, int y, int z, Direction dir, float s, float[] out) {
        float x0 = x * s, y0 = y * s, z0 = z * s;
        float x1 = x0 + s, y1 = y0 + s, z1 = z0 + s;
        switch (dir) {
            case UP -> put(out, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
            case DOWN -> put(out, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case NORTH -> put(out, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
            case SOUTH -> put(out, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            case WEST -> put(out, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            case EAST -> put(out, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        }
    }

    private static void put(float[] o, float... v) {
        System.arraycopy(v, 0, o, 0, 12);
    }
}
