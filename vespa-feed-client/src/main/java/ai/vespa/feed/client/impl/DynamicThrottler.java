// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        targetInflight = new AtomicLong(minInflight);
    }

    @Override
    public void sent(long __, CompletableFuture<HttpResponse> ___) {
        double currentInflight = targetInflight();
        if (++sent * sent * sent < 1e3 * currentInflight * currentInflight)
            return;

        sent = 0;
        double elapsedNanos = -startNanos + (startNanos = System.nanoTime());
        double currentThroughput = ok.getAndSet(0) / elapsedNanos;

        // Use buckets for throughput over inflight, along the log-scale, in [minInflight, maxInflight).
        int index = (int) (throughputs.length * log(max(1, min(255, currentInflight / minInflight)))
                                              / log(256)); // 512 (server max streams per connection) / 2 (our min per connection)
        throughputs[index] = currentThroughput;

        // Loop over throughput measurements and pick the one which optimises throughput and latency.
        double best = currentInflight;
        double max = -1;
        int j = -1, k = -1, choice = 0;
        double s = 0;
        for (int i = 0; i < throughputs.length; i++) {
            if (throughputs[i] == 0) continue; // Skip unknown values.
            double inflight = minInflight * pow(256, (i + 0.5) / throughputs.length);
            double objective = throughputs[i] * pow(inflight, (weight - 1)); // Optimise throughput (weight), but also latency (1 - weight).
            if (objective > max) {
                max = objective;
                best = inflight;
                choice = i;
            }
            // Additionally, smooth the throughput values, to reduce the impact of noise, and reduce jumpiness.
            if (j != -1) {
                double t = throughputs[j];
                if (k != -1) throughputs[j] = (18 * t + throughputs[i] + s) / 20;
                s = t;
            }
            k = j;
            j = i;
        }
        long target = (long) ((random() * 0.40 + 0.84) * best + random() * 4 - 1); // Random step, skewed towards increase.
        // If the best inflight is at the high end of the known, we override the random walk to speed up upwards exploration.
        if (choice == j && choice + 1 < throughputs.length)
            target = (long) (1 + minInflight * pow(256, (choice + 1.5) / throughputs.length));
        targetInflight.set(max(minInflight, min(maxInflight, target)));
    }

    @Override
    public void success() {
        super.success();
        ok.incrementAndGet();
    }

    @Override
    public long targetInflight() {
        return min(super.targetInflight(), targetInflight.get());
    }

}
