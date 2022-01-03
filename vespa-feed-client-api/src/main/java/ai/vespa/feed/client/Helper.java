// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
class Helper {

    private Helper() {}

    @SafeVarargs
    static List<Result> await(CompletableFuture<Result>... promises) throws MultiFeedException {
        List<CompletableFuture<Result>> list = new ArrayList<>();
        for (CompletableFuture<Result> p : promises) list.add(p);
        return await(list);
    }

    static List<Result> await(List<CompletableFuture<Result>> promises) throws MultiFeedException {
        try {
            CompletableFuture.allOf(promises.toArray(new CompletableFuture<?>[0])).join();
            return promises.stream()
                    .map(p -> Objects.requireNonNull(p.getNow(null)))
                    .collect(Collectors.toList());
        } catch (CompletionException e) {
            List<FeedException> exceptions = new ArrayList<>();
            for (CompletableFuture<Result> promise : promises) {
                if (promise.isCompletedExceptionally()) {
                    // Lambda is executed on this thread since the future is already completed
                    promise.whenComplete((__, error) -> exceptions.add((FeedException) error));
                }
            }
            throw new MultiFeedException(exceptions);
        }
    }
}
