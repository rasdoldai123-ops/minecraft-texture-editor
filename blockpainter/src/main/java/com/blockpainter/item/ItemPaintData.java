package com.blockpainter.item;

import com.blockpainter.VoxelData;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

/** Stores a custom 16x16 ARGB texture in item NBT. */
public final class ItemPaintData {
    public static final String KEY = "BlockPainterTexture";

    private ItemPaintData() {}

    public static boolean has(ItemStack stack) {
        if (stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(KEY, NbtElement.INT_ARRAY_TYPE);
    }

    @Nullable
    public static int[] get(ItemStack stack) {
        if (!has(stack)) return null;
        int[] data = stack.getNbt().getIntArray(KEY);
        return data.length == VoxelData.AREA ? data : null;
    }

    public static void set(ItemStack stack, int[] pixels) {
        if (pixels.length != VoxelData.AREA) {
            throw new IllegalArgumentException("Texture must be 16x16");
        }
        stack.getOrCreateNbt().putIntArray(KEY, pixels.clone());
    }

    public static void clear(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null) return;
        nbt.remove(KEY);
        if (nbt.isEmpty()) stack.setNbt(null);
    }
}
