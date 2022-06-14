// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.testrunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * @author jonmv
 */
public class AggregateTestRunner implements TestRunner {

    private static final Logger log = Logger.getLogger(AggregateTestRunner.class.getName());

    private final List<TestRunner> wrapped;
    private int current = -1;
    private boolean error = false;
    private final Object monitor = new Object();

    private AggregateTestRunner(List<TestRunner> testRunners) {
        this.wrapped = testRunners;
    }

    public static TestRunner of(Collection<TestRunner> testRunners) {
        return new AggregateTestRunner(List.copyOf(testRunners));
    }

    @Override
    public Collection<LogRecord> getLog(long after) {
        ArrayList<LogRecord> records = new ArrayList<>();
        synchronized (monitor) {
            for (int i = 0; i <= current && i < wrapped.size(); i++)
                records.addAll(wrapped.get(i).getLog(after));
        }
        return records;
    }

    @Override
    public Status getStatus() {
        if (error) return Status.ERROR;
        synchronized (monitor) {
            if (current == -1)
                return Status.NOT_STARTED;

            Status status = Status.NO_TESTS;
            for (int i = 0; i <= current; i++) {
                if (i == wrapped.size())
                    return status;

                Status next = wrapped.get(i).getStatus();
                status = status.ordinal() < next.ordinal() ? status : next;
            }
            return Status.RUNNING;
        }
    }

    @Override
    public CompletableFuture<?> test(Suite suite, byte[] config) {
        synchronized (monitor) {
            if (0 <= current && current < wrapped.size())
                throw new IllegalStateException("Tests already running, should not attempt to start now");

            current = -1;
            CompletableFuture<?> aggregate = new CompletableFuture<>();
            CompletableFuture<?> vessel = CompletableFuture.completedFuture(null);
            runNext(suite, config, vessel, aggregate);
            return aggregate;
        }
    }

    private void runNext(Suite suite, byte[] config, CompletableFuture<?> vessel, CompletableFuture<?> aggregate) {
        vessel.whenComplete((__, ___) -> {
            synchronized (monitor) {
                if (++current < wrapped.size())
                    try {
                        runNext(suite, config, wrapped.get(current).test(suite, config), aggregate);
                    }
                    catch (Throwable t) {
                        log.log(Level.SEVERE, "Failed running next suite (" + wrapped.get(current) + ")", t);
                        error = true;
                    }
                else
                    aggregate.complete(null);
            }
        });
    }

    @Override
    public TestReport getReport() {
        TestReport report = null;
        synchronized (monitor) {
            for (int i = 0; i < current && i < wrapped.size(); i++)
                report = merge(report, wrapped.get(i).getReport());
        }
        return report;
    }

    static TestReport merge(TestReport first, TestReport second) {
        return first == null ? second : second == null ? first : first.mergedWith(second);
    }

}
