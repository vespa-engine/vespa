// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.log;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.pow;
import static java.lang.Math.random;

/**
 * Samples latency as a function of inflight requests, and regularly adjusts to the optimal value.
 *
 * @author jonmv
 */
public class DynamicThrottler extends StaticThrottler {

    private final AtomicLong ok = new AtomicLong(0);
    private final AtomicLong targetInflight;
    private final double weight = 0.7;
    private final double[] throughputs = new double[128];
    private long startNanos = System.nanoTime();
    private long sent = 0;

    public DynamicThrottler(FeedClientBuilderImpl builder) {
        super(builder);
        targetInflight = new AtomicLong(8 * minInflight);
    }

    @Override
    public void sent(long __, CompletableFuture<HttpResponse> ___) {
        double currentInflight = targetInflight.get();
        if (++sent * sent * sent < 1e2 * currentInflight * currentInflight)
            return;

        sent = 0;
        double elapsedNanos = -startNanos + (startNanos = System.nanoTime());
        double currentThroughput = ok.getAndSet(0) / elapsedNanos;

        // Use buckets for throughput over inflight, along the log-scale, in [minInflight, maxInflight).
        int index = (int) (throughputs.length * log(max(1, min(255, currentInflight / minInflight)))
                                              / log(256)); // 4096 (server max streams per connection) / 16 (our min per connection)
        throughputs[index] = currentThroughput;

        // Loop over throughput measurements and pick the one which optimises throughput and latency.
        double choice = currentInflight;
        double max = -1;
        for (int i = throughputs.length; i-- > 0; ) {
            if (throughputs[i] == 0) continue; // Skip unknown values.
            double inflight = minInflight * pow(256, (i + 0.5) / throughputs.length);
            double objective = throughputs[i] * pow(inflight, (weight - 1)); // Optimise throughput (weight), but also latency (1 - weight).
            if (objective > max) {
                max = objective;
                choice = inflight;
            }
        }
        long target = (long) ((random() * 0.20 + 0.92) * choice); // Random walk, skewed towards increase.
        targetInflight.set(max(minInflight, min(maxInflight, target)));
    }

    @Override
    public void success() {
        super.success();
        ok.incrementAndGet();
    }

    @Override
    public void throttled(long inflight) {
        super.throttled(inflight);
    }

    @Override
    public long targetInflight() {
        return min(super.targetInflight(), targetInflight.get());
    }

}
