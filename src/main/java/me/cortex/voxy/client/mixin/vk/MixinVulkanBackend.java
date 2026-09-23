package me.cortex.voxy.client.mixin.vk;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import me.cortex.voxy.client.core.vk.VulkanDeviceFeatures;
import me.cortex.voxy.common.Logger;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.Collection;
import java.util.Set;

import static org.lwjgl.vulkan.NVRepresentativeFragmentTest.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

/** Enables the Vulkan 1.2 indirect-count feature on Minecraft's device for Voxy. */
@Mixin(VulkanBackend.class)
public class MixinVulkanBackend {
    private static final VulkanFeature VOXY_DRAW_INDIRECT_COUNT = new VulkanFeature(
            VulkanBackend.VK12_FEATURES_STRUCT,
            "drawIndirectCount",
            VkPhysicalDeviceVulkan12Features.DRAWINDIRECTCOUNT);
    private static final VulkanPNextStruct VOXY_REPRESENTATIVE_FRAGMENT_TEST_STRUCT = new VulkanPNextStruct(
            VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_REPRESENTATIVE_FRAGMENT_TEST_FEATURES_NV,
            VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.SIZEOF);
    private static final VulkanFeature VOXY_REPRESENTATIVE_FRAGMENT_TEST = new VulkanFeature(
            VOXY_REPRESENTATIVE_FRAGMENT_TEST_STRUCT,
            "representativeFragmentTest",
            VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.REPRESENTATIVEFRAGMENTTEST);

    @ModifyArgs(
            method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"))
    private void voxy$enableDrawIndirectCount(Args args) {
        @SuppressWarnings("unchecked")
        Collection<String> enabledExtensions = args.get(0);
        VulkanPhysicalDevice physicalDevice = args.get(1);
        @SuppressWarnings("unchecked")
        Set<VulkanFeature> enabledFeatures = args.get(2);

        boolean indirectCountSupported;
        boolean representativeFragmentTestSupported;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var vulkan12 = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default();
            var features2 = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(vulkan12);
            vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), features2);
            indirectCountSupported = vulkan12.drawIndirectCount();

            var representative = VkPhysicalDeviceRepresentativeFragmentTestFeaturesNV.calloc(stack).sType$Default();
            features2.pNext(representative);
            vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), features2);
            representativeFragmentTestSupported = physicalDevice.hasDeviceExtension(
                    VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME)
                    && representative.representativeFragmentTest();
        }

        if (indirectCountSupported) {
            enabledFeatures.add(VOXY_DRAW_INDIRECT_COUNT);
        }
        if (representativeFragmentTestSupported) {
            enabledExtensions.add(VK_NV_REPRESENTATIVE_FRAGMENT_TEST_EXTENSION_NAME);
            enabledFeatures.add(VOXY_REPRESENTATIVE_FRAGMENT_TEST);
        }
        VulkanDeviceFeatures.setDrawIndirectCount(indirectCountSupported);
        VulkanDeviceFeatures.setRepresentativeFragmentTest(representativeFragmentTestSupported);
        Logger.info("Voxy: Vulkan drawIndirectCount "
                + (indirectCountSupported ? "enabled" : "unsupported; using fallback"));
        Logger.info("Voxy: Vulkan representative fragment test "
                + (representativeFragmentTestSupported ? "enabled" : "unsupported; cull raster fallback active"));
    }
}
