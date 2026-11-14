package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkUploadBudget;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;

/**
 * Limits how many completed chunk upload tasks are submitted in one render frame.
 * Returning null at the queue poll leaves the remaining tasks queued for the next frame.
 */
@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderUploadPacingMixin {
    @Shadow @Final private Queue<Runnable> uploadQueue;
    @Unique private int lowspec$uploadsThisFrame;
    @Unique private int lowspec$uploadLimit;

    @Inject(method = "upload()V", at = @At("HEAD"))
    private void lowspec$beginUploadFrame(CallbackInfo ci) {
        lowspec$uploadsThisFrame = 0;
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.chunkUploadPacingEnabled) {
            lowspec$uploadLimit = Integer.MAX_VALUE;
        } else {
            lowspec$uploadLimit = ChunkUploadBudget.effectiveLimit(
                    LowSpecRenderCoreClient.CONFIG.chunkUploadsPerFrame);
        }
    }

    @Redirect(
            method = "upload()V",
            at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;")
    )
    private Object lowspec$paceChunkUploads(Queue<?> queue) {
        if (queue != uploadQueue) return queue.poll();
        if (lowspec$uploadsThisFrame >= lowspec$uploadLimit) return null;
        Object next = queue.poll();
        if (next != null) lowspec$uploadsThisFrame++;
        return next;
    }
}
