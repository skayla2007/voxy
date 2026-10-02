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

    @Test void rolledBackFineBranchDoesNotDestroyItsHiddenParents() {
        LodMesh root=mesh(4,0,3), a=mesh(3,0,3), b=mesh(3,1,0), aa=mesh(2,0,0), ab=mesh(2,1,0);
        var selection=LodHierarchy.prepare(scene(List.of(aa,ab,b),List.of(root,a)));
        assertEquals(List.of(root),selection.readySelection(m -> m != b,List.of()));
        assertFalse(selection.isCoveredParent(a.key));
        assertFalse(selection.isCoveredParent(root.key));
    }
    @Test void sceneNodeIndexDoesNotShareMutableReadinessBetweenConsumers() {
        LodMesh parent=mesh(4,0,3), a=mesh(3,0,0), b=mesh(3,1,0);
        LodScene scene=scene(List.of(a,b),List.of(parent));
        var first=LodHierarchy.prepare(scene); var second=LodHierarchy.prepare(scene);
        first.readySelection(m -> true,List.of());
        second.readySelection(m -> m==parent,List.of());
        assertTrue(first.isCoveredParent(parent.key)); assertFalse(second.isCoveredParent(parent.key));
    }
    @Test void olderCoverageSkipsOnlyUnchangedAncestorsAndCannotStarveCoarsening() {
        LodMesh parent=mesh(4,0,3), a=mesh(3,0,0), b=mesh(3,1,0);
        var old=LodHierarchy.prepare(scene(List.of(a,b),List.of(parent)));
        old.readySelection(m -> true,List.of());
        var same=LodHierarchy.prepare(scene(List.of(a,b),List.of(parent)));
        assertTrue(same.coveredBy(old,parent.key));
        var coarse=LodHierarchy.prepare(scene(List.of(parent),List.of()));
        assertFalse(coarse.coveredBy(old,parent.key));
        LodMesh changed=new LodMesh(parent.key,2,parent.level,parent.x,parent.y,parent.z,0,3,new long[0],new int[8]);
        var edited=LodHierarchy.prepare(scene(List.of(a,b),List.of(changed)));
        assertFalse(edited.coveredBy(old,parent.key));
    }

    @Test void refinementRetainsCompleteFineCoverageWithEmptyLeavesUntilReplacementReady() {
        LodMesh root=mesh(4,0,3), a=mesh(3,0,0), b=mesh(3,1,0);
        LodMesh empty=new LodMesh(b.key,1,b.level,b.x,b.y,b.z,0,0,new long[0],new int[8]);
        var old=LodHierarchy.prepare(scene(List.of(a,empty),List.of(root))).coverage(List.of(a,empty));
        LodMesh changedA=mesh(3,0,1), aa=mesh(2,0,0);
        var next=LodHierarchy.prepare(scene(List.of(aa,empty),List.of(root,changedA)));
        assertEquals(Set.of(a,empty),new HashSet<>(next.readySelectionUnsorted(m -> m==root || m==empty,() -> List.of(a,empty),old)));
        assertEquals(Set.of(aa,empty),new HashSet<>(next.readySelectionUnsorted(m -> m==aa || m==empty,() -> List.of(a,empty),old)));
        assertEquals(Set.of(a,empty),new HashSet<>(old.fineBranch(root)));
        assertTrue(old.fineBranch(mesh(4,0,7)).isEmpty());
        assertEquals(Set.of(root,a,empty),new HashSet<>(old.branchNodes(root)));
        var coarser=LodHierarchy.prepare(scene(List.of(root),List.of()));
        coarser.readySelectionUnsorted(m -> false,() -> List.of(a,empty),old);
        assertFalse(coarser.isCoveredParent(root.key),"A requested coarse target must still be built");
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
        float[] x=new float[8],y=new float[8];
        for(int i=0;i<8;i++){x[i]=points[i][0];y[i]=points[i][1];}
        assertEquals(LodView.projectedArea(points),LodView.projectedArea(x,y),0);
    }

    @Test void cameraInsideRefinesButDistantOffscreenNearPlaneCrossingDoesNot() {
        assertTrue(view(1920, 1080, 256, 0, 0, 70).refine(-32, -32, -32, 64));
        assertFalse(view(1920, 1080, 256, 0, 0, 70).refine(4096, -32, -32, 64));
    }
}
