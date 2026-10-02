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
    public static Selection prepare(LodScene scene) { return new Selection(scene.index().get(scene)); }

    /** Immutable published coverage, including empty leaves, for branch-local replacement. */
    public static final class Coverage {
        private final Long2ObjectOpenHashMap<LodMesh> nodes;
        private final LongOpenHashSet leaves;
        private Coverage(Selection selection, List<LodMesh> published, Coverage retained) {
            nodes = new Long2ObjectOpenHashMap<>(selection.nodes);
            leaves = new LongOpenHashSet(published.size());
            for (LodMesh mesh : published) {
                leaves.add(mesh.key);
                nodes.put(mesh.key, mesh);
                if (retained == null) continue;
                for (int level = mesh.level + 1; level <= WorldEngine.MAX_LOD_LAYER; level++) {
                    int shift = level - mesh.level;
                    long key = WorldEngine.getWorldSectionId(level, Math.floorDiv(mesh.x, 1 << shift),
                            Math.floorDiv(mesh.y, 1 << shift), Math.floorDiv(mesh.z, 1 << shift));
                    if (!nodes.containsKey(key) && retained.nodes.containsKey(key)) nodes.put(key, retained.nodes.get(key));
                }
            }
        }

        /** Prior finer geometry is usable only when it still covers every existing child of this node. */
        public List<LodMesh> fineBranch(LodMesh node) {
            LodMesh old = nodes.get(node.key);
            if (old == null || old.children != node.children || leaves.contains(node.key)) return List.of();
            List<LodMesh> result = new ArrayList<>();
            return collect(old, result) ? result : List.of();
        }

        /** Ancestor paths accompanying a retained branch, including its leaves. */
        public List<LodMesh> branchNodes(LodMesh node) {
            List<LodMesh> result = new ArrayList<>();
            appendNodes(nodes.get(node.key), result);
            return result;
        }

        private void appendNodes(LodMesh node, List<LodMesh> out) {
            if (node == null) return;
            out.add(node);
            if (leaves.contains(node.key) || node.level == 0) return;
            for (int child = 0; child < 8; child++) if ((node.children & (1 << child)) != 0)
                appendNodes(nodes.get(childKey(node, child)), out);
        }

        private boolean collect(LodMesh node, List<LodMesh> out) {
            if (leaves.contains(node.key)) { out.add(node); return true; }
            if (node.level == 0 || node.children == 0) return false;
            for (int child = 0; child < 8; child++) if ((node.children & (1 << child)) != 0) {
                LodMesh mesh = nodes.get(childKey(node, child));
                if (mesh == null || !collect(mesh, out)) return false;
            }
            return true;
        }
    }

    public static final class Selection {
        private final Long2ObjectOpenHashMap<LodMesh> nodes;
        private final LongOpenHashSet targets;
        private final List<LodMesh> roots = new ArrayList<>();
        private final LongOpenHashSet coveredParents;
        private Selection(Selection index) {
            nodes=index.nodes; targets=index.targets; roots.addAll(index.roots);
            coveredParents=new LongOpenHashSet(Math.max(0,nodes.size()-targets.size()));
        }
        Selection(LodScene scene) {
            nodes = new Long2ObjectOpenHashMap<>(scene.meshes().size() + scene.ancestors().size());
            targets = new LongOpenHashSet(scene.meshes().size());
            coveredParents = new LongOpenHashSet(scene.ancestors().size());
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
            return readySelectionUnsorted(ready, previous, null);
        }

        public Coverage coverage(List<LodMesh> published) { return coverage(published, null); }
        public Coverage coverage(List<LodMesh> published, Coverage retained) { return new Coverage(this, published, retained); }

        public List<LodMesh> readySelectionUnsorted(Predicate<LodMesh> ready, Supplier<List<LodMesh>> previous,
                                                   Coverage retained) {
            coveredParents.clear();
            List<LodMesh> result = new ArrayList<>();
            Long2ObjectOpenHashMap<List<LodMesh>> previousByRoot = null;
            for (LodMesh mesh : roots) {
                int start = result.size();
                if (!select(mesh, nodes, targets, ready, result, retained)) {
                    if (previousByRoot == null) {
                        previousByRoot = new Long2ObjectOpenHashMap<>();
                        for (LodMesh old : previous.get()) {
                            int shift = WorldEngine.MAX_LOD_LAYER - old.level;
                            long root = WorldEngine.getWorldSectionId(WorldEngine.MAX_LOD_LAYER,
                                    Math.floorDiv(old.x, 1 << shift), Math.floorDiv(old.y, 1 << shift), Math.floorDiv(old.z, 1 << shift));
                            previousByRoot.computeIfAbsent(root, key -> new ArrayList<>()).add(old);
                        }
                    }
                    List<LodMesh> previousRoot = previousByRoot.getOrDefault(mesh.key, List.of());
                    if (!previousRoot.isEmpty()) {
                        result.subList(start, result.size()).clear();
                        result.addAll(previousRoot);
                    }
                }
            }
            // Only the final partition owns resources. Tentative descendants rolled back to a parent
            // must not be reported as covered or destroyed by a consumer.
            LongOpenHashSet published = new LongOpenHashSet(result.size());
            for (LodMesh mesh : result) published.add(mesh.key);
            for (LodMesh mesh : roots) markCovered(mesh, published, retained);
            return result;
        }

        private boolean markCovered(LodMesh node, LongOpenHashSet published, Coverage retained) {
            if (published.contains(node.key)) return true;
            if (node.level == 0 || node.children == 0) return false;
            boolean complete = true;
            for (int child = 0; child < 8; child++) if ((node.children & (1 << child)) != 0) {
                LodMesh mesh = nodes.get(childKey(node, child));
                if (mesh == null && retained != null) mesh = retained.nodes.get(childKey(node, child));
                if (mesh != null) complete &= markCovered(mesh, published, retained);
            }
            if (complete) coveredParents.add(node.key);
            return complete;
        }

        /** Whether the last selection covered this parent entirely with available descendants. */
        public boolean isCoveredParent(long key) { return coveredParents.contains(key) && !targets.contains(key); }
        /** Reuses published coverage only for an unchanged ancestor, never for a requested target. */
        public boolean coveredBy(Selection previous, long key) {
            if (previous == null || targets.contains(key) || !previous.coveredParents.contains(key)) return false;
            LodMesh current=nodes.get(key), old=previous.nodes.get(key);
            return current != null && old != null && current.revision==old.revision && current.children==old.children;
        }
        /** Read-only view of redundant parent keys, valid until the next selection call. */
        public LongSet coveredParents() { return LongSets.unmodifiable(coveredParents); }
    }

    private static boolean select(LodMesh node, Long2ObjectOpenHashMap<LodMesh> nodes, LongOpenHashSet targets,
                                  Predicate<LodMesh> ready, List<LodMesh> out, Coverage retained) {
        boolean available = ready.test(node);
        if (targets.contains(node.key)) {
            if (available) { out.add(node); return true; }
            if (retained != null) {
                List<LodMesh> old = retained.fineBranch(node);
                if (!old.isEmpty()) { out.addAll(old); return true; }
            }
            return false;
        }
        int start = out.size();
        boolean complete = true;
        for (int child = 0; child < 8; child++) if ((node.children & (1 << child)) != 0) {
            LodMesh mesh = nodes.get(WorldEngine.getWorldSectionId(node.level - 1, node.x * 2 + (child & 1),
                    node.y * 2 + ((child >> 2) & 1), node.z * 2 + ((child >> 1) & 1)));
            // Missing nodes are outside the provider's distance window. Unready requested nodes remain present.
            if (mesh != null) complete &= select(mesh, nodes, targets, ready, out, retained);
        }
        if (complete) return true;
        if (retained != null) {
            List<LodMesh> old = retained.fineBranch(node);
            if (!old.isEmpty()) {
                out.subList(start, out.size()).clear();
                out.addAll(old);
                return true;
            }
        }
        if (available) {
            out.subList(start, out.size()).clear();
            out.add(node);
            return true;
        }
        return false;
    }

    private static long childKey(LodMesh node, int child) {
        return WorldEngine.getWorldSectionId(node.level - 1, node.x * 2 + (child & 1),
                node.y * 2 + ((child >> 2) & 1), node.z * 2 + ((child >> 1) & 1));
    }
}
