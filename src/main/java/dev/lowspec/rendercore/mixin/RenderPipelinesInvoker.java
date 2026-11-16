package dev.lowspec.rendercore.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Accesses Minecraft's private pipeline registry for an early custom pipeline registration. */
@Mixin(RenderPipelines.class)
public interface RenderPipelinesInvoker {
    @Invoker("register")
    static RenderPipeline lowspec$register(RenderPipeline pipeline) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
