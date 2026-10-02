package me.cortex.voxy.api;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LodSelectionWorkerTest {
    @Test void blockedSelectionKeepsOnlyTheNewestPendingViewAndCloseJoinsIt() throws Exception {
        var started=new CountDownLatch(1); var release=new CountDownLatch(1); var done=new CountDownLatch(1);
        var result=new AtomicInteger();
        var worker=new LodSelectionWorker();
        worker.submit(() -> { started.countDown(); try { release.await(); } catch(InterruptedException e) { throw new RuntimeException(e); } });
        assertTrue(started.await(5,TimeUnit.SECONDS));
        worker.submit(() -> result.set(1));
        worker.submit(() -> { result.set(2); done.countDown(); });
        release.countDown(); assertTrue(done.await(5,TimeUnit.SECONDS)); worker.close();
        assertEquals(2,result.get()); worker.submit(() -> result.set(3)); assertEquals(2,result.get());
    }
    @Test void backgroundFailureIsSurfacedToTheConsumer() throws Exception {
        var done=new CountDownLatch(1); var worker=new LodSelectionWorker();
        worker.submit(() -> { try { throw new IllegalArgumentException("test"); } finally { done.countDown(); } });
        assertTrue(done.await(5,TimeUnit.SECONDS)); worker.close();
        assertThrows(IllegalStateException.class,worker::checkFailure);
    }
}
