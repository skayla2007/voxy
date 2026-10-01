package me.cortex.voxy.api;

import me.cortex.voxy.common.world.WorldEngine;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

/** Publishes independently ready branches, replacing a parent only when its requested children cover it. */
public final class LodHierarchy {
    private LodHierarchy() {}

    public static List<LodMesh> readySelection(LodScene scene, Predicate<LodMesh> ready) {
        return readySelection(scene, ready, List.of());
    }

    public static List<LodMesh> readySelection(LodScene scene, Predicate<LodMesh> ready, List<LodMesh> previous) {
        return prepare(scene).readySelection(ready, previous);
    }

    /** Immutable hierarchy index that can be reused while only consumer readiness changes. */
    public static Selection prepare(LodScene scene) { return new Selection(scene); }

    public static final class Selection {
        private final Long2ObjectOpenHashMap<LodMesh> nodes = new Long2ObjectOpenHashMap<>();
        private final LongOpenHashSet targets = new LongOpenHashSet();
        private final List<LodMesh> roots = new ArrayList<>();
        private final LongOpenHashSet coveredParents = new LongOpenHashSet();
        private Selection(LodScene scene) {
            for (LodMesh mesh : scene.ancestors()) nodes.put(mesh.key, mesh);
            for (LodMesh mesh : scene.meshes()) { nodes.put(mesh.key, mesh); targets.add(mesh.key); }
            for (LodMesh mesh : nodes.values()) if (mesh.level == WorldEngine.MAX_LOD_LAYER) roots.add(mesh);
            roots.sort(Comparator.comparingLong(m -> m.key));
        }

        public List<LodMesh> readySelection(Predicate<LodMesh> ready, List<LodMesh> previous) {
            List<LodMesh> result = readySelectionUnsorted(ready, () -> previous);
            result.sort(Comparator.comparingLong(m -> m.key));
            return result;
        }

        /** Stable tree order; the previous selection is fetched only when a root needs fallback coverage. */
        public List<LodMesh> readySelectionUnsorted(Predicate<LodMesh> ready, Supplier<List<LodMesh>> previous) {
            coveredParents.clear();
            List<LodMesh> result = new ArrayList<>();
            Long2ObjectOpenHashMap<List<LodMesh>> previousByRoot = null;
            for (LodMesh mesh : roots) {
                int start = result.size();
                if (!select(mesh, nodes, targets, ready, result, coveredParents)) {
                    if (previousByRoot == null) {
                        previousByRoot = new Long2ObjectOpenHashMap<>();
                        for (LodMesh old : previous.get()) {
                            int shift = WorldEngine.MAX_LOD_LAYER - old.level;
                            long root = WorldEngine.getWorldSectionId(WorldEngine.MAX_LOD_LAYER,
                                    Math.floorDiv(old.x, 1 << shift), Math.floorDiv(old.y, 1 << shift), Math.floorDiv(old.z, 1 << shift));
                            previousByRoot.computeIfAbsent(root, key -> new ArrayList<>()).add(old);
                        }
                    }
                    List<LodMesh> retained = previousByRoot.getOrDefault(mesh.key, List.of());
                    if (!retained.isEmpty()) {
                        result.subList(start, result.size()).clear();
                        result.addAll(retained);
                    }
                }
            }
            return result;
        }

        /** Whether the last selection covered this parent entirely with available descendants. */
        public boolean isCoveredParent(long key) { return coveredParents.contains(key); }
        /** Read-only view of redundant parent keys, valid until the next selection call. */
        public LongSet coveredParents() { return LongSets.unmodifiable(coveredParents); }
    }

    private static boolean select(LodMesh node, Long2ObjectOpenHashMap<LodMesh> nodes, LongOpenHashSet targets,
                                  Predicate<LodMesh> ready, List<LodMesh> out, LongOpenHashSet coveredParents) {
        boolean available = ready.test(node);
        if (targets.contains(node.key)) {
            if (available) out.add(node);
            return available;
        }
        int start = out.size();
        boolean complete = true;
        for (int child = 0; child < 8; child++) if ((node.children & (1 << child)) != 0) {
            LodMesh mesh = nodes.get(WorldEngine.getWorldSectionId(node.level - 1, node.x * 2 + (child & 1),
                    node.y * 2 + ((child >> 2) & 1), node.z * 2 + ((child >> 1) & 1)));
            // Missing nodes are outside the provider's distance window. Unready requested nodes remain present.
            if (mesh != null) complete &= select(mesh, nodes, targets, ready, out, coveredParents);
        }
        if (complete) { coveredParents.add(node.key); return true; }
        if (available) {
            out.subList(start, out.size()).clear();
            out.add(node);
            return true;
        }
        return false;
    }
}
