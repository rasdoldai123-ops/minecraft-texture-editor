package com.blockpainter.network;

import com.blockpainter.BlockPainterMod;
import com.blockpainter.VoxelData;
import com.blockpainter.block.PaintedBlock;
import com.blockpainter.block.PaintedBlockEntity;
import com.blockpainter.item.ItemPaintData;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client -> server packets. All validation happens on the server so a modified client cannot
 * paint blocks far away or corrupt arbitrary block entities.
 */
public final class PaintNetworking {
    public static final Identifier PAINT_ITEM = BlockPainterMod.id("paint_item");
    public static final Identifier PAINT_BLOCK = BlockPainterMod.id("paint_block");
    public static final Identifier RESTORE_BLOCK = BlockPainterMod.id("restore_block");

    private static final double MAX_DISTANCE_SQ = 10.0 * 10.0;

    private PaintNetworking() {}

    public static PacketByteBuf writePaintItem(Hand hand, int[] pixels) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(hand);
        buf.writeBoolean(pixels != null);
        if (pixels != null) buf.writeIntArray(pixels);
        return buf;
    }

    public static PacketByteBuf writePaintBlock(BlockPos pos, int[] voxels) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeIntArray(voxels);
        return buf;
    }

    public static PacketByteBuf writeRestoreBlock(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        return buf;
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(PAINT_ITEM, (server, player, handler, buf, responseSender) -> {
            Hand hand = buf.readEnumConstant(Hand.class);
            boolean hasPixels = buf.readBoolean();
            int[] pixels = hasPixels ? buf.readIntArray(VoxelData.AREA) : null;
            server.execute(() -> handlePaintItem(player, hand, pixels));
        });

        ServerPlayNetworking.registerGlobalReceiver(PAINT_BLOCK, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int[] voxels = buf.readIntArray(VoxelData.VOLUME);
            server.execute(() -> handlePaintBlock(player, pos, voxels));
        });

        ServerPlayNetworking.registerGlobalReceiver(RESTORE_BLOCK, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> handleRestoreBlock(player, pos));
        });
    }

    private static void handlePaintItem(ServerPlayerEntity player, Hand hand, int[] pixels) {
        if (pixels != null && pixels.length != VoxelData.AREA) return;
        ItemStack stack = player.getStackInHand(hand);
        if (stack.isEmpty()) return;
        if (pixels == null) {
            ItemPaintData.clear(stack);
        } else {
            ItemPaintData.set(stack, pixels);
        }
        player.getInventory().markDirty();
    }

    private static boolean canEdit(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        if (!world.isChunkLoaded(pos)) return false;
        if (player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > MAX_DISTANCE_SQ) return false;
        if (!world.canPlayerModifyAt(player, pos)) return false;
        return true;
    }

    private static void handlePaintBlock(ServerPlayerEntity player, BlockPos pos, int[] voxels) {
        if (voxels.length != VoxelData.VOLUME) return;
        if (!canEdit(player, pos)) return;
        ServerWorld world = player.getServerWorld();
        BlockState current = world.getBlockState(pos);

        if (current.getBlock() instanceof PaintedBlock) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof PaintedBlockEntity painted) {
                painted.setVoxels(voxels);
            }
            return;
        }

        if (current.isAir()) {
            player.sendMessage(Text.translatable("blockpainter.msg.air"), true);
            return;
        }
        if (current.hasBlockEntity()) {
            player.sendMessage(Text.translatable("blockpainter.msg.has_entity"), true);
            return;
        }
        if (!current.getFluidState().isEmpty() && current.getBlock().getDefaultState().getFluidState().isStill()) {
            player.sendMessage(Text.translatable("blockpainter.msg.fluid"), true);
            return;
        }

        world.setBlockState(pos, BlockPainterMod.PAINTED_BLOCK.getDefaultState(), 3);
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PaintedBlockEntity painted) {
            painted.setOriginalState(current);
            painted.setVoxels(voxels);
        }
    }

    private static void handleRestoreBlock(ServerPlayerEntity player, BlockPos pos) {
        if (!canEdit(player, pos)) return;
        ServerWorld world = player.getServerWorld();
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PaintedBlockEntity painted) {
            BlockState original = painted.getOriginalState();
            world.setBlockState(pos, original, 3);
        }
    }
}
