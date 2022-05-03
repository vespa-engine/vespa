package com.yahoo.yolean.concurrent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

/**
 * @author jonmv
 */
public class MemoizedTest {

    final Phaser phaser = new Phaser();
    final int threads = 128;

    @Test
    public void test() throws ExecutionException, InterruptedException {
        var lazy = new Memoized<>(new OnceSupplier(), OnceCloseable::close);
        phaser.register(); // test thread
        phaser.register(); // whoever calls the factory

        Phaser latch = new Phaser(threads + 1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            futures.add(executor.submit(() -> {
                latch.arriveAndAwaitAdvance();
                lazy.get().rendezvous();
                while (true) lazy.get();
            }));
        }

        // All threads waiting for latch, will race to factory
        latch.arriveAndAwaitAdvance();

        // One thread waiting in factory, the others are blocked, will go to rendezvous
        phaser.arriveAndAwaitAdvance();

        // All threads waiting in rendezvous, will repeatedly get until failure
        phaser.arriveAndAwaitAdvance();

        // Unsynchronized close should be detected by all threads
        lazy.close();

        // Close should carry through only once
        lazy.close();

        assertEquals("already closed",
                     assertThrows(IllegalStateException.class, lazy::get).getMessage());

        for (Future<?> future : futures)
            assertEquals("java.lang.IllegalStateException: already closed",
                         assertThrows(ExecutionException.class, future::get).getMessage());

        executor.shutdown();
    }

    @Test
    public void closeBeforeFirstGet() throws Exception {
        OnceSupplier supplier = new OnceSupplier();
        Memoized<OnceCloseable, ?> lazy = Memoized.of(supplier);
        lazy.close();
        assertEquals("already closed",
                     assertThrows(IllegalStateException.class, lazy::get).getMessage());
        lazy.close();
        assertFalse(supplier.initialized.get());
    }

    class OnceSupplier implements Supplier<OnceCloseable> {
        final AtomicBoolean initialized = new AtomicBoolean();
        @Override public OnceCloseable get() {
            phaser.arriveAndAwaitAdvance();
            if ( ! initialized.compareAndSet(false, true)) fail("initialized more than once");
            phaser.bulkRegister(threads - 1); // register all the threads who didn't get the factory
            return new OnceCloseable();
        }
    }

    class OnceCloseable implements AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean();
        @Override public void close() {
            if ( ! closed.compareAndSet(false, true)) fail("closed more than once");
        }
        void rendezvous() {
            phaser.arriveAndAwaitAdvance();
        }
    }

}
