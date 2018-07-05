// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import com.google.inject.Provider;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.ContainerThread;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.application.MetricProvider;
import com.yahoo.jdisc.core.SystemTimer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class StateMonitorBenchmarkTest {

    private final static int NUM_THREADS = 32;
    private final static int NUM_UPDATES = 1000;//0000;

    @Test
    public void requireThatHealthMonitorDoesNotBlockMetricThreads() throws Exception {
        StateMonitor monitor = new StateMonitor(new HealthMonitorConfig(new HealthMonitorConfig.Builder()),
                                                new SystemTimer());
        Provider<MetricConsumer> provider = MetricConsumerProviders.wrap(monitor);
        performUpdates(provider, 8);
        for (int i = 1; i <= NUM_THREADS; i *= 2) {
            long millis = performUpdates(provider, i);
            System.err.format("%2d threads, %5d millis => %9d ups\n",
                              i, millis, (int)((i * NUM_UPDATES) / (millis / 1000.0)));
        }
        monitor.deconstruct();
    }

    private long performUpdates(Provider<MetricConsumer> metricProvider, int numThreads) throws Exception {
        ThreadFactory threadFactory = new ContainerThread.Factory(metricProvider);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads, threadFactory);
        List<Callable<Boolean>> tasks = new ArrayList<>(numThreads);
        for (int i = 0; i < numThreads; ++i) {
            tasks.add(new UpdateTask(new MetricProvider(metricProvider).get()));
        }
        long before = System.nanoTime();
        List<Future<Boolean>> results = executor.invokeAll(tasks);
        long after = System.nanoTime();
        for (Future<Boolean> result : results) {
            assertTrue(result.get());
        }
        return TimeUnit.NANOSECONDS.toMillis(after - before);
    }

    public static class UpdateTask implements Callable<Boolean> {

        final Metric metric;

        UpdateTask(Metric metric) {
            this.metric = metric;
        }

        @Override
        public Boolean call() throws Exception {
            Metric.Context ctx = metric.createContext(Collections.<String, Object>emptyMap());
            for (int i = 0; i < NUM_UPDATES; ++i) {
                metric.add("foo", 69L, ctx);
            }
            return true;
        }
    }
}
