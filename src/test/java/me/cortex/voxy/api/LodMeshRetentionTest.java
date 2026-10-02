package me.cortex.voxy.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LodMeshRetentionTest {
    private static LodMesh mesh(long revision) { return new LodMesh(42,revision,0,0,0,0,0,0,new long[]{1},new int[8]); }
    @Test void consumerOwnedMeshKeepsItsExactIdentityAndInvalidationForcesReplacement() {
        var cache = new LodMeshRetention(); var first = mesh(1);
        cache.remember(first); cache.clean();
        assertSame(first,cache.get(42));
        cache.invalidate(42); assertNull(cache.get(42));
        var changed = mesh(2); cache.remember(changed);
        assertSame(changed,cache.get(42));
        cache.clear(); assertNull(cache.get(42));
    }
    @Test void materialValidationBelongsToTheProviderEpoch() {
        var mesh=mesh(1);
        assertFalse(mesh.validated(7)); mesh.validatedFor(7);
        assertTrue(mesh.validated(7)); assertFalse(mesh.validated(8));
    }
}
