package me.cortex.voxy.api;

/**
 * A reference to one loaded 32x32x32 voxy section. While the handle is open voxy keeps the section in memory;
 * {@link #close()} releases it. A handle is not thread-confined, but it must be closed exactly once.
 *
 * <p>Reads are not synchronized with voxy's writers: a concurrent ingest may be observed partially. Every such
 * write is followed by a {@link LodDataListener#onSectionChanged} event, so consumers that rebuild on that
 * event converge to the final data.
 */
public interface LodSectionHandle extends AutoCloseable {
    /** Detail level of this section. */
    int level();

    int x();

    int y();

    int z();

    /** True when no voxel of this section (or of any child section for levels above 0) is non-air. */
    boolean isEmpty();

    /** Raw voxel at a local coordinate in {@code [0, 32)}; decode with {@link LodVoxel}. */
    long voxel(int x, int y, int z);

    /** Raw voxel at {@link LodVoxel#index(int, int, int)}. */
    long voxel(int index);

    /** Copy all {@link LodVoxel#SECTION_VOLUME} voxels in {@link LodVoxel#index} order. */
    void copyTo(long[] destination, int offset);

    /** Release the section. Further reads throw. */
    @Override
    void close();
}
