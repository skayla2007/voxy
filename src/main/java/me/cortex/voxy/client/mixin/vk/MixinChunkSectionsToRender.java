package me.cortex.voxy.client.mixin.vk;

import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.RenderMatrices;
import me.cortex.voxy.client.core.vk.MinecraftVkHost;
import me.cortex.voxy.client.core.vk.MinecraftVkHostAdapter;
import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs Voxy after vanilla's opaque terrain pass on Minecraft's Vulkan command buffer. */
@Mixin(ChunkSectionsToRender.class)
public class MixinChunkSectionsToRender {
    @Unique private static boolean voxy$loggedFirstFrame;

    @Inject(method = "renderGroup", at = @At("TAIL"))
    private void voxy$renderVulkanFrame(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
        if (group != ChunkSectionLayerGroup.OPAQUE) return;
        if (!(MinecraftVkHost.get() instanceof MinecraftVkHostAdapter adapter)) return;

        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer == null || renderer.vkCore == null) return;

        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        if (camera == null || !camera.initialized || camera.pos == null) return;

        // Vanilla owns visibility now. Feed the same compiled sections into Voxy's
        // depth-bound pass that the old Sodium collectors supplied.
        var visible = renderer.visbleSectionStream;
        if (visible != null) {
            visible.reset();
            for (var section : minecraft.levelRenderer.visibleSections()) {
                var origin = section.getRenderOrigin();
                visible.put(net.minecraft.core.SectionPos.asLong(
                        origin.getX() >> 4, origin.getY() >> 4, origin.getZ() >> 4));
            }
        }

        try {
            renderer.vkCore.renderFrame(group.outputTarget(), adapter,
                    new RenderMatrices(camera.projectionMatrix, camera.viewRotationMatrix),
                    camera.pos.x, camera.pos.y, camera.pos.z);
            if (!voxy$loggedFirstFrame) {
                voxy$loggedFirstFrame = true;
                Logger.info("Voxy rendered its first standalone Vulkan LOD frame through vanilla terrain");
            }
        } catch (Throwable t) {
            Logger.error("Voxy Vulkan frame failed", t);
        }
    }
}
