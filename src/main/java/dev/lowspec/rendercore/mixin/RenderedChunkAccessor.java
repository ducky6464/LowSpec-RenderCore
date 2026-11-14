package net.minecraft.client.render.chunk;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RenderedChunk.class)
public interface RenderedChunkAccessor {
    @Accessor("blockPalette")
    PalettedContainer<BlockState> lowspec$getBlockPalette();

    @Accessor("blockEntities")
    Map<BlockPos, BlockEntity> lowspec$getBlockEntities();

    @Accessor("debugWorld")
    boolean lowspec$isDebugWorld();
}
