package com.yahoo.vespa.curator;

import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.path.Path;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.api.VespaCurator;
import com.yahoo.vespa.curator.api.VespaCurator.Meta;
import com.yahoo.vespa.curator.api.VespaCurator.SingletonWorker;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.mock.MockCuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class CuratorWrapperTest {

    static final Path lockPath = Path.fromString("/vespa/singleton/v1/singleton/lock");

    @Test
    public void testUserApi() throws Exception {
        try (Curator wrapped = new MockCurator()) {
            CuratorWrapper curator = new CuratorWrapper(wrapped, new MockMetric());

            Path path = Path.fromString("path");
            assertEquals(Optional.empty(), curator.stat(path));

            Meta meta = curator.write(path, "data".getBytes(UTF_8));
            assertEquals(Optional.of(meta), curator.stat(path));

            assertEquals("data", new String(curator.read(path).get().data(), UTF_8));
            assertEquals(meta, curator.read(path).get().meta());

            assertEquals(Optional.empty(), curator.write(path, new byte[0], 0));

            meta = curator.write(path, new byte[0], meta.version()).get();
            assertEquals(3, meta.version());

            assertEquals(List.of("path"), curator.list(Path.createRoot()));

            assertFalse(curator.delete(path, 0));

            curator.delete(path, 3);

            assertEquals(List.of(), curator.list(Path.createRoot()));

            try (AutoCloseable lock = curator.lock(path, Duration.ofSeconds(1))) {
                assertEquals(List.of("path"), wrapped.getChildren(CuratorWrapper.userRoot));
            }
        }
    }

    @Test
    public void testSingleSingleton() {
        try (Curator wrapped = new MockCurator()) {
            Phaser stunning = new Phaser(1);
            ManualClock clock = new ManualClock() {
                @Override public Instant instant() {
                    stunning.arriveAndAwaitAdvance();
                    // Let test thread advance time when desired.
                    stunning.arriveAndAwaitAdvance();
                    return super.instant();
                };
            };
            MockMetric metric = new MockMetric();
            CuratorWrapper curator = new CuratorWrapper(wrapped, clock, Duration.ofMillis(100), metric);

            // First singleton to register becomes active during construction.
            Singleton singleton = new Singleton(curator);
            assertTrue(singleton.isActive);
            assertTrue(wrapped.exists(lockPath));
            stunning.register();
            assertTrue(curator.isActive(singleton.id()));
            stunning.arriveAndDeregister();
            singleton.shutdown();
            assertFalse(singleton.isActive);
            // ... and deactivated as a result of unregistering again.

            // Singleton can be set up again, but this time, time runs away.
            Phaser mark2 = new Phaser(2); // Janitor and helper.
            new Thread(() -> {
                mark2.arriveAndAwaitAdvance(); // Wait for janitor to call activate.
                stunning.arriveAndAwaitAdvance(); // Let janitor measure time spent on activation, while test thread waits for it.
                stunning.arriveAndAwaitAdvance(); // Let janitor measure time spent on activation, while test thread waits for it.
            }).start();
            singleton = new Singleton(curator) {
                @Override public void activate() {
                    // Set up sync in clock on next renewLease.
                    super.activate();
                    stunning.register();
                    mark2.arrive();
                }
            };
            assertTrue(singleton.isActive);

            stunning.arriveAndAwaitAdvance(); // Wait for next renewLease.
            stunning.arriveAndAwaitAdvance(); // Let next renewLease complete.
            stunning.arriveAndAwaitAdvance(); // Wait for next updateStatus.
            clock.advance(wrapped.sessionTimeout());
            singleton.phaser.register();      // Set up so we can synchronise with deactivation.
            stunning.forceTermination();      // Let lease expire, and ensure further ticks complete if we lose the race to unregister.

            singleton.phaser.arriveAndAwaitAdvance();
            assertFalse(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 2.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 2.0,
                                 "deactivation.millis", 0.0),
                          metric);

            // Singleton is reactivated next tick.
            singleton.phaser.awaitAdvance(singleton.phaser.arriveAndDeregister());
            assertTrue(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 3.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 2.0,
                                 "deactivation.millis", 0.0),
                          metric);

            // Manager unregisters remaining singletons on shutdown.
            curator.deconstruct();
            assertFalse(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 3.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 3.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 0.0),
                          metric);
        }
    }

    @Test
    public void testSingletonsInSameContainer() {
        try (Curator wrapped = new MockCurator()) {
            MockMetric metric = new MockMetric();
            CuratorWrapper curator = new CuratorWrapper(wrapped, new ManualClock(), Duration.ofMillis(100), metric);

            // First singleton to register becomes active during construction.
            Singleton singleton = new Singleton(curator);
            assertTrue(singleton.isActive);
            assertTrue(wrapped.exists(lockPath));
            assertTrue(curator.isActive(singleton.id()));

            Singleton newSingleton = new Singleton(curator);
            assertTrue(newSingleton.isActive);
            assertFalse(singleton.isActive);

            Singleton newerSingleton = new Singleton(curator);
            assertTrue(newerSingleton.isActive);
            assertFalse(newSingleton.isActive);
            assertFalse(singleton.isActive);

            singleton.shutdown();
            assertTrue(newerSingleton.isActive);
            assertFalse(newSingleton.isActive);
            assertFalse(singleton.isActive);

            newerSingleton.shutdown();
            assertFalse(newerSingleton.isActive);
            assertTrue(newSingleton.isActive);
            assertFalse(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 4.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 3.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 1.0),
                          metric);

            // Add a singleton which fails activation.
            Phaser stunning = new Phaser(2);
            AtomicReference<String> thrownMessage = new AtomicReference<>();
            new Thread(() -> {
                RuntimeException e = assertThrows(RuntimeException.class,
                                                  () -> new Singleton(curator) {
                                                      @Override public void activate() {
                                                          throw new RuntimeException("expected test exception");
                                                      }
                                                      @Override public void deactivate() {
                                                          stunning.arriveAndAwaitAdvance();
                                                          stunning.arriveAndAwaitAdvance();
                                                          throw new RuntimeException("expected test exception");
                                                      }
                                                      @Override public String toString() {
                                                          return "failing singleton";
                                                      }
                                                  });
                thrownMessage.set(e.getMessage());
                stunning.arriveAndAwaitAdvance();
            }).start();

            stunning.arriveAndAwaitAdvance(); // Failing component is about to be deactivated.
            assertFalse(newSingleton.isActive);
            assertTrue(curator.isActive(newSingleton.id())); // No actual active components, but container has the lease.
            verifyMetrics(Map.of("activation.count", 5.0,
                                 "activation.millis", 0.0,
                                 "activation.failure.count", 1.0,
                                 "deactivation.count", 5.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 0.0),
                          metric);
            stunning.arriveAndAwaitAdvance(); // Failing component is done being deactivated.
            stunning.arriveAndAwaitAdvance(); // Failing component is done cleaning up after itself.
            assertTrue(newSingleton.isActive);
            assertEquals("failed registering failing singleton", thrownMessage.get());
            verifyMetrics(Map.of("activation.count", 6.0,
                                 "activation.millis", 0.0,
                                 "activation.failure.count", 1.0,
                                 "deactivation.count", 5.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 1.0),
                          metric);

            newSingleton.shutdown();
            curator.deconstruct();
            verifyMetrics(Map.of("activation.count", 6.0,
                                 "activation.millis", 0.0,
                                 "activation.failure.count", 1.0,
                                 "deactivation.count", 6.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 0.0),
                          metric);
        }
    }

    @Test
    public void testSingletonsInDifferentContainers() {
        try (MockCurator wrapped = new MockCurator()) {
            MockMetric metric = new MockMetric();
            CuratorWrapper curator = new CuratorWrapper(wrapped, new ManualClock(), Duration.ofMillis(100), metric);

            // Simulate a different container holding the lock.
            Singleton singleton;
            try (Lock lock = wrapped.lock(lockPath, Duration.ofSeconds(1))) {
                singleton = new Singleton(curator);
                assertFalse(singleton.isActive);
                assertFalse(curator.isActive(singleton.id()));
                singleton.phaser.register();
            }

            singleton.phaser.arriveAndAwaitAdvance();
            assertTrue(curator.isActive(singleton.id()));
            assertTrue(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 1.0),
                          metric);

            // Simulate a different container wanting the lock.
            Phaser stunning = new Phaser(2);
            new Thread(() -> {
                try (Lock lock = wrapped.lock(lockPath, Duration.ofSeconds(2))) {
                    stunning.arriveAndAwaitAdvance();
                    stunning.arriveAndAwaitAdvance();
                }
            }).start();

            // Simulate connection loss for our singleton's ZK session.
            ((MockCuratorFramework) wrapped.framework()).connectionStateListeners.listeners.forEach(listener -> listener.stateChanged(null, ConnectionState.LOST));
            singleton.phaser.arriveAndAwaitAdvance();
            stunning.arriveAndAwaitAdvance();
            assertFalse(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 1.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 1.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 0.0),
                          metric);

            // Connection is restored, and the other container releases the lock again.
            stunning.arriveAndAwaitAdvance();
            singleton.phaser.arriveAndAwaitAdvance();
            assertTrue(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 2.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 1.0,
                                 "deactivation.millis", 0.0),
                          metric);

            singleton.phaser.arriveAndDeregister();
            singleton.shutdown();
            curator.deconstruct();
            assertFalse(singleton.isActive);
            verifyMetrics(Map.of("activation.count", 2.0,
                                 "activation.millis", 0.0,
                                 "deactivation.count", 2.0,
                                 "deactivation.millis", 0.0,
                                 "is_active", 0.0),
                          metric);
        }
    }

    static class Singleton implements SingletonWorker {
        final VespaCurator curator;
        Singleton(VespaCurator curator) {
            this.curator = curator;

            curator.register(this, Duration.ofSeconds(2));
        }
        boolean isActive;
        Phaser phaser = new Phaser(1);
        @Override public String id() { return "singleton"; } // ... lest anonymous subclasses get different IDs ... ƪ(`▿▿▿▿´ƪ)
        @Override public void activate() {
            if (isActive) throw new IllegalStateException("already active");
            isActive = true;
            phaser.arriveAndAwaitAdvance();
        }
        @Override public void deactivate() {
            if ( ! isActive) throw new IllegalStateException("already inactive");
            isActive = false;
            phaser.arriveAndAwaitAdvance();
        }
        public void shutdown() { curator.unregister(this, Duration.ofSeconds(2)); }
    }

    static void verifyMetrics(Map<String, Double> expected, MockMetric metrics) {
        expected.forEach((metric, value) -> assertEquals(metric, value, metrics.metrics().get("jdisc.singleton." + metric).get(Map.of("singletonId", "singleton"))));
    }

}
