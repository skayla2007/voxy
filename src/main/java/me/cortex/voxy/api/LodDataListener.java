package me.cortex.voxy.api;

/**
 * Receives LoD data events from voxy. Register with {@link VoxyLodApi#addListener(LodDataListener)}.
 *
 * <p>Threading: {@link #onWorldAttached} and {@link #onWorldDetached} run on the client (render) thread.
 * {@link #onSectionChanged} runs on voxy's worker threads, possibly concurrently, while voxy still holds the
 * changed section. Implementations must be thread-safe and should only record the coordinates and return
 * quickly; read the data later through {@link LodWorldView#acquireSection}.
 */
public interface LodDataListener {
    /** A world's LoD data became available (the client joined or switched to it). */
    default void onWorldAttached(LodWorldView world) {
    }

    /**
     * The world's LoD data is no longer available. Every {@link LodSectionHandle} obtained from it must be
     * closed promptly; voxy cannot shut the world down while handles are open.
     */
    default void onWorldDetached(LodWorldView world) {
    }

    /**
     * A section's voxel data changed (ingest, import, block update or mip propagation from a child level).
     *
     * @param lvl   detail level, 0 (1 block per voxel) to {@link LodVoxel#MAX_LEVEL}
     * @param x     section x in level-{@code lvl} section units ({@code blockX >> (5 + lvl)})
     * @param y     section y in level-{@code lvl} section units
     * @param z     section z in level-{@code lvl} section units
     * @param flags bit set of {@link #CHANGE_BLOCKS} and {@link #CHANGE_CHILD_EXISTENCE}
     */
    default void onSectionChanged(LodWorldView world, int lvl, int x, int y, int z, int flags) {
    }

    /** Voxel contents of the section changed. */
    int CHANGE_BLOCKS = 1;
    /** Which of the section's children contain non-air voxels changed. */
    int CHANGE_CHILD_EXISTENCE = 2;
}
