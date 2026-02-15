// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 * @author havardpe
 */
@Timeout(30)
class DynamicBatcherTest {

    @Test
    void batch_dispatched_when_full() throws Exception {
        var invocations = new CopyOnWriteArrayList<List<String>>();
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 3, Duration.ofSeconds(10), (key, inputs) -> {
                    invocations.add(List.copyOf(inputs));
                    return inputs.stream().map(s -> s + "-out").toList();
                });

        var barrier = new CyclicBarrier(3);
        var results = new String[3];
        var threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            var idx = i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    results[idx] = batcher.execute("k", "item-" + idx);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (var t : threads) t.join();

        assertEquals(1, invocations.size());
        assertEquals(3, invocations.get(0).size());
        for (int i = 0; i < 3; i++) {
            assertEquals("item-" + i + "-out", results[i]);
        }
    }

    @Test
    void timeout_triggers_partial_batch() throws Exception {
        var timer = new ManualTimer();
        var invocations = new CopyOnWriteArrayList<List<String>>();
        var waiting = new CountDownLatch(1);
        var batcher = new DynamicBatcher<String, String, String>(
                timer, 100, Duration.ofMillis(50), (key, inputs) -> {
                    invocations.add(List.copyOf(inputs));
                    return inputs.stream().map(s -> s + "-out").toList();
                }, waiting::countDown);

        var resultRef = new AtomicReference<String>();
        var thread = new Thread(() -> resultRef.set(batcher.execute("k", "solo")));
        thread.setDaemon(true);
        thread.start();

        waiting.await();
        timer.advance(50);
        batcher.wakeup();
        thread.join();

        assertEquals(1, invocations.size());
        assertEquals(List.of("solo"), invocations.get(0));
        assertEquals("solo-out", resultRef.get());
    }

    @Test
    void separate_keys_produce_separate_batches() throws Exception {
        var invocations = new ConcurrentHashMap<String, List<String>>();
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 2, Duration.ofSeconds(10), (key, inputs) -> {
                    invocations.put(key, List.copyOf(inputs));
                    return inputs.stream().map(s -> key + ":" + s).toList();
                });

        var results = new ConcurrentHashMap<String, String>();
        var barrier = new CyclicBarrier(4);
        var threads = new ArrayList<Thread>();
        for (var entry : List.of(Map.entry("a", "x1"), Map.entry("a", "x2"),
                                 Map.entry("b", "y1"), Map.entry("b", "y2"))) {
            var t = new Thread(() -> {
                try {
                    barrier.await();
                    var r = batcher.execute(entry.getKey(), entry.getValue());
                    results.put(entry.getValue(), r);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (var t : threads) t.join();

        assertEquals(2, invocations.size());
        assertEquals(2, invocations.get("a").size());
        assertEquals(2, invocations.get("b").size());
        assertEquals("a:x1", results.get("x1"));
        assertEquals("a:x2", results.get("x2"));
        assertEquals("b:y1", results.get("y1"));
        assertEquals("b:y2", results.get("y2"));
    }

    @Test
    void operation_failure_propagated_to_all_callers() throws Exception {
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 3, Duration.ofSeconds(10),
                (key, inputs) -> { throw new RuntimeException("batch-fail"); });

        var barrier = new CyclicBarrier(3);
        var errors = new ConcurrentHashMap<Integer, RuntimeException>();
        var threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            var idx = i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    batcher.execute("k", "item-" + idx);
                } catch (RuntimeException e) {
                    errors.put(idx, e);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (var t : threads) t.join();

        assertEquals(3, errors.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("batch-fail", errors.get(i).getMessage());
        }
    }

    @Test
    void checked_exception_thrown_as_is() throws Exception {
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 2, Duration.ofSeconds(10),
                (key, inputs) -> { throw Exceptions.throwUnchecked(new IOException("io-fail")); });

        var barrier = new CyclicBarrier(2);
        var errors = new ConcurrentHashMap<Integer, Exception>();
        var threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            var idx = i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    batcher.execute("k", "item-" + idx);
                } catch (Exception e) {
                    errors.put(idx, e);
                }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (var t : threads) t.join();

        assertEquals(2, errors.size());
        for (var error : errors.values()) {
            assertInstanceOf(IOException.class, error);
            assertEquals("io-fail", error.getMessage());
        }
    }

    @Test
    void result_size_mismatch_throws() throws Exception {
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 2, Duration.ofSeconds(10),
                (key, inputs) -> List.of("only-one"));

        var barrier = new CyclicBarrier(2);
        var errors = new ConcurrentHashMap<Integer, RuntimeException>();
        var threads = new Thread[2];
        for (int i = 0; i < 2; i++) {
            var idx = i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    batcher.execute("k", "item-" + idx);
                } catch (RuntimeException e) {
                    errors.put(idx, e);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            threads[i].setDaemon(true);
            threads[i].start();
        }
        for (var t : threads) t.join();

        assertEquals(2, errors.size());
        for (var error : errors.values()) {
            assertInstanceOf(IllegalStateException.class, error);
            assertEquals("BatchOperation returned 1 results for 2 inputs", error.getMessage());
        }
    }

    @Test
    void max_batch_size_one_executes_immediately() throws Exception {
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 1, Duration.ofSeconds(10),
                (key, inputs) -> inputs.stream().map(s -> s + "!").toList());

        assertEquals("hello!", batcher.execute("k", "hello"));
        assertEquals("world!", batcher.execute("k", "world"));
    }

    @Test
    void concurrent_stress_test() throws Exception {
        var threadCount = 50;
        var batchSize = 5;
        var batcher = new DynamicBatcher<Integer, Integer, Integer>(
                batchSize, Duration.ofMillis(5),
                (key, inputs) -> inputs.stream().map(i -> i * 10 + key).toList());

        var barrier = new CyclicBarrier(threadCount);
        var results = new ConcurrentHashMap<String, Integer>();
        var threads = new ArrayList<Thread>();

        for (int i = 0; i < threadCount; i++) {
            var key = i % 3;
            var input = i;
            var t = new Thread(() -> {
                try {
                    barrier.await();
                    var result = batcher.execute(key, input);
                    results.put(key + ":" + input, result);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            t.setDaemon(true);
            t.start();
            threads.add(t);
        }
        for (var t : threads) t.join();

        assertEquals(threadCount, results.size());
        for (int i = 0; i < threadCount; i++) {
            var key = i % 3;
            assertEquals(i * 10 + key, results.get(key + ":" + i));
        }
    }

    @Test
    void partial_batch_with_multiple_items_on_timeout() throws Exception {
        var timer = new ManualTimer();
        var invocations = new CopyOnWriteArrayList<List<String>>();
        var creatorWaiting = new CountDownLatch(1);
        var batcher = new DynamicBatcher<String, String, String>(
                timer, 100, Duration.ofMillis(50), (key, inputs) -> {
                    invocations.add(List.copyOf(inputs));
                    return inputs.stream().map(s -> s + "-out").toList();
                }, creatorWaiting::countDown);

        var results = new ConcurrentHashMap<String, String>();

        // Start CREATOR thread
        var t1 = new Thread(() -> results.put("item-0", batcher.execute("k", "item-0")));
        t1.setDaemon(true);
        t1.start();
        creatorWaiting.await();

        // Start WAITER thread and wait for it to join the batch and enter wait
        var t2 = new Thread(() -> results.put("item-1", batcher.execute("k", "item-1")));
        t2.setDaemon(true);
        t2.start();
        while (t2.getState() != Thread.State.WAITING) Thread.onSpinWait();

        // Trigger timeout
        timer.advance(50);
        batcher.wakeup();
        t1.join();
        t2.join();

        assertEquals(1, invocations.size());
        assertEquals(2, invocations.get(0).size());
        assertEquals("item-0-out", results.get("item-0"));
        assertEquals("item-1-out", results.get("item-1"));
    }

    @Test
    void sequential_batches_on_same_key() throws Exception {
        var invocations = new CopyOnWriteArrayList<List<String>>();
        var batcher = new DynamicBatcher<String, String, String>(
                new ManualTimer(), 2, Duration.ofSeconds(10), (key, inputs) -> {
                    invocations.add(List.copyOf(inputs));
                    return inputs.stream().map(s -> s + "-out").toList();
                });

        for (int round = 0; round < 2; round++) {
            var barrier = new CyclicBarrier(2);
            var results = new String[2];
            var threads = new Thread[2];
            var r = round;
            for (int i = 0; i < 2; i++) {
                var idx = i;
                threads[i] = new Thread(() -> {
                    try {
                        barrier.await();
                        results[idx] = batcher.execute("k", "r" + r + "-" + idx);
                    } catch (Exception e) { throw new RuntimeException(e); }
                });
                threads[i].setDaemon(true);
                threads[i].start();
            }
            for (var t : threads) t.join();

            assertEquals(round + 1, invocations.size());
            assertEquals(2, invocations.get(round).size());
            for (int i = 0; i < 2; i++) {
                assertEquals("r" + round + "-" + i + "-out", results[i]);
            }
        }
    }

    @Test
    void constructor_validates_parameters() {
        DynamicBatcher.BatchOperation<String, String, String> op = (k, i) -> i;
        assertThrows(IllegalArgumentException.class, () -> new DynamicBatcher<>(0, Duration.ofSeconds(1), op));
        assertThrows(IllegalArgumentException.class, () -> new DynamicBatcher<>(1, Duration.ZERO, op));
        assertThrows(IllegalArgumentException.class, () -> new DynamicBatcher<>(1, Duration.ofMillis(-1), op));
        assertThrows(NullPointerException.class, () -> new DynamicBatcher<>(1, Duration.ofSeconds(1), null));
        assertThrows(NullPointerException.class, () -> new DynamicBatcher<>(1, null, op));
    }
}
