package me.cortex.voxy.api;

/** Voxy's quad_util.glsl geometry decoding, without raster-only crack expansion. */
public record LodQuad(int face, int model, int biome, int light, int tintMode, boolean cutout,
                      int axisU, int axisV, int axisNormal,
                      float x, float y, float z, float width, float height, float u, float v) {
    public static LodQuad decode(long packed, LodModel model, int level) {
        int face = (int) packed & 7;
        int data = model.face(face);
        float scale = 1 << level;
        float u = (data & 15) / 16f, v = ((data >>> 8) & 15) / 16f;
        float width = (((data >>> 4) & 15) + 1) / 16f - u + ((packed >>> 3) & 15);
        float height = (((data >>> 12) & 15) + 1) / 16f - v + ((packed >>> 7) & 15);
        int depth = (data >>> 16) & 63;
        float d = (depth == 63 ? 64 : depth) / 64f;
        if ((face & 1) != 0) d = 1 - d;
        int axis = face >> 1;
        int au = axis == 2 ? 1 : 0, av = axis == 0 ? 2 : axis == 1 ? 1 : 2;
        int an = axis == 0 ? 1 : axis == 1 ? 2 : 0;
        float[] p = {(packed >>> 21) & 31, (packed >>> 16) & 31, (packed >>> 11) & 31};
        p[au] += u; p[av] += v; p[an] += d;
        boolean cutout = ((data >>> 22) & 1) != 0 || ((((data >>> 23) & 1) != 0) && (((packed >>> 3) & 255) != 0));
        return new LodQuad(face, model.id(), (int) (packed >>> 46) & 511, (int) (packed >>> 55) & 255,
                (data >>> 24) & 3, cutout, au, av, an, p[0] * scale, p[1] * scale, p[2] * scale,
                width * scale, height * scale, u, v);
    }
}
