package me.cortex.voxy.api;

/**
 * Decoding helpers for voxy's 64-bit voxel encoding and section geometry.
 *
 * <p>Layout: bits 27..46 block-state id, bits 47..55 biome id, bits 56..63 light
 * (low nibble sky light, high nibble block light). Block id 0 is air. Block and biome ids are per-world and
 * resolved with {@link LodWorldView#blockState(long)} and {@link LodWorldView#biomeKey(long)}.
 *
 * <p>A section at level {@code lvl} covers {@code 32 << lvl} blocks per axis; each voxel covers
 * {@code 1 << lvl} blocks. Section coordinates are {@code blockCoord >> (5 + lvl)}.
 */
public final class LodVoxel {
    public static final int SECTION_SIZE = 32;
    public static final int SECTION_VOLUME = SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;
    public static final int MAX_LEVEL = 4;

    private LodVoxel() {
    }

    public static int index(int x, int y, int z) {
        return ((y & 31) << 10) | ((z & 31) << 5) | (x & 31);
    }

    public static boolean isAir(long voxel) {
        return blockId(voxel) == 0;
    }

    public static int blockId(long voxel) {
        return (int) ((voxel >>> 27) & ((1 << 20) - 1));
    }

    public static int biomeId(long voxel) {
        return (int) ((voxel >>> 47) & 0x1FF);
    }

    public static int skyLight(long voxel) {
        return (int) ((voxel >>> 56) & 0xF);
    }

    public static int blockLight(long voxel) {
        return (int) ((voxel >>> 60) & 0xF);
    }

    /** Blocks per voxel edge at a level. */
    public static int voxelSize(int lvl) {
        return 1 << lvl;
    }

    /** Blocks per section edge at a level. */
    public static int sectionBlocks(int lvl) {
        return SECTION_SIZE << lvl;
    }
}
