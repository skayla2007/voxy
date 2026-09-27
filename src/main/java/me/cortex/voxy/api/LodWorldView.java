package me.cortex.voxy.api;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Read access to one world's LoD store. Obtained from {@link VoxyLodApi#activeWorld()} or a
 * {@link LodDataListener} callback. All methods are thread-safe.
 */
public interface LodWorldView {
    /** False once the world has been detached; acquisitions then return null. */
    boolean isAlive();

    /** Stable identity for the attached world, unique for the lifetime of the voxy instance. */
    long worldId();

    /**
     * Acquire a section if voxy has data for it, loading it from storage when needed (this can block on disk
     * I/O, so call it off the render thread). Returns null when the section was never ingested or the world
     * is detached. The returned handle must be closed.
     */
    @Nullable LodSectionHandle acquireSection(int lvl, int x, int y, int z);

    /** The block state of a voxel. Air for air voxels and unknown ids. */
    BlockState blockState(long voxel);

    /** Biome registry key (for example {@code minecraft:plains}) of a voxel, or null if unknown. */
    @Nullable String biomeKey(long voxel);
}
