package me.cortex.voxy.client.mixin.vk;

import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.RenderMatrices;
import me.cortex.voxy.client.core.vk.MinecraftVkHost;
import me.cortex.voxy.client.core.vk.MinecraftVkHostAdapter;
import me.cortex.voxy.client.core.vk.VulkanFrameMatrices;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs Voxy immediately before vanilla's opaque terrain pass. */
@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsToRender {
    @Unique private static boolean voxy$loggedFirstFrame;

    @Inject(method = "renderGroup", at = @At("HEAD"))
    private void voxy$renderVulkanFrame(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (group != ChunkSectionLayerGroup.OPAQUE) return;
        if (!(MinecraftVkHost.get() instanceof MinecraftVkHostAdapter adapter)) return;

        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null || renderer.vkCore == null) return;

        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return;

        // Render the LOD underneath vanilla terrain. In 26.2 newly compiled chunks
        // fade in while already writing depth; rendering Voxy afterwards therefore
        // punched a temporary chunk-shaped hole. Do not pre-cut visible chunk AABBs:
        // the real opaque pass below naturally replaces the LOD as it fades in.
        var visible = renderer.visbleSectionStream;
        if (visible != null) {
            visible.reset();
        }

        try {
            var terrainProjection = VulkanFrameMatrices.getProjectionOr(camera.projectionMatrix);
            renderer.vkCore.renderFrame(group.outputTarget(), adapter,
                    new RenderMatrices(terrainProjection, camera.viewRotationMatrix),
                    camera.pos.x, camera.pos.y, camera.pos.z);
            if (!voxy$loggedFirstFrame) {
                voxy$loggedFirstFrame = true;
                Logger.info("Voxy rendered its first standalone Vulkan LOD frame through vanilla terrain");
            }
        } catch (Throwable t) {
            Logger.error("Voxy Vulkan frame failed", t);
        }
    }

    @Inject(method = "renderGroup", at = @At("TAIL"))
    private void voxy$finishVulkanFrame(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (group != ChunkSectionLayerGroup.OPAQUE) return;
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null || renderer.vkCore == null) return;
        try {
            renderer.vkCore.finishOpaqueTerrain();
        } catch (Throwable t) {
            Logger.error("Voxy Vulkan depth resolve failed", t);
        }
    }
}
