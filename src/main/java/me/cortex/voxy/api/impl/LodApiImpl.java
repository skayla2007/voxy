package me.cortex.voxy.api.impl;

import me.cortex.voxy.api.LodDataListener;
import me.cortex.voxy.api.LodWorldView;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Backing state of {@link me.cortex.voxy.api.VoxyLodApi}. Attach/detach calls come from the client thread
 * (level renderer lifecycle and instance shutdown); listener dispatch for section changes comes from voxy's
 * worker threads through {@link WorldEngine}'s change listeners.
 */
public final class LodApiImpl {
    private static final CopyOnWriteArrayList<LodDataListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong WORLD_IDS = new AtomicLong();
    private static volatile boolean headlessRequested;
    private static volatile boolean headless;
    private static volatile LodWorldViewImpl active;

    private LodApiImpl() {
    }

    public static boolean isAvailable() {
        return VoxyCommon.isAvailable();
    }

    public static void requestHeadless(String requester) {
        if (!headlessRequested) {
            Logger.info("Headless LoD mode requested by " + requester);
        }
        headlessRequested = true;
    }

    public static boolean isHeadlessRequested() {
        return headlessRequested;
    }

    /** Called once from client render init with the resolved mode. */
    public static void setHeadless(boolean value) {
        headless = value;
    }

    public static boolean isHeadless() {
        return headless;
    }

    public static void addListener(LodDataListener listener) {
        LISTENERS.addIfAbsent(listener);
        LodWorldViewImpl world = active;
        if (world != null && world.isAlive()) {
            safeAttached(listener, world);
        }
    }

    public static void removeListener(LodDataListener listener) {
        LISTENERS.remove(listener);
    }

    public static LodWorldView activeWorld() {
        LodWorldViewImpl world = active;
        return world != null && world.isAlive() ? world : null;
    }

    /** Attach the client's current world. The view holds a world reference until {@link #detach()}. */
    public static void attach(WorldEngine engine) {
        LodWorldViewImpl current = active;
        if (current != null) {
            if (current.engine == engine) {
                return;
            }
            detach();
        }
        LodWorldViewImpl view = new LodWorldViewImpl(engine, WORLD_IDS.incrementAndGet());
        engine.addChangeListener(view.changeCallback);
        active = view;
        for (LodDataListener listener : LISTENERS) {
            safeAttached(listener, view);
        }
    }

    public static void detach() {
        LodWorldViewImpl view = active;
        if (view == null) {
            return;
        }
        active = null;
        view.engine.removeChangeListener(view.changeCallback);
        view.kill();
        for (LodDataListener listener : LISTENERS) {
            try {
                listener.onWorldDetached(view);
            } catch (Throwable t) {
                Logger.error("LoD listener failed in onWorldDetached", t);
            }
        }
        view.releaseWorldRef();
    }

    /** Instance shutdown waits for world references, so the API view must let go first. */
    public static void onInstanceShutdown(VoxyInstance instance) {
        LodWorldViewImpl view = active;
        if (view != null && view.engine.instanceIn == instance) {
            detach();
        }
    }

    static void dispatchChange(LodWorldViewImpl view, int lvl, int x, int y, int z, int flags) {
        for (LodDataListener listener : LISTENERS) {
            try {
                listener.onSectionChanged(view, lvl, x, y, z, flags);
            } catch (Throwable t) {
                Logger.error("LoD listener failed in onSectionChanged", t);
            }
        }
    }

    private static void safeAttached(LodDataListener listener, LodWorldViewImpl view) {
        try {
            listener.onWorldAttached(view);
        } catch (Throwable t) {
            Logger.error("LoD listener failed in onWorldAttached", t);
        }
    }
}
