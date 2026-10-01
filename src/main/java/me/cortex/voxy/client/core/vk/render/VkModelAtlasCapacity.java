package me.cortex.voxy.client.core.vk.render;

/** Model IDs retain 256 columns; only occupied row capacity grows. */
final class VkModelAtlasCapacity {
    static int rows(int current, int modelId) {
        if (current < 8 || current > 256 || (current & (current - 1)) != 0)
            throw new IllegalArgumentException("Atlas rows must be a power of two from 8 to 256");
        if (modelId < 0 || modelId >= 65536) throw new IllegalArgumentException("Model ID outside atlas");
        int required = (modelId >>> 8) + 1;
        while (current < required) current *= 2;
        return Math.min(current, 256);
    }
}
