package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.entity.player.SkinTextures;

/**
 * Submits one prepared skin batch to an already-open Minecraft render pass.
 * The caller owns pass creation, framebuffer selection, dynamic transforms, and draw ordering.
 * This method does not suppress vanilla rendering; it is a controlled submission primitive.
 */
public final class AetherPlayerDrawSubmitter {
    private AetherPlayerDrawSubmitter() { }

    public static int submitSkinBatch(RenderPass pass, GpuBuffer vertexBuffer,
                                      AetherPlayerRenderQueue queue,
                                      AetherPlayerGeometryDrawPlan drawPlan,
                                      int skinGroup, GpuBufferSlice dynamicTransforms) {
        AetherRenderThread.assertCurrent();
        if (pass == null || vertexBuffer == null || queue == null || drawPlan == null
                || dynamicTransforms == null || skinGroup < 0 || skinGroup >= queue.skinGroupCount()) {
            return 0;
        }
        RenderPipeline pipeline = AetherPlayerPipeline.pipeline();
        if (pipeline == null) return 0;
        SkinTextures skin = queue.skinGroupTexture(skinGroup);

        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", dynamicTransforms);
        if (!AetherPlayerTextureBinder.bind(pass, skin)) return 0;
        pass.setVertexBuffer(0, vertexBuffer);

        int submitted = 0;
        for (int batch = 0; batch < drawPlan.batchCount(); batch++) {
            if (drawPlan.batchSkinGroup(batch) != skinGroup) continue;
            for (int offset = 0; offset < drawPlan.batchSize(batch); offset++) {
                int firstVertex = drawPlan.firstVertex(batch, offset);
                int vertexCount = drawPlan.vertexCount(batch, offset);
                if (firstVertex < 0 || vertexCount <= 0) continue;
                pass.draw(firstVertex, vertexCount);
                submitted++;
            }
        }
        return submitted;
    }
}
