package me.cortex.voxy.client.core.rendering;

import org.joml.Matrix4fc;

/** Projection/model-view pair captured from vanilla Minecraft's render state. */
public record RenderMatrices(Matrix4fc projection, Matrix4fc modelView) {
}
