package me.cortex.voxy.api;
import java.util.concurrent.*;

/** Serial CPU work with one replaceable pending view, joined before its world resources are released. */
final class LodSelectionWorker implements AutoCloseable {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "Voxy external LOD selection"); thread.setDaemon(true); return thread;
    });
    private Runnable latest;
    private boolean running, closed;
    private volatile Throwable failure;

    synchronized void submit(Runnable task) {
        checkFailure();
        if (closed) return;
        latest = task;
        if (!running) { running = true; executor.execute(this::drain); }
    }
    private void drain() {
        for (;;) {
            Runnable task;
            synchronized (this) {
                task = latest; latest = null;
                if (task == null || closed) { running = false; return; }
            }
            try { task.run(); }
            catch (Throwable t) { failure = t; synchronized (this) { latest=null; running=false; } return; }
        }
    }
    void checkFailure() { if (failure != null) throw new IllegalStateException("Voxy LOD selection failed", failure); }
    @Override public void close() {
        synchronized (this) { closed=true; latest=null; }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) throw new IllegalStateException("LOD selection worker did not stop");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
}
