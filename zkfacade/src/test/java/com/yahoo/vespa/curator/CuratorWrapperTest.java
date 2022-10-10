package com.yahoo.vespa.curator;

import com.yahoo.path.Path;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.api.AbstractSingletonWorker;
import com.yahoo.vespa.curator.api.VespaCurator;
import com.yahoo.vespa.curator.api.VespaCurator.Meta;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.mock.MockCuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
            CuratorWrapper curator = new CuratorWrapper(wrapped);

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
                assertEquals(List.of("user", "path"), wrapped.getChildren(Path.createRoot()));
                assertEquals(List.of("path"), wrapped.getChildren(CuratorWrapper.userRoot));
            }

            try (AutoCloseable lock = curator.lock(path, Duration.ofSeconds(1))) {
                // Both previous locks were released.
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
            CuratorWrapper curator = new CuratorWrapper(wrapped, clock, Duration.ofMillis(100));

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
            singleton = new Singleton(curator) {
                @Override public void activate() {
                    // Set up sync in clock on next renewLease.
                    super.activate();
                    stunning.register();
                }
            };
            assertTrue(singleton.isActive);

            stunning.arriveAndAwaitAdvance(); // Wait for next renewLease.
            stunning.arriveAndAwaitAdvance(); // Let next renewLease complete.
            stunning.arriveAndAwaitAdvance(); // Wait for next updateStatus.
            clock.advance(Curator.ZK_SESSION_TIMEOUT);
            singleton.phaser.register();      // Set up so we can synchronise with deactivation.
            stunning.arriveAndDeregister();   // Let lease expire, and ensure further ticks complete if we lose the race to unregister.

            singleton.phaser.arriveAndAwaitAdvance();
            assertFalse(singleton.isActive);

            // Singleton is reactivated next tick.
            singleton.phaser.arriveAndAwaitAdvance();
            assertTrue(singleton.isActive);

            // Manager unregisters remaining singletons on shutdown.
            curator.deconstruct();
            singleton.phaser.arriveAndAwaitAdvance();
            assertFalse(singleton.isActive);
        }
    }

    @Test
    public void testSingletonsInSameContainer() {
        try (Curator wrapped = new MockCurator()) {
            CuratorWrapper curator = new CuratorWrapper(wrapped);

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

            // Add a singleton which fails activation.
            Phaser stunning = new Phaser(2);
            AtomicReference<String> thrownMessage = new AtomicReference<>();
            new Thread(() -> {
                RuntimeException e = assertThrows(RuntimeException.class,
                                                  () -> new Singleton(curator) {
                                                      @Override public void activate() {
                                                          throw new RuntimeException();
                                                      }
                                                      @Override public void deactivate() {
                                                          stunning.arriveAndAwaitAdvance();
                                                          stunning.arriveAndAwaitAdvance();
                                                          throw new RuntimeException();
                                                      }
                                                      @Override public String toString() {
                                                          return "failing singleton";
                                                      }
                                                  });
                stunning.arriveAndAwaitAdvance();
                thrownMessage.set(e.getMessage());
            }).start();

            stunning.arriveAndAwaitAdvance(); // Failing component is about to be deactivated.
            assertFalse(newSingleton.isActive);
            assertTrue(curator.isActive(newSingleton.id())); // No actual active components, but container has the lease.
            stunning.arriveAndAwaitAdvance(); // Failing component is done being deactivated.
            stunning.arriveAndAwaitAdvance(); // Failing component is done cleaning up after itself.
            assertTrue(newSingleton.isActive);
            assertEquals("failed to register failing singleton", thrownMessage.get());
            newSingleton.shutdown();

            curator.deconstruct();
        }
    }

    @Test
    public void testSingletonsInDifferentContainers() {
        try (MockCurator wrapped = new MockCurator()) {
            CuratorWrapper curator = new CuratorWrapper(wrapped, Clock.systemUTC(), Duration.ofMillis(100));

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
            stunning.arriveAndAwaitAdvance();
            singleton.phaser.arriveAndAwaitAdvance();
            assertFalse(singleton.isActive);

            // Connection is restored, and the other container releases the lock again.
            stunning.arriveAndAwaitAdvance();
            singleton.phaser.arriveAndAwaitAdvance();
            assertTrue(singleton.isActive);
            singleton.phaser.arriveAndDeregister();
            singleton.shutdown();
            assertFalse(singleton.isActive);

            curator.deconstruct();
        }
    }

    static class Singleton extends AbstractSingletonWorker {
        Singleton(VespaCurator curator) { register(curator, Duration.ofSeconds(2)); }
        boolean isActive;
        Phaser phaser = new Phaser(1);
        @Override public String id() { return "singleton"; } // ... lest anonymous subclasses get different IDs ... ƪ(`▿▿▿▿´ƪ)
        @Override public void activate() { isActive = true; phaser.arriveAndAwaitAdvance(); }
        @Override public void deactivate() { isActive = false; phaser.arriveAndAwaitAdvance(); }
        public void shutdown() { unregister(Duration.ofSeconds(2)); }
    }

}
