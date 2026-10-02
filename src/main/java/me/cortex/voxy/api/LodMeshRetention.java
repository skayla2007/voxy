package me.cortex.voxy.api;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** Reuses immutable meshes still owned by a consumer without extending their lifetime. */
final class LodMeshRetention {
    private static final class Ref extends WeakReference<LodMesh> {
        final long key;
        Ref(LodMesh mesh, ReferenceQueue<LodMesh> queue) { super(mesh, queue); key = mesh.key; }
    }
    private final Long2ObjectOpenHashMap<Ref> refs = new Long2ObjectOpenHashMap<>();
    private final ReferenceQueue<LodMesh> collected = new ReferenceQueue<>();

    void clean() {
        Ref ref;
        while ((ref = (Ref) collected.poll()) != null) if (refs.get(ref.key) == ref) refs.remove(ref.key);
    }
    LodMesh get(long key) { Ref ref = refs.get(key); return ref == null ? null : ref.get(); }
    void remember(LodMesh mesh) { refs.put(mesh.key, new Ref(mesh, collected)); }
    void invalidate(long key) { refs.remove(key); }
    void clear() { refs.clear(); while (collected.poll() != null) {} }
}
