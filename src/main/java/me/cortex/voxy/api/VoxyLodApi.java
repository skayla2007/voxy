package me.cortex.voxy.api;

import me.cortex.voxy.api.impl.LodApiImpl;
import org.jetbrains.annotations.Nullable;

/**
 * Public entry point to voxy's level-of-detail voxel data, for mods that consume LoD data themselves (for
 * example alternative renderers).
 *
 * <p>Typical use: register a {@link LodDataListener} during client init, keep the {@link LodWorldView} from
 * {@link LodDataListener#onWorldAttached}, rebuild on {@link LodDataListener#onSectionChanged}, and pull
 * sections with {@link LodWorldView#acquireSection}.
 *
 * <p>A mod that renders the world without OpenGL should call {@link #requestHeadless(String)} from its client
 * initializer. Voxy then keeps ingesting, storing and serving LoD data but never creates its own OpenGL
 * renderer. Headless mode is also entered automatically when no OpenGL context exists at renderer
 * initialization (for example on the Vulkan backend).
 */
public final class VoxyLodApi {
    /** Incremented on incompatible changes to this package. */
    public static final int API_VERSION = 1;

    private VoxyLodApi() {
    }

    /** {@link #API_VERSION} of the installed voxy. Consumers compiled against another revision should check this. */
    public static int apiVersion() {
        return API_VERSION;
    }

    /** True when voxy is initialized and able to provide LoD data in this session. */
    public static boolean isAvailable() {
        return LodApiImpl.isAvailable();
    }

    /** Ask voxy to run without its own renderer. Must be called before the game's renderer initializes. */
    public static void requestHeadless(String requesterModId) {
        LodApiImpl.requestHeadless(requesterModId);
    }

    /** True when voxy runs without its own renderer. */
    public static boolean isHeadless() {
        return LodApiImpl.isHeadless();
    }

    /**
     * Register a listener. If a world is already attached, {@link LodDataListener#onWorldAttached} is invoked
     * for it immediately on the calling thread.
     */
    public static void addListener(LodDataListener listener) {
        LodApiImpl.addListener(listener);
    }

    public static void removeListener(LodDataListener listener) {
        LodApiImpl.removeListener(listener);
    }

    /** The world currently attached to the client, or null. */
    public static @Nullable LodWorldView activeWorld() {
        return LodApiImpl.activeWorld();
    }
}
