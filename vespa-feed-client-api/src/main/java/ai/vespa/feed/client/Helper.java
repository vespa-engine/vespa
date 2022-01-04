// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ai.vespa.feed.client.FeedClientBuilder.PREFERRED_IMPLEMENTATION_PROPERTY;

/**
 * @author bjorncs
 */
class Helper {

    private static final AtomicReference<Supplier<FeedClientBuilder>> feedClientBuilderSupplier = new AtomicReference<>(Helper::getFeedClientBuilder);

    static final void setFeedClientBuilderSupplier(Supplier<FeedClientBuilder> supplier) {
        feedClientBuilderSupplier.set(supplier);
    }

    static Supplier<FeedClientBuilder> getFeedClientBuilderSupplier() {
        return feedClientBuilderSupplier.get();
    }

    static FeedClientBuilder getFeedClientBuilder() {
        String defaultImplementation = "ai.vespa.feed.client.impl.FeedClientBuilderImpl";
        String preferredImplementation = System.getProperty(PREFERRED_IMPLEMENTATION_PROPERTY, defaultImplementation);
        Iterator<FeedClientBuilder> iterator = ServiceLoader.load(FeedClientBuilder.class).iterator();
        if (iterator.hasNext()) {
            List<FeedClientBuilder> builders = new ArrayList<>();
            iterator.forEachRemaining(builders::add);
            return builders.stream()
                    .filter(builder -> preferredImplementation.equals(builder.getClass().getName()))
                    .findFirst()
                    .orElse(builders.get(0));
        } else {
            try {
                Class<?> aClass = Class.forName(preferredImplementation);
                for (Constructor<?> constructor : aClass.getConstructors()) {
                    if (constructor.getParameterTypes().length == 0) {
                        return ((FeedClientBuilder) constructor.newInstance());
                    }
                }
                throw new RuntimeException("Could not find Feed client builder implementation");
            } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

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
