// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.yahoo.yolean.UncheckedInterruptedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Helper for {@link java.util.concurrent.CompletableFuture} / {@link java.util.concurrent.CompletionStage}.
 *
 * @author bjorncs
 */
public class CompletableFutures {

    private CompletableFutures() {}

    /**
     * Returns a new completable future that is either
     * - completed when any of the provided futures complete without exception
     * - completed exceptionally once all provided futures complete exceptionally
     */
    public static <T> CompletableFuture<T> firstOf(List<CompletableFuture<T>> futures) {
        class Combiner {
            final Object monitor = new Object();
            final CompletableFuture<T> combined = new CompletableFuture<>();
            final int futuresCount;

            Throwable error = null;
            int exceptionCount = 0;

            Combiner(int futuresCount) { this.futuresCount = futuresCount; }

            void onCompletion(T value, Throwable error) {
                if (combined.isDone()) return;
                T valueToComplete = null;
                Throwable exceptionToComplete = null;

                synchronized (monitor) {
                    if (value != null) {
                        valueToComplete = value;
                    } else {
                        if (this.error == null) {
                            this.error = error;
                        } else {
                            this.error.addSuppressed(error);
                        }
                        if (++exceptionCount == futuresCount) {
                            exceptionToComplete = this.error;
                        }
                    }
                }
                if (valueToComplete != null) {
                    combined.complete(value);
                } else if (exceptionToComplete != null) {
                    combined.completeExceptionally(exceptionToComplete);
                }
            }
        }

        int size = futures.size();
        if (size == 0) throw new IllegalArgumentException();
        if (size == 1) return futures.get(0);
        Combiner combiner = new Combiner(size);
        futures.forEach(future -> future.whenComplete(combiner::onCompletion));
        return combiner.combined;
    }

    /** Similar to {@link CompletableFuture#allOf(CompletableFuture[])} but returns a list of the results */
    public static <T> CompletableFuture<List<T>> allOf(List<CompletableFuture<T>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(__ -> {
                    List<T> results = new ArrayList<>();
                    for (CompletableFuture<T> f : futures) {
                        try {
                            results.add(f.get());
                        } catch (InterruptedException | ExecutionException e) {
                            // Should not happen since all futures are completed without exception
                            throw new IllegalStateException(e);
                        }
                    }
                    return results;
                });
    }

    /**
     * Helper for migrating from {@link ListenableFuture} to {@link CompletableFuture} in Vespa public apis
     * @deprecated to be removed in Vespa 8
     */
    @SuppressWarnings("unchecked")
    @Deprecated(forRemoval = true, since = "7")
    public static <V> ListenableFuture<V> toGuavaListenableFuture(CompletableFuture<V> future) {
        if (future instanceof ListenableFuture) {
            return ((ListenableFuture<V>) future);
        }
        SettableFuture<V> guavaFuture = SettableFuture.create();
        future.whenComplete((result, error) -> {
            if (result != null) guavaFuture.set(result);
            else if (error instanceof CancellationException) guavaFuture.setException(error);
            else guavaFuture.cancel(true);
        });
        return guavaFuture;
    }

    /**
     * Helper for migrating from {@link ListenableFuture} to {@link CompletableFuture} in Vespa public apis
     * @deprecated to be removed in Vespa 8
     */
    @Deprecated(forRemoval = true, since = "7")
    public static <V> CompletableFuture<V> toCompletableFuture(ListenableFuture<V> guavaFuture) {
        CompletableFuture<V> future = new CompletableFuture<>();
        guavaFuture.addListener(
                () -> {
                    if (guavaFuture.isCancelled()) future.cancel(true);
                    try {
                        V value = guavaFuture.get();
                        future.complete(value);
                    } catch (InterruptedException e) {
                        // Should not happens since listener is invoked after future is complete
                        throw new UncheckedInterruptedException(e);
                    } catch (ExecutionException e) {
                        future.completeExceptionally(e.getCause());
                    }
                },
                Runnable::run);
        return future;
    }

}
