package com.blockpainter.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.List;

/**
 * A block that remembers which block it replaced and renders a 16x16x16 voxel texture
 * painted by the player. Rendering is done by {@code PaintedBlockRenderer} on the client.
 */
public class PaintedBlock extends BlockWithEntity {

    public PaintedBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PaintedBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    public boolean isSideInvisible(BlockState state, BlockState stateFrom, Direction direction) {
        return false;
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        BlockEntity be = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (be instanceof PaintedBlockEntity painted) {
            BlockState original = painted.getOriginalState();
            if (original != null && !original.isAir()) {
                return original.getDroppedStacks(builder);
            }
        }
        return super.getDroppedStacks(state, builder);
    }

    @Override
    public ItemStack getPickStack(BlockView world, BlockPos pos, BlockState state) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof PaintedBlockEntity painted) {
            BlockState original = painted.getOriginalState();
            if (original != null) {
                return new ItemStack(original.getBlock());
            }
        }
        return ItemStack.EMPTY;
    }

    public static BlockEntityType<PaintedBlockEntity> type() {
        return com.blockpainter.BlockPainterMod.PAINTED_BLOCK_ENTITY;
    }
}
