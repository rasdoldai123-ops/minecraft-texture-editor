package com.blockpainter;

/**
 * Helpers for the 16x16x16 voxel grid stored as ARGB ints.
 * Alpha == 0 means the voxel is empty.
 */
public final class VoxelData {
    public static final int SIZE = 16;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    public static final int AREA = SIZE * SIZE;

    private VoxelData() {}

    public static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    public static int pixelIndex(int x, int y) {
        return y * SIZE + x;
    }

    public static boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < SIZE && y < SIZE && z < SIZE;
    }

    public static boolean isEmpty(int argb) {
        return (argb >>> 24) == 0;
    }

    public static boolean isEmpty(int[] voxels, int x, int y, int z) {
        if (!inBounds(x, y, z)) return true;
        return isEmpty(voxels[index(x, y, z)]);
    }

    public static boolean allEmpty(int[] data) {
        for (int c : data) {
            if (!isEmpty(c)) return false;
        }
        return true;
    }

    public static int alpha(int argb) { return (argb >>> 24) & 0xFF; }
    public static int red(int argb) { return (argb >>> 16) & 0xFF; }
    public static int green(int argb) { return (argb >>> 8) & 0xFF; }
    public static int blue(int argb) { return argb & 0xFF; }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    public static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    /** Converts NativeImage ABGR order to ARGB. */
    public static int abgrToArgb(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
