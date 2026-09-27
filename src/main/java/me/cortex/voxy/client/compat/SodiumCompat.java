package me.cortex.voxy.client.compat;

import me.cortex.voxy.client.mixin.sodium.AccessorSodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;

//Isolates references to sodium classes so callers can stay loadable when sodium is not installed.
// Only call after checking VoxyClient.SODIUM_LOADED.
public final class SodiumCompat {
    private SodiumCompat() {
    }

    //Number of sodium chunk builder threads, or -1 when sodium's world renderer is not active
    public static int builderThreadCount() {
        var swr = SodiumWorldRenderer.instanceNullable();
        if (swr == null) {
            return -1;
        }
        var rsm = ((AccessorSodiumWorldRenderer) swr).getRenderSectionManager();
        if (rsm == null) {
            return -1;
        }
        return rsm.getBuilder().getTotalThreadCount();
    }
}
