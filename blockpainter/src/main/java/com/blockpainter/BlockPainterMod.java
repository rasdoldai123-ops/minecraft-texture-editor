package com.blockpainter;

import com.blockpainter.block.PaintedBlock;
import com.blockpainter.block.PaintedBlockEntity;
import com.blockpainter.network.PaintNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockPainterMod implements ModInitializer {
    public static final String MOD_ID = "blockpainter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final PaintedBlock PAINTED_BLOCK = new PaintedBlock(
            AbstractBlock.Settings.create()
                    .strength(1.5f, 6.0f)
                    .sounds(BlockSoundGroup.STONE)
                    .nonOpaque()
                    .dynamicBounds()
                    .allowsSpawning((state, world, pos, type) -> false)
                    .solidBlock((state, world, pos) -> false)
                    .suffocates((state, world, pos) -> false)
                    .blockVision((state, world, pos) -> false)
    );

    public static BlockEntityType<PaintedBlockEntity> PAINTED_BLOCK_ENTITY;

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("painted_block"), PAINTED_BLOCK);
        PAINTED_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                id("painted_block"),
                FabricBlockEntityTypeBuilder.create(PaintedBlockEntity::new, PAINTED_BLOCK).build()
        );
        PaintNetworking.registerServer();
        LOGGER.info("Block Painter loaded");
    }
}
