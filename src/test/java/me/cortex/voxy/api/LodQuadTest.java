package me.cortex.voxy.api;

import org.junit.jupiter.api.Test;
import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LodQuadTest {
    private static LodModel cube(int faceData) {
        return new LodModel(17, faceData, faceData, faceData, faceData, faceData, faceData, 0, -1, null);
    }

    @Test void decodesAllFacesAndWorldScale() {
        for (int face = 0; face < 6; face++) for (int level = 0; level <= 4; level++) {
            long packed = face | (2L << 3) | (4L << 7) | (7L << 21) | (8L << 16) | (9L << 11) | (17L << 26) | (511L << 46) | (255L << 55);
            LodQuad q = LodQuad.decode(packed, cube(0xf0f0), level);
            assertEquals(3 * (1 << level), q.width());
            assertEquals(5 * (1 << level), q.height());
            assertEquals(511, q.biome());
            assertEquals(255, q.light());
            assertEquals(17, q.model());
            int normal = new int[]{1,1,2,2,0,0}[face];
            assertEquals(normal, q.axisNormal());
            assertNotEquals(normal, q.axisU());
            assertNotEquals(normal, q.axisV());
            assertNotEquals(q.axisU(), q.axisV());
            float[] pos = {q.x(), q.y(), q.z()};
            float[] expected = {7,8,9};
            expected[normal] += face & 1;
            for (int i = 0; i < 3; i++) assertEquals(expected[i] * (1 << level), pos[i]);
        }
    }

    @Test void preservesInsetAndPartialFaceBounds() {
        int data = 2 | (11 << 4) | (4 << 8) | (13 << 12) | (16 << 16);
        LodQuad q = LodQuad.decode(1, cube(data), 2);
        assertEquals(.5f, q.x());
        assertEquals(3f, q.y());
        assertEquals(1f, q.z());
        assertEquals(2.5f, q.width());
        assertEquals(2.5f, q.height());
    }

    @Test void indentationEndpointAndCutoutOverride() {
        LodModel model = cube(0xf0f0 | (63 << 16) | (1 << 23));
        assertEquals(1, LodQuad.decode(0, model, 0).y());
        assertEquals(0, LodQuad.decode(1, model, 0).y());
        assertFalse(LodQuad.decode(0, model, 0).cutout());
        assertTrue(LodQuad.decode(1L << 3, model, 0).cutout());
        assertTrue(LodQuad.decode(0, cube(0xf0f0 | (1 << 22)), 0).cutout());
    }

    @Test void snapshotsOwnTheirMemoryAndNegativeOrigins() {
        long[] quads = {42}; int[] offsets = {1,2};
        LodMesh mesh = new LodMesh(0, 1, 4, -2, -1, 3, 0, 255, quads, offsets);
        quads[0] = 99; offsets[0] = 99;
        assertEquals(42, mesh.quads().get());
        assertEquals(1, mesh.offsets()[0]);
        assertThrows(ReadOnlyBufferException.class, () -> mesh.quads().put(0, 7));
        assertEquals(-1024, mesh.originX());
        assertEquals(-512, mesh.originY());
        assertEquals(1536, mesh.originZ());
    }

    @Test void missingBiomePaletteEntryUsesUntintedSentinel() {
        LodScene scene = new LodScene(1, 1, 0, 16, List.of(), Map.of(), new int[]{0xffabcdef});
        assertEquals(0xffabcdef, scene.colour(0));
        assertEquals(-1, scene.colour(-1));
        assertEquals(-1, scene.colour(1));
    }
}
