package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.lowspec.rendercore.mixin.RenderPipelinesInvoker;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.util.Identifier;

/**
 * Registers the first Aether base-player shader pipeline.
 *
 * The pipeline is deliberately not submitted yet. Vanilla player drawing remains
 * authoritative until the render-pass handoff and compatibility checks are ready.
 */
public final class AetherPlayerPipeline {
    private static final Identifier LOCATION =
            Identifier.of("lowspec_rendercore", "pipeline/aether_player");
    private static RenderPipeline pipeline;

    private AetherPlayerPipeline() { }

    public static void register() {
        if (pipeline != null) return;
        RenderPipeline candidate = RenderPipeline.builder()
                .withLocation(LOCATION)
                .withVertexShader("core/aether_player")
                .withFragmentShader("core/aether_player")
                .withSampler("Sampler0")
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(AetherPlayerVertexLayout.FORMAT, VertexFormat.DrawMode.TRIANGLES)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(true)
                .withCull(false)
                .withColorWrite(true, true)
                .build();
        pipeline = RenderPipelinesInvoker.lowspec$register(candidate);
    }

    public static boolean isRegistered() { return pipeline != null; }
    public static RenderPipeline pipeline() { return pipeline; }
    public static Identifier location() { return LOCATION; }
}
