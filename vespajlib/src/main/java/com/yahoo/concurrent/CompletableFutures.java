// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import java.util.ArrayList;
import java.util.List;
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
}
