package com.blockpainter.block;

import com.blockpainter.BlockPainterMod;
import com.blockpainter.VoxelData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class PaintedBlockEntity extends BlockEntity {
    private static final String KEY_ORIGINAL = "Original";
    private static final String KEY_VOXELS = "Voxels";

    private int[] voxels = new int[VoxelData.VOLUME];
    private BlockState originalState = Blocks.STONE.getDefaultState();

    /** Incremented on every data change so the client renderer can cache its mesh. */
    private int version = 0;
    /** Client-only render cache, owned by the renderer. */
    @Nullable
    public Object renderCache;

    public PaintedBlockEntity(BlockPos pos, BlockState state) {
        super(BlockPainterMod.PAINTED_BLOCK_ENTITY, pos, state);
    }

    public int[] getVoxels() {
        return voxels;
    }

    public int getVersion() {
        return version;
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public void setOriginalState(BlockState state) {
        this.originalState = state;
        version++;
        markDirty();
    }

    public void setVoxels(int[] data) {
        if (data.length != VoxelData.VOLUME) {
            throw new IllegalArgumentException("Voxel array must have " + VoxelData.VOLUME + " entries");
        }
        this.voxels = data.clone();
        version++;
        markDirty();
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put(KEY_ORIGINAL, NbtHelper.fromBlockState(originalState));
        nbt.putIntArray(KEY_VOXELS, voxels);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        if (nbt.contains(KEY_ORIGINAL)) {
            originalState = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), nbt.getCompound(KEY_ORIGINAL));
        }
        int[] data = nbt.getIntArray(KEY_VOXELS);
        if (data.length == VoxelData.VOLUME) {
            voxels = data;
        } else {
            voxels = new int[VoxelData.VOLUME];
        }
        version++;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }
}
