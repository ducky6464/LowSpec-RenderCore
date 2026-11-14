package dev.lowspec.rendercore.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.lowspec.rendercore.ChunkGpuBufferPool;
import net.minecraft.client.render.chunk.ChunkRenderData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

@Mixin(ChunkRenderData.class)
public abstract class ChunkRenderDataGpuPoolMixin {
    @Redirect(
            method = "upload(Lnet/minecraft/client/render/BlockRenderLayer;Lnet/minecraft/client/render/BuiltBuffer;J)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
            require = 0
    )
    private GpuBuffer lowspec$reuseVertexBuffer(GpuDevice device, Supplier<String> label, int usage, ByteBuffer data) {
        return ChunkGpuBufferPool.createOrReuse(device, label, usage, data);
    }

    @Redirect(
            method = "uploadIndexBuffer(Lnet/minecraft/client/render/BlockRenderLayer;Lnet/minecraft/client/util/BufferAllocator$CloseableBuffer;J)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/blaze3d/buffers/GpuBuffer;"),
            require = 0
    )
    private GpuBuffer lowspec$reuseIndexBuffer(GpuDevice device, Supplier<String> label, int usage, ByteBuffer data) {
        return ChunkGpuBufferPool.createOrReuse(device, label, usage, data);
    }
}
