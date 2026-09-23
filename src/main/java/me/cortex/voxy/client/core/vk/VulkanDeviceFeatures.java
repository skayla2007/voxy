package me.cortex.voxy.client.core.vk;

/** Features that Voxy successfully added while Minecraft created its VkDevice. */
public final class VulkanDeviceFeatures {
    private static volatile boolean drawIndirectCount;
    private static volatile boolean representativeFragmentTest;

    private VulkanDeviceFeatures() {}

    public static void setDrawIndirectCount(boolean enabled) {
        drawIndirectCount = enabled;
    }

    public static boolean hasDrawIndirectCount() {
        return drawIndirectCount;
    }

    public static void setRepresentativeFragmentTest(boolean enabled) {
        representativeFragmentTest = enabled;
    }

    public static boolean hasRepresentativeFragmentTest() {
        return representativeFragmentTest;
    }

    public static void reset() {
        drawIndirectCount = false;
        representativeFragmentTest = false;
    }
}
