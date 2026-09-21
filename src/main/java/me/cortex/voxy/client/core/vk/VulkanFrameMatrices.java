package me.cortex.voxy.client.core.vk;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Captures the exact projection matrix Minecraft submits for world terrain. */
public final class VulkanFrameMatrices {
    private static final Matrix4f TERRAIN_PROJECTION = new Matrix4f();
    private static boolean hasProjection;

    private VulkanFrameMatrices() {}

    public static Matrix4fc captureProjection(Matrix4f projection) {
        TERRAIN_PROJECTION.set(projection);
        hasProjection = true;
        return projection;
    }

    public static Matrix4fc getProjectionOr(Matrix4fc fallback) {
        return hasProjection ? TERRAIN_PROJECTION : fallback;
    }
}
