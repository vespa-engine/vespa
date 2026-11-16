// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertSame;

public class MetricIdTest {

    @Test
    public void concurrent_interning_gives_one_instance_per_id() throws Exception {
        int threads = 8, ids = 2000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MetricId[]>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(executor.submit(() -> {
                    start.await();
                    MetricId[] interned = new MetricId[ids];
                    for (int i = 0; i < ids; i++)
                        interned[i] = MetricId.toMetricId("concurrent_interning_test_" + i);
                    return interned;
                }));
            }
            start.countDown();
            MetricId[] first = results.get(0).get();
            for (var result : results) {
                MetricId[] interned = result.get();
                for (int i = 0; i < ids; i++)
                    assertSame(first[i], interned[i]);
            }
        } finally {
            executor.shutdownNow();
        }
    }

}
