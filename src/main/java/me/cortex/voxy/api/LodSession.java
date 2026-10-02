package me.cortex.voxy.api;

import me.cortex.voxy.client.core.model.ModelBakerySubsystem;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import me.cortex.voxy.client.core.vk.render.VkModelStore;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** CPU LOD selection for external renderers; mesh generation and shape remain Voxy-owned. */
public final class LodSession implements AutoCloseable {
    private static final AtomicLong EPOCHS = new AtomicLong();
    private static final long CACHE_BYTES = 192L << 20;
    private static final int REQUESTS_PER_FRAME = 1024;
    private static final int MAX_PENDING = 4096;
    private final long epoch = EPOCHS.incrementAndGet();
    private final RenderGenerationService generator;
    private final VkModelStore models;
    private final Long2ObjectLinkedOpenHashMap<LodMesh> cache = new Long2ObjectLinkedOpenHashMap<>(256);
    private final LongOpenHashSet pending = new LongOpenHashSet();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<Long> invalidations = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<BuiltSection> completed = new ConcurrentLinkedQueue<>();
    private long revision, meshRevision, cacheBytes;
    private int requests;
    private long profileAt = System.nanoTime(), profileFrames, profileNs;
    private volatile boolean closed;
    private volatile LodScene previous;
    private final LodSelectionWorker selector = new LodSelectionWorker();
    private record Palette(long revision, long atlasView, LodModel[] models, int[] colours) {
        LodModel model(int id) { return models[id]; }
        int colour(int id) { return colours[id]; }
    }
    private Palette capturedPalette, workingPalette;
    private LodScene renderScene, renderSource;
    private volatile int debugPending, debugCache;
    private LodView previousView;
    private LodHierarchy.Coverage previousCoverage;
    private long nextSelectionTime;
    private static final long SELECTION_INTERVAL = 100_000_000L;
    private int previousMinY, previousMaxY;
    private double previousRadius;
    private final Long2ObjectLinkedOpenHashMap<LodMesh> visited = new Long2ObjectLinkedOpenHashMap<>();
    private final LodMeshRetention retainedMeshes = new LodMeshRetention();
    private long paletteRevision = -1;
    private Map<Integer, LodModel> palette = Map.of();
    private int[] colours = new int[65536];

    public LodSession(WorldEngine world, ModelBakerySubsystem bakery, ServiceManager services, VkModelStore models) {
        this.models = models;
        generator = new RenderGenerationService(world, bakery, services, false);
        generator.setResultConsumer(section -> {
            if (closed) section.free();
            else completed.add(section);
        });
    }

    public void invalidate(long key) {
        if (closed) return;
        invalidations.add(key);
        int level = WorldEngine.getLevel(key), x = WorldEngine.getX(key), y = WorldEngine.getY(key), z = WorldEngine.getZ(key);
        invalidations.add(WorldEngine.getWorldSectionId(level, x - 1, y, z));
        invalidations.add(WorldEngine.getWorldSectionId(level, x + 1, y, z));
        invalidations.add(WorldEngine.getWorldSectionId(level, x, y - 1, z));
        invalidations.add(WorldEngine.getWorldSectionId(level, x, y + 1, z));
        invalidations.add(WorldEngine.getWorldSectionId(level, x, y, z - 1));
        invalidations.add(WorldEngine.getWorldSectionId(level, x, y, z + 1));
    }

    public LodScene tick(double cameraX, double cameraY, double cameraZ, int minY, int maxY, double radius, LodView view) {
        if (closed) throw new IllegalStateException("LOD session is closed");
        // GPU model export is captured on its owning render thread. The selector never reads mutable
        // model arrays or native atlas resources and receives only the most recent camera request.
        if (capturedPalette == null || capturedPalette.revision() != models.exportRevision
                || capturedPalette.atlasView() != models.exportedAtlasView()) {
            LodModel[] exported = new LodModel[65536]; int[] tints = new int[65536];
            for (int i=0;i<65536;i++) { exported[i]=models.exportedModel(i); tints[i]=models.exportedColour(i); }
            capturedPalette = new Palette(models.exportRevision, models.exportedAtlasView(), exported, tints);
        }
        Palette snapshot = capturedPalette;
        selector.submit(() -> selectTick(cameraX,cameraY,cameraZ,minY,maxY,radius,view,snapshot));
        LodScene source = previous;
        if (source == null) {
            if (renderScene == null || renderScene.atlasView() != snapshot.atlasView())
                renderScene = new LodScene(epoch,0,snapshot.atlasView(),ModelFactory.MODEL_TEXTURE_SIZE,List.of(),Map.of(),snapshot.colours());
        } else if (source != renderSource || renderScene.atlasView() != snapshot.atlasView()) {
            renderSource=source;
            // Atlas growth retires the old native view independently of CPU selection latency.
            renderScene=source.atlasView()==snapshot.atlasView() ? source : source.withAtlas(snapshot.atlasView());
        }
        return renderScene;
    }

    private LodScene selectTick(double cameraX, double cameraY, double cameraZ, int minY, int maxY, double radius, LodView view, Palette snapshot) {
        long start = System.nanoTime();
        if (closed) return previous;
        workingPalette = snapshot;
        Long invalidated;
        retainedMeshes.clean();
        while ((invalidated = invalidations.poll()) != null) {
            retainedMeshes.invalidate(invalidated.longValue());
            dirty.add(invalidated.longValue());
        }
        // Coalesce camera motion and completed meshes into one consumer-visible selection per interval.
        if (previous != null && paletteRevision == snapshot.revision() && previousMinY == minY
                && previousMaxY == maxY && previousRadius == radius && start < nextSelectionTime) return previous;
        if (previous != null && pending.isEmpty() && completed.isEmpty() && dirty.isEmpty() &&
                paletteRevision == snapshot.revision() && previousMinY == minY && previousMaxY == maxY &&
                previousRadius == radius && view.sameView(previousView)) return previous;
        previousView = view; previousMinY = minY; previousMaxY = maxY; previousRadius = radius;
        nextSelectionTime = start + SELECTION_INTERVAL;
        requests = 0;
        dirty.removeIf((java.util.function.LongPredicate) key -> !cache.containsKey(key) && !pending.contains(key));
        visited.clear();
        for (int n = 0; n < REQUESTS_PER_FRAME; n++) {
            BuiltSection built = completed.poll();
            if (built == null) break;
            try {
                long key = built.position;
                pending.remove(key);
                long[] quads = built.isEmpty() ? new long[0] : new long[Math.toIntExact(built.geometryBuffer.size / 8)];
                if (quads.length > 0) MemoryUtil.memLongBuffer(built.geometryBuffer.address, quads.length).get(quads);
                LodMesh mesh = new LodMesh(key, ++meshRevision, WorldEngine.getLevel(key), WorldEngine.getX(key),
                        WorldEngine.getY(key), WorldEngine.getZ(key), built.aabb, built.childExistence & 255,
                        quads, built.offsets == null ? new int[8] : built.offsets);
                LodMesh old = cache.put(key, mesh);
                retainedMeshes.remember(mesh);
                cacheBytes += quads.length * 8L - (old == null ? 0 : old.quadCount() * 8L);
            } finally { built.free(); }
        }
        List<LodMesh> selected = new ArrayList<>();
        int rootSize = 32 << WorldEngine.MAX_LOD_LAYER;
        int cx = (int) Math.floor(cameraX / rootSize), cz = (int) Math.floor(cameraZ / rootSize);
        int r = (int) Math.ceil(radius / rootSize);
        // Nearest roots are requested first, keeping the bounded queue useful after teleports.
        for (int ring = 0; ring <= r; ring++) {
            for (int z = cz - ring; z <= cz + ring; z++) for (int x = cx - ring; x <= cx + ring; x++) {
                if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != ring) continue;
                for (int y = Math.floorDiv(minY, rootSize); y <= Math.floorDiv(maxY, rootSize); y++)
                    select(WorldEngine.MAX_LOD_LAYER, x, y, z, cameraX, cameraY, cameraZ, radius, view, selected);
            }
        }
        selected.sort(Comparator.comparingLong(m -> m.key));
        LongOpenHashSet retained = new LongOpenHashSet();
        retained.addAll(visited.keySet());
        var iterator = cache.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext() && (cacheBytes > CACHE_BYTES || cache.size() > 32768)) {
            var entry = iterator.next();
            if (!retained.contains(entry.getLongKey())) {
                cacheBytes -= entry.getValue().quadCount() * 8L;
                iterator.remove();
            }
        }
        Set<LodMesh> leaves = new HashSet<>(selected);
        List<LodMesh> ancestors = visited.values().stream().filter(m -> !leaves.contains(m)).toList();
        debugPending=pending.size(); debugCache=cache.size();
        if (Boolean.getBoolean("caustica.voxy.profile")) {
            long now = System.nanoTime(); profileFrames++; profileNs += now - start;
            if (now - profileAt > 2_000_000_000L) {
                org.slf4j.LoggerFactory.getLogger("VoxyLOD").info("Voxy provider profile cpuMs={} visited={} selected={} jobs={} completed={} cache={} packedMiB={} requests={}",
                    profileNs / (profileFrames * 1e6), visited.size(), selected.size(), pending.size(), completed.size(), cache.size(), cacheBytes / 1048576, requests);
                profileAt = now; profileFrames = profileNs = 0;
            }
        }
        if (previous != null && previous.meshes().equals(selected) && previous.ancestors().equals(ancestors) && paletteRevision == snapshot.revision())
            return previous;
        if (paletteRevision != snapshot.revision()) {
            Map<Integer, LodModel> updated = new HashMap<>();
            for (int i = 0; i < 65536; i++) {
                LodModel model = snapshot.model(i);
                if (model != null) updated.put(i, model);
                colours[i] = snapshot.colour(i);
            }
            palette = Map.copyOf(updated);
            paletteRevision = snapshot.revision();
        }
        LodScene next = new LodScene(epoch, ++revision,
                snapshot.atlasView(), ModelFactory.MODEL_TEXTURE_SIZE, selected, ancestors, palette, colours);
        LodHierarchy.prepare(next);
        previous = next;
        previousCoverage = null;
        return previous;
    }

    private boolean select(int level, int x, int y, int z, double cx, double cy, double cz, double radius, LodView view, List<LodMesh> out) {
        int size = 32 << level;
        double dx = Math.max(Math.max(x * (double) size - cx, cx - (x + 1.0) * size), 0);
        double dz = Math.max(Math.max(z * (double) size - cz, cz - (z + 1.0) * size), 0);
        if (dx * dx + dz * dz > radius * radius) return true;
        long key = WorldEngine.getWorldSectionId(level, x, y, z);
        LodMesh mesh = cache.getAndMoveToLast(key);
        if (mesh == null && !dirty.contains(key)) {
            mesh = retainedMeshes.get(key);
            if (mesh != null) { cache.putAndMoveToLast(key, mesh); cacheBytes += mesh.quadCount() * 8L; }
        }
        if ((mesh == null || dirty.contains(key)) && !pending.contains(key) && requests < REQUESTS_PER_FRAME && pending.size() < MAX_PENDING) {
            dirty.remove(key);
            pending.add(key);
            generator.enqueueTask(key);
            requests++;
        }
        if (mesh == null) return false;
        // A missing material stalls only its own branch, never the complete world snapshot.
        if (!mesh.validated(epoch)) {
            var quads = mesh.quads();
            while (quads.hasRemaining()) if (workingPalette.model((int) (quads.get() >>> 26) & 65535) == null) return false;
            mesh.validatedFor(epoch);
        }
        visited.put(key, mesh);
        if (level > 0 && view.refine(x * size, y * size, z * size, size) && mesh.children != 0) {
            int start = out.size();
            boolean complete = true;
            for (int child = 0; child < 8; child++) {
                if ((mesh.children & (1 << child)) != 0)
                    complete &= select(level - 1, x * 2 + (child & 1), y * 2 + ((child >> 2) & 1),
                            z * 2 + ((child >> 1) & 1), cx, cy, cz, radius, view, out);
            }
            if (complete) return true;
            out.subList(start, out.size()).clear();
            if (previous != null) {
                if (previousCoverage == null) previousCoverage = LodHierarchy.prepare(previous).coverage(previous.meshes());
                List<LodMesh> retained = previousCoverage.fineBranch(mesh);
                if (!retained.isEmpty()) {
                    // Retained meshes must also remain in the exported graph and CPU cache.
                    for (LodMesh old : previousCoverage.branchNodes(mesh)) {
                        if (old.key != mesh.key) visited.put(old.key, old);
                        if (!cache.containsKey(old.key)) {
                            cache.putAndMoveToLast(old.key, old);
                            cacheBytes += old.quadCount() * 8L;
                        }
                    }
                    out.addAll(retained);
                    return true;
                }
            }
        }
        out.add(mesh);
        return true;
    }

    @Override public void close() {
        closed = true;
        selector.close();
        generator.shutdown();
        BuiltSection section;
        while ((section = completed.poll()) != null) section.free();
        cache.clear();
        dirty.clear();
        invalidations.clear();
        pending.clear();
        previous = null;
        retainedMeshes.clear();
    }

    public void addDebugInfo(List<String> lines) {
        int[] levels = new int[WorldEngine.MAX_LOD_LAYER + 1];
        if (previous != null) for (LodMesh mesh : previous.meshes()) levels[mesh.level]++;
        lines.add("External LOD targets L0..L4: " + Arrays.toString(levels));
        lines.add("External LOD mesh jobs: " + debugPending + ", cache: " + debugCache);
    }
}
