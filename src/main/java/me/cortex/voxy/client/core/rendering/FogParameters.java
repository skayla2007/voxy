package me.cortex.voxy.client.core.rendering;

/** Backend-neutral snapshot of Minecraft's per-frame terrain fog. */
public record FogParameters(float red, float green, float blue, float alpha,
                            float environmentalStart, float environmentalEnd,
                            float renderDistanceStart, float renderDistanceEnd) {
}
