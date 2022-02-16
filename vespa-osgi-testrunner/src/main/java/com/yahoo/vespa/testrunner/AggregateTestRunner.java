// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * @author jonmv
 */
public class AggregateTestRunner implements TestRunner {

    static final TestRunner noRunner = new TestRunner() {
        final LogRecord record = new LogRecord(Level.WARNING, "No tests were found");
        @Override public Collection<LogRecord> getLog(long after) { return List.of(record); }
        @Override public Status getStatus() { return Status.FAILURE; }
        @Override public CompletableFuture<?> test(Suite suite, byte[] config) { return CompletableFuture.completedFuture(null); }
        @Override public boolean isSupported() { return true; }
    };

    private final List<TestRunner> wrapped;
    private final AtomicInteger current = new AtomicInteger(-1);

    private AggregateTestRunner(List<TestRunner> testRunners) {
        this.wrapped = testRunners;
    }

    public static TestRunner of(Collection<TestRunner> testRunners) {
        List<TestRunner> supported = testRunners.stream().filter(TestRunner::isSupported).collect(toUnmodifiableList());
        return supported.isEmpty() ? noRunner : new AggregateTestRunner(supported);
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        ArrayList<LogRecord> records = new ArrayList<>();
        for (int i = 0; i <= current.get() && i < wrapped.size(); i++)
            records.addAll(wrapped.get(i).getLog(after));

        return records;
    }

    @Override
    public Status getStatus() {
        if (current.get() == -1)
            return Status.NOT_STARTED;

        boolean failed = false;
        boolean inconclusive = false;
        for (int i = 0; i <= current.get(); i++) {
            if (i == wrapped.size())
                return failed ? Status.FAILURE : inconclusive ? Status.INCONCLUSIVE : Status.SUCCESS;

            switch (wrapped.get(i).getStatus()) {
                case ERROR: return Status.ERROR;
                case INCONCLUSIVE: inconclusive = true; break;
                case FAILURE: failed = true;
            }
        }
        return Status.RUNNING;
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] config) {
        if (0 <= current.get() && current.get() < wrapped.size())
            throw new IllegalStateException("Tests already running, should not attempt to start now");

        current.set(-1);
        CompletableFuture<?> aggregate = new CompletableFuture<>();
        CompletableFuture<?> vessel = CompletableFuture.completedFuture(null);
        runNext(suite, config, vessel, aggregate);
        return aggregate;
    }

    private void runNext(Suite suite, byte[] config, CompletableFuture<?> vessel, CompletableFuture<?> aggregate) {
        vessel.whenComplete((__, ___) -> {
            int next = current.incrementAndGet();
            if (next == wrapped.size())
                aggregate.complete(null);
            else
                runNext(suite, config, wrapped.get(next).test(suite, config), aggregate);
        });
    }

    @Override
    public boolean isSupported() {
        return wrapped.stream().anyMatch(TestRunner::isSupported);
    }

    @Override
    public TestReport getReport() {
        return wrapped.stream().map(TestRunner::getReport).filter(Objects::nonNull)
                      .reduce(AggregateTestRunner::merge).orElse(null);
    }

    static TestReport merge(TestReport first, TestReport second) {
        return TestReport.builder()
                         .withAbortedCount(first.abortedCount + second.abortedCount)
                         .withFailedCount(first.failedCount + second.failedCount)
                         .withIgnoredCount(first.ignoredCount + second.ignoredCount)
                         .withSuccessCount(first.successCount + second.successCount)
                         .withTotalCount(first.totalCount + second.totalCount)
                         .withFailures(merged(first.failures, second.failures))
                         .withLogs(merged(first.logLines, second.logLines))
                         .build();
    }

    static <T> List<T> merged(List<T> first, List<T> second) {
        ArrayList<T> merged = new ArrayList<>();
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }

}
