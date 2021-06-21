// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AtomicLong targetInflight;
    private long updateNanos = 0;
    private final List<AtomicLong> latencies = new ArrayList<>();
    private final double weight = 0.9; // Higher weight favours higher (own) throughput, at the cost of (shared) latency.

    public DynamicThrottler(FeedClientBuilder builder) {
        super(builder);
        this.targetInflight = new AtomicLong(128L * builder.connectionsPerEndpoint * builder.endpoints.size());
        for (int i = 0; i < 128; i++)
            latencies.add(new AtomicLong(-1));
    }

    @Override
    public void sent(long inflight, CompletableFuture<HttpResponse> vessel) {
        long startNanos = System.nanoTime();
        if (updateNanos == 0) updateNanos = System.nanoTime();
        boolean update = startNanos - updateNanos >= 1e8; // Ship ten updates per second.
        if (update) updateNanos = startNanos;

        vessel.whenComplete((response, thrown) -> {
            // Use buckets for latency measurements, with inflight along a log scale,
            // and with minInflight and maxInflight at the ends.
            int index = (int) (latencies.size() * log(max(1, (double) inflight / minInflight))
                                                / log(256)); // 4096 (server max streams per connection) / 16 (our min per connection)
            long nowNanos = System.nanoTime();
            long latencyNanos = nowNanos - startNanos;
            latencies.get(index).set(latencyNanos);
            if ( ! update)
                return;

            // Loop over latency measurements and pick the one which optimises throughput and latency.
            double choice = -1;
            double max = -1;
            for (int i = latencies.size(); i-- > 0; ) {
                double latency = latencies.get(i).get();
                if (latency < 0) continue; // Skip unknown values.
                double target = minInflight * pow(256, (i + 0.5) / latencies.size());
                double objective = pow(target, weight) / latency; // Optimise throughput (weight), but also latency (1 - weight).
                if (objective > max) {
                    max = objective;
                    choice = target;
                }
            }
            long target = (long) ((random() * 0.25 + 0.90) * choice); // Random walk, skewed towards increase.
            targetInflight.set(max(minInflight, min(maxInflight, target)));
        });
    }

    @Override
    public void success() {
        super.success();
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
