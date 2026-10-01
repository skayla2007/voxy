package me.cortex.voxy.api;

import me.cortex.voxy.common.world.WorldEngine;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LodSelectionTest {
    private static LodMesh mesh(int level, int x, int children) {
        return new LodMesh(WorldEngine.getWorldSectionId(level, x, 0, 0), 1, level, x, 0, 0,
                0, children, new long[]{1}, new int[8]);
    }
    private static LodScene scene(List<LodMesh> leaves, List<LodMesh> parents) {
        return new LodScene(1, 1, 1, 16, leaves, parents, Map.of(), new int[0]);
    }

    @Test void partialChildrenKeepParentAndOtherRootsPublishImmediately() {
        LodMesh parent = mesh(4, 0, 3), a = mesh(3, 0, 0), b = mesh(3, 1, 0), other = mesh(4, 2, 0);
        LodScene scene = scene(List.of(a, b, other), List.of(parent));
        Set<Long> ready = new HashSet<>(List.of(parent.key, a.key, other.key));
        assertEquals(Set.of(parent, other), new HashSet<>(LodHierarchy.readySelection(scene, m -> ready.contains(m.key))));
        ready.add(b.key);
        assertEquals(Set.of(a, b, other), new HashSet<>(LodHierarchy.readySelection(scene, m -> ready.contains(m.key))));
    }

    @Test void movementRetainsPreviousFineCoverageUntilCoarseReplacementIsReady() {
        LodMesh parent = mesh(4, -1, 3), a = mesh(3, -2, 0), b = mesh(3, -1, 0);
        LodScene scene = scene(List.of(parent), List.of());
        assertEquals(Set.of(a, b), new HashSet<>(LodHierarchy.readySelection(scene, m -> false, List.of(a, b))));
        assertEquals(List.of(parent), LodHierarchy.readySelection(scene, m -> true, List.of(a, b)));
    }

    @Test void childrenOutsideDistanceWindowDoNotBlockInWindowRefinement() {
        LodMesh parent = mesh(4, 0, 3), inside = mesh(3, 0, 0);
        assertEquals(List.of(inside), LodHierarchy.readySelection(scene(List.of(inside), List.of(parent)), m -> true));
    }

    @Test void emptyReadyChildStillSuppliesCoverage() {
        LodMesh parent = mesh(4, 0, 3), a = mesh(3, 0, 0), b = mesh(3, 1, 0);
        LodMesh empty = new LodMesh(b.key, 1, b.level, b.x, b.y, b.z, 0, 0, new long[0], new int[8]);
        assertEquals(Set.of(a, empty), new HashSet<>(LodHierarchy.readySelection(scene(List.of(a, empty), List.of(parent)), m -> true)));
    }

    @Test void parentResourcesAreRedundantOnlyAfterChildrenSupplyCoverage() {
        LodMesh parent = mesh(4, 0, 3), a = mesh(3, 0, 0), b = mesh(3, 1, 0);
        LodHierarchy.Selection selection = LodHierarchy.prepare(scene(List.of(a,b), List.of(parent)));
        selection.readySelection(m -> m != b, List.of());
        assertFalse(selection.isCoveredParent(parent.key));
        assertEquals(Set.of(a,b), new HashSet<>(selection.readySelection(m -> m != parent, List.of())));
        assertTrue(selection.isCoveredParent(parent.key));
        selection.readySelection(m -> m == parent, List.of());
        assertFalse(selection.isCoveredParent(parent.key));
    }

    @Test void readyRootsAvoidCopyingPreviousCoverageAndTraversalOrderStaysStable() {
        LodMesh parent = mesh(4, 0, 3), a = mesh(3, 0, 0), b = mesh(3, 1, 0), other = mesh(4, 2, 0);
        var selection = LodHierarchy.prepare(scene(List.of(b, other, a), List.of(parent)));
        var first = selection.readySelectionUnsorted(m -> true, () -> { fail("Ready roots need no previous snapshot"); return List.of(); });
        assertEquals(Set.of(a, b, other), new HashSet<>(first));
        assertEquals(first, selection.readySelectionUnsorted(m -> true, () -> List.of(parent)));
        assertThrows(UnsupportedOperationException.class, () -> selection.coveredParents().clear());
    }

    private static LodView view(int width, int height, float subdivision, double x, double z, float fov) {
        return new LodView(x, 0, z, new Matrix4f().perspective((float) Math.toRadians(fov),
                width / (float) height, .1f, 48000), width, height, subdivision);
    }

    @Test void precisionDisplayResolutionFovAndMovementAffectProjectedRefinement() {
        assertTrue(view(1920, 1080, 28, 0, 0, 70).refine(-32, -32, -1800, 64));
        assertFalse(view(1920, 1080, 256, 0, 0, 70).refine(-32, -32, -1800, 64));
        assertFalse(view(960, 540, 28, 0, 0, 70).refine(-32, -32, -1800, 64));
        assertFalse(view(1920, 1080, 28, 0, 0, 110).refine(-32, -32, -1800, 64));
        assertTrue(view(960, 540, 28, 0, -1400, 70).refine(-32, -32, -1800, 64));
    }

    @Test void projectedAreaMatchesSixFaceCrossProducts() {
        float[][] points = new float[8][2];
        for (int i = 0; i < 8; i++) {
            points[i][0] = ((i & 1) != 0 ? 2 : 0) + ((i & 4) != 0 ? 1 : 0);
            points[i][1] = ((i & 2) != 0 ? 3 : 0) + ((i & 4) != 0 ? 1 : 0);
        }
        assertEquals(11, LodView.projectedArea(points), 1e-6);
    }

    @Test void cameraInsideRefinesButDistantOffscreenNearPlaneCrossingDoesNot() {
        assertTrue(view(1920, 1080, 256, 0, 0, 70).refine(-32, -32, -32, 64));
        assertFalse(view(1920, 1080, 256, 0, 0, 70).refine(4096, -32, -32, 64));
    }
}
