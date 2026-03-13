// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.yahoo.text.Text;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Groups items from multiple caller threads into batches, partitioned by a caller-supplied key.
 * <p>
 * Caller threads block in {@link #execute} until their batch is executed.
 * One caller thread per batch acts as executor; the others wait for the result.
 * No dedicated thread pool is used for dispatching batch operations.
 * <p>
 * A batch is dispatched when it reaches {@code maxBatchSize} or when
 * {@code maxDelay} has elapsed since the first item was added (whichever comes first).
 *
 * @param <K> partition key type — represents additional context or fixed parameters that determine how items are
 *            grouped; items with the same key are batched together
 *            (must have proper {@link Object#equals} and {@link Object#hashCode})
 * @param <I> input item type
 * @param <O> output result type
 * @author bjorncs
 * @author havardpe
 */
public class DynamicBatcher<K, I, O> {

    /** A batched operation that processes multiple inputs for a given key. */
    @FunctionalInterface
    public interface BatchOperation<K, I, O> {
        /**
         * @param key    the partition key
         * @param inputs the list of inputs (at least one, at most {@code maxBatchSize})
         * @return a list of outputs, one per input, in the same order
         */
        List<O> execute(K key, List<I> inputs);
    }

    private static class Batch<K, I, O> {
        final Object monitor = new Object();
        final K key;
        final List<I> inputs;
        final Instant deadline;
        boolean isFilling = true;
        boolean isDone = false;
        List<O> results;
        Exception failure;

        Batch(K key, int maxBatchSize, I initialInput, Instant deadline) {
            this.key = key;
            this.deadline = deadline;
            this.inputs = new ArrayList<>(maxBatchSize);
            this.inputs.add(initialInput);
        }
    }

    private final Object batchesLock = new Object();
    private final BatchOperation<K, I, O> processBatch;
    private final int maxBatchSize;
    private final Duration maxDelay;
    private final Timer timer;
    private final Map<K, Batch<K, I, O>> batches = new HashMap<>();
    private final Runnable onWaiting;

    public DynamicBatcher(int maxBatchSize, Duration maxDelay, BatchOperation<K, I, O> operation) {
        this(Timer.monotonic, maxBatchSize, maxDelay, operation, () -> {});
    }

    DynamicBatcher(Timer timer, int maxBatchSize, Duration maxDelay, BatchOperation<K, I, O> operation) {
        this(timer, maxBatchSize, maxDelay, operation, () -> {});
    }

    DynamicBatcher(Timer timer, int maxBatchSize, Duration maxDelay, BatchOperation<K, I, O> operation, Runnable onWaiting) {
        if (maxBatchSize < 1) throw new IllegalArgumentException("maxBatchSize must be >= 1, got " + maxBatchSize);
        if (maxDelay.isNegative() || maxDelay.isZero()) throw new IllegalArgumentException("maxDelay must be positive, got " + maxDelay);
        this.processBatch = Objects.requireNonNull(operation);
        this.maxBatchSize = maxBatchSize;
        this.maxDelay = maxDelay;
        this.timer = Objects.requireNonNull(timer);
        this.onWaiting = Objects.requireNonNull(onWaiting);
    }

    /**
     * Submits an input for batched execution under the given key.
     * Blocks until the batch containing this item is executed and the result is available.
     *
     * @param key   the partition key
     * @param input the input item
     * @return the output corresponding to this input
     */
    public O execute(K key, I input) {
        if (maxBatchSize == 1) {
            return processBatch.execute(key, List.of(input)).get(0);
        }

        Batch<K, I, O> batch;
        int index = 0;
        boolean isExecutor = false;

        // Determine batch, role and index
        synchronized (batchesLock) {
            var existing = batches.get(key);
            if (existing == null) {
                batch = startNewBatch(key, input);
            } else {
                synchronized (existing.monitor) {
                    if (!existing.isFilling) {
                        batch = startNewBatch(key, input);
                    } else {
                        batch = existing;
                        index = existing.inputs.size();
                        existing.inputs.add(input);
                        if (existing.inputs.size() == maxBatchSize) {
                            batches.remove(key);
                            existing.isFilling = false;
                            isExecutor = true;
                        }
                    }
                }
            }
        }

        boolean removeBatch = false;
        // First thread for the batch waits for the timeout
        if (index == 0) {
            synchronized (batch.monitor) {
                onWaiting.run();
                for (long deadlineMs = batch.deadline.toEpochMilli(), now = timer.instant().toEpochMilli();
                     batch.isFilling && now < deadlineMs;
                     now = timer.instant().toEpochMilli()) {
                    try { batch.monitor.wait(deadlineMs - now); }
                    catch (InterruptedException e) {}
                }
                if (batch.isFilling) {
                    isExecutor = true;
                    batch.isFilling = false;
                    removeBatch = true;
                }
            }
        }

        if (isExecutor) {
            if (removeBatch) {
                synchronized (batchesLock) { batches.remove(batch.key, batch); }
            }
            return executeBatch(batch, index);
        }
        return awaitResult(batch, index);
    }

    /** Wakes up all waiting threads for the batches still in the map. For unit testing. */
    void wakeup() {
        List<Batch<K, I, O>> snapshot;
        synchronized (batchesLock) {
            snapshot = List.copyOf(batches.values());
        }
        for (var batch : snapshot) {
            synchronized (batch.monitor) { batch.monitor.notifyAll(); }
        }
    }

    private Batch<K, I, O> startNewBatch(K key, I input) {
        assert Thread.holdsLock(batchesLock) : "Caller must hold lock on 'batchesLock'";
        var b = new Batch<K, I, O>(key, maxBatchSize, input, timer.instant().plus(maxDelay));
        batches.put(key, b);
        return b;
    }

    private O executeBatch(Batch<K, I, O> batch, int index) {
        List<I> inputs;
        synchronized (batch.monitor) {
            inputs = List.copyOf(batch.inputs);
        }
        List<O> results;
        try {
            results = List.copyOf(processBatch.execute(batch.key, inputs));
            if (results.size() != inputs.size())
                throw new IllegalStateException(
                        Text.format("BatchOperation returned %d results for %d inputs", results.size(), inputs.size()));
        } catch (Exception e) {
            completeBatch(batch, null, e);
            throw e;
        }
        completeBatch(batch, results, null);
        return results.get(index);
    }

    private void completeBatch(Batch<K, I, O> batch, List<O> results, Exception failure) {
        synchronized (batch.monitor) {
            batch.isDone = true;
            if (failure == null) {
                batch.results = results;
            } else {
                batch.failure = failure;
            }
            batch.monitor.notifyAll();
        }
    }

    private O awaitResult(Batch<K, I, O> batch, int index) {
        synchronized (batch.monitor) {
            while (!batch.isDone) {
                try { batch.monitor.wait(); }
                catch (InterruptedException e) {}
            }
            if (batch.failure != null) throw Exceptions.throwUnchecked(batch.failure);
            return batch.results.get(index);
        }
    }
}
