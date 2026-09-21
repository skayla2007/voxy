package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.core.vk.VulkanFrameMatrices;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Captures view bobbing, hurt tilt, nausea and other per-frame projection effects. */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            index = 0)
    private Matrix4f voxy$captureTerrainProjection(Matrix4f projection) {
        VulkanFrameMatrices.captureProjection(projection);
        return projection;
    }
}
