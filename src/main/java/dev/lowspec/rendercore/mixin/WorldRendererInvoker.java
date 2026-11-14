package dev.lowspec.rendercore.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WorldRenderer.class)
public interface WorldRendererInvoker {
    @Invoker("scheduleChunkRender")
    void lowspec$invokeScheduleChunkRender(int x, int y, int z, boolean important);
}
