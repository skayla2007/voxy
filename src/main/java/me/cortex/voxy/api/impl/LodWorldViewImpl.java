package me.cortex.voxy.api.impl;

import me.cortex.voxy.api.LodDataListener;
import me.cortex.voxy.api.LodSectionHandle;
import me.cortex.voxy.api.LodVoxel;
import me.cortex.voxy.api.LodWorldView;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * {@link LodWorldView} over a {@link WorldEngine}. Holds one engine reference from construction until
 * {@link #releaseWorldRef()} so the world is not reclaimed as idle while a consumer uses it.
 *
 * <p>Section acquisition and {@link #kill()} are ordered by a read/write lock: after {@code kill()} returns no
 * new handle can be created, so the engine only has to wait for handles that were already open.
 */
final class LodWorldViewImpl implements LodWorldView {
    final WorldEngine engine;
    private final long worldId;
    private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private boolean alive = true;
    private boolean refHeld;
    final WorldEngine.ISectionChangeCallback changeCallback;

    LodWorldViewImpl(WorldEngine engine, long worldId) {
        this.engine = engine;
        this.worldId = worldId;
        engine.acquireRef();
        this.refHeld = true;
        this.changeCallback = (section, flags, neighbors) -> {
            if (this.isAlive()) {
                int apiFlags = 0;
                if ((flags & WorldEngine.UPDATE_TYPE_BLOCK_BIT) != 0) apiFlags |= LodDataListener.CHANGE_BLOCKS;
                if ((flags & WorldEngine.UPDATE_TYPE_CHILD_EXISTENCE_BIT) != 0) apiFlags |= LodDataListener.CHANGE_CHILD_EXISTENCE;
                LodApiImpl.dispatchChange(this, section.lvl, section.x, section.y, section.z, apiFlags);
            }
        };
    }

    @Override
    public boolean isAlive() {
        this.lifecycle.readLock().lock();
        try {
            return this.alive && this.engine.isLive();
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    @Override
    public long worldId() {
        return this.worldId;
    }

    @Override
    public LodSectionHandle acquireSection(int lvl, int x, int y, int z) {
        if (lvl < 0 || lvl > LodVoxel.MAX_LEVEL) {
            throw new IllegalArgumentException("LoD level out of range: " + lvl);
        }
        this.lifecycle.readLock().lock();
        try {
            if (!this.alive || !this.engine.isLive()) {
                return null;
            }
            WorldSection section = this.engine.acquireIfExists(lvl, x, y, z);
            return section == null ? null : new Handle(section);
        } finally {
            this.lifecycle.readLock().unlock();
        }
    }

    @Override
    public BlockState blockState(long voxel) {
        int id = LodVoxel.blockId(voxel);
        if (id == 0) {
            return Blocks.AIR.defaultBlockState();
        }
        try {
            BlockState state = this.engine.getMapper().getBlockStateFromBlockId(id);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        } catch (IndexOutOfBoundsException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    public String biomeKey(long voxel) {
        return this.engine.getMapper().getBiomeKeyFromBiomeId(LodVoxel.biomeId(voxel));
    }

    void kill() {
        this.lifecycle.writeLock().lock();
        try {
            this.alive = false;
        } finally {
            this.lifecycle.writeLock().unlock();
        }
    }

    void releaseWorldRef() {
        if (this.refHeld) {
            this.refHeld = false;
            if (this.engine.isLive()) {
                this.engine.releaseRef();
            }
        }
    }

    private static final class Handle implements LodSectionHandle {
        private final WorldSection section;
        private final long[] data;
        private final AtomicBoolean open = new AtomicBoolean(true);

        Handle(WorldSection section) {
            this.section = section;
            this.data = section._unsafeGetRawDataArray();
        }

        @Override
        public int level() {
            return this.section.lvl;
        }

        @Override
        public int x() {
            return this.section.x;
        }

        @Override
        public int y() {
            return this.section.y;
        }

        @Override
        public int z() {
            return this.section.z;
        }

        @Override
        public boolean isEmpty() {
            this.checkOpen();
            return this.section.getNonEmptyChildren() == 0;
        }

        @Override
        public long voxel(int x, int y, int z) {
            this.checkOpen();
            return this.data[LodVoxel.index(x, y, z)];
        }

        @Override
        public long voxel(int index) {
            this.checkOpen();
            return this.data[index];
        }

        @Override
        public void copyTo(long[] destination, int offset) {
            this.checkOpen();
            System.arraycopy(this.data, 0, destination, offset, LodVoxel.SECTION_VOLUME);
        }

        @Override
        public void close() {
            if (this.open.compareAndSet(true, false)) {
                this.section.release();
            }
        }

        private void checkOpen() {
            if (!this.open.get()) {
                throw new IllegalStateException("LoD section handle already closed");
            }
        }
    }
}
