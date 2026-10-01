package me.cortex.voxy.api;

import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;

/** Optional external renderer API. All entry points run on Minecraft's render thread. */
public final class VoxyLodApi {
    /** Runtime field prevents consumers from inlining a different installed provider's API version. */
    public static final int VERSION;
    static { VERSION = 4; }
    private static final Object DEVICE_HOST_LOCK = new Object();
    private static java.util.function.BooleanSupplier externalRenderer = () -> false;
    private VoxyLodApi() {}

    /** Shared host synchronization for device-idle and queue submission across renderer-owned queues. */
    public static Object deviceHostLock() { return DEVICE_HOST_LOCK; }

    /** Register before world creation. An active external renderer suppresses Voxy raster allocations and draws. */
    public static void registerExternalRenderer(java.util.function.BooleanSupplier active) {
        externalRenderer = java.util.Objects.requireNonNull(active);
    }

    public static boolean externalRendererActive() { return externalRenderer.getAsBoolean(); }

    /** Returns null when the world or Vulkan LOD provider is unavailable. */
    public static LodScene update(double x, double y, double z, org.joml.Matrix4fc projectionView, int width, int height) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        return renderer == null || renderer.vkCore == null ? null : renderer.vkCore.externalScene(x, y, z, projectionView, width, height);
    }
}
