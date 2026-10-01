package me.cortex.voxy.api;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.FrustumIntersection;

/** Camera-relative, unjittered projection at the display resolution, independent of RT upscaling. */
public final class LodView {
    private final Matrix4f matrix;
    private final double x, y, z;
    private final double threshold;
    private final FrustumIntersection frustum;

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
        float[][] points = new float[8][2];
        float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = maxX;
        int front = 0;
        for (int i = 0; i < 8; i++) {
            Vector4f p = matrix.transform(new Vector4f((float) (ox - x + ((i & 1) != 0 ? size : 0)),
                    (float) (oy - y + ((i & 2) != 0 ? size : 0)),
                    (float) (oz - z + ((i & 4) != 0 ? size : 0)), 1));
            if (p.w > .001f) front++;
            points[i][0] = p.x / p.w * .5f;
            points[i][1] = p.y / p.w * .5f;
            minX = Math.min(minX, points[i][0]); maxX = Math.max(maxX, points[i][0]);
            minY = Math.min(minY, points[i][1]); maxY = Math.max(maxY, points[i][1]);
        }
        if (front != 0 && front != 8) return true;
        // Secondary rays retain a local off-screen hierarchy; primary rays use Voxy's exact six-face area.
        if (front == 0 || maxX < -.5f || minX > .5f || maxY < -.5f || minY > .5f)
            return dx * dx + dy * dy + dz * dz < size * (double) size * 9;
        return projectedArea(points) > threshold;
    }

    boolean sameView(LodView other) {
        return other != null && x == other.x && y == other.y && z == other.z &&
                threshold == other.threshold && matrix.equals(other.matrix);
    }

    static double projectedArea(float[][] p) {
        return (cornerArea(p, 0, 1, 2, 4) + cornerArea(p, 7, 6, 5, 3)) * .5;
    }

    private static double cornerArea(float[][] p, int origin, int a, int b, int c) {
        double ax = p[a][0] - p[origin][0], ay = p[a][1] - p[origin][1];
        double bx = p[b][0] - p[origin][0], by = p[b][1] - p[origin][1];
        double cx = p[c][0] - p[origin][0], cy = p[c][1] - p[origin][1];
        return Math.abs(ax * by - bx * ay) + Math.abs(ax * cy - cx * ay) + Math.abs(cx * by - bx * cy);
    }
}
