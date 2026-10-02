package me.cortex.voxy.api;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.FrustumIntersection;

/** Camera-relative, unjittered projection at the display resolution, independent of RT upscaling. */
public final class LodView {
    private final Matrix4f matrix;
    private final double x, y, z;
    private final double threshold;
    private final FrustumIntersection frustum;
    private final float[] projectedX = new float[8], projectedY = new float[8];

    public LodView(double x, double y, double z, Matrix4fc matrix, int width, int height, float subdivision) {
        this.x = x; this.y = y; this.z = z;
        this.matrix = new Matrix4f(matrix);
        // Side planes reject off-screen refinement; Minecraft's real-chunk far plane does not bound LODs.
        frustum = new FrustumIntersection(this.matrix);
        threshold = subdivision * (double) subdivision / Math.max(1L, (long) width * height);
    }

    public boolean refine(int ox, int oy, int oz, int size) {
        double dx = Math.max(Math.max(ox - x, x - ox - size), 0);
        double dy = Math.max(Math.max(oy - y, y - oy - size), 0);
        double dz = Math.max(Math.max(oz - z, z - oz - size), 0);
        if (dx * dx + dy * dy + dz * dz < size * (double) size) return true;
        if (frustum.intersectAab((float)(ox-x), (float)(oy-y), (float)(oz-z),
                (float)(ox-x+size), (float)(oy-y+size), (float)(oz-z+size),
                FrustumIntersection.PLANE_MASK_NX | FrustumIntersection.PLANE_MASK_PX |
                FrustumIntersection.PLANE_MASK_NY | FrustumIntersection.PLANE_MASK_PY) >= 0)
            return dx * dx + dy * dy + dz * dz < size * (double) size * 9;
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = maxX;
        int front = 0;
        for (int i = 0; i < 8; i++) {
            float px = (float) (ox - x + ((i & 1) != 0 ? size : 0));
            float py = (float) (oy - y + ((i & 2) != 0 ? size : 0));
            float pz = (float) (oz - z + ((i & 4) != 0 ? size : 0));
            float w = matrix.m03() * px + matrix.m13() * py + matrix.m23() * pz + matrix.m33();
            if (w > .001f) front++;
            projectedX[i] = (matrix.m00() * px + matrix.m10() * py + matrix.m20() * pz + matrix.m30()) / w * .5f;
            projectedY[i] = (matrix.m01() * px + matrix.m11() * py + matrix.m21() * pz + matrix.m31()) / w * .5f;
            minX = Math.min(minX, projectedX[i]); maxX = Math.max(maxX, projectedX[i]);
            minY = Math.min(minY, projectedY[i]); maxY = Math.max(maxY, projectedY[i]);
        }
        if (front != 0 && front != 8) return true;
        // Secondary rays retain a local off-screen hierarchy; primary rays use Voxy's exact six-face area.
        if (front == 0 || maxX < -.5f || minX > .5f || maxY < -.5f || minY > .5f)
            return dx * dx + dy * dy + dz * dz < size * (double) size * 9;
        return projectedArea(projectedX, projectedY) > threshold;
    }

    boolean sameView(LodView other) {
        return other != null && x == other.x && y == other.y && z == other.z &&
                threshold == other.threshold && matrix.equals(other.matrix);
    }

    static double projectedArea(float[][] p) {
        return (cornerArea(p, 0, 1, 2, 4) + cornerArea(p, 7, 6, 5, 3)) * .5;
    }

    static double projectedArea(float[] x, float[] y) {
        return (cornerArea(x, y, 0, 1, 2, 4) + cornerArea(x, y, 7, 6, 5, 3)) * .5;
    }

    private static double cornerArea(float[] x, float[] y, int o, int a, int b, int c) {
        double ax = x[a] - x[o], ay = y[a] - y[o];
        double bx = x[b] - x[o], by = y[b] - y[o];
        double cx = x[c] - x[o], cy = y[c] - y[o];
        return Math.abs(ax * by - bx * ay) + Math.abs(ax * cy - cx * ay) + Math.abs(cx * by - bx * cy);
    }

    private static double cornerArea(float[][] p, int origin, int a, int b, int c) {
        double ax = p[a][0] - p[origin][0], ay = p[a][1] - p[origin][1];
        double bx = p[b][0] - p[origin][0], by = p[b][1] - p[origin][1];
        double cx = p[c][0] - p[origin][0], cy = p[c][1] - p[origin][1];
        return Math.abs(ax * by - bx * ay) + Math.abs(ax * cy - cx * ay) + Math.abs(cx * by - bx * cy);
    }
}
