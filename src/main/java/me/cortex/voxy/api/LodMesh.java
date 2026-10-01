package me.cortex.voxy.api;

import java.nio.LongBuffer;

/** Owned immutable copy of Voxy's merged quads. Coordinates are section-local voxels. */
public final class LodMesh {
    public final long key;
    public final long revision;
    public final int level, x, y, z, aabb, children;
    private final long[] quads;
    private final int[] offsets;

    public LodMesh(long key, long revision, int level, int x, int y, int z,
                   int aabb, int children, long[] quads, int[] offsets) {
        this.key = key;
        this.revision = revision;
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        this.aabb = aabb;
        this.children = children;
        this.quads = quads.clone();
        this.offsets = offsets.clone();
    }

    public LongBuffer quads() { return LongBuffer.wrap(quads).asReadOnlyBuffer(); }
    public int quadCount() { return quads.length; }
    public int[] offsets() { return offsets.clone(); }
    public int blockSize() { return 32 << level; }
    public int originX() { return x * blockSize(); }
    public int originY() { return y * blockSize(); }
    public int originZ() { return z * blockSize(); }
}
