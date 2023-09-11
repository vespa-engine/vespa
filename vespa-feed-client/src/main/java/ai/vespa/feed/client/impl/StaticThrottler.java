// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Reduces max throughput whenever throttled; increases it slowly whenever successful responses are obtained.
 *
 * @author jonmv
 */
public class StaticThrottler implements Throttler {

    protected final long maxInflight;
    protected final long minInflight;
    private final AtomicLong targetX10;

    public StaticThrottler(FeedClientBuilderImpl builder) {
        minInflight = 4L * builder.connectionsPerEndpoint * builder.endpoints.size();
        // Add 1 to buffer some extra requests
        maxInflight = Math.min(builder.maxStreamsPerConnection + 1L, 512)
                * (builder.connectionsPerEndpoint + 1L) * (long)builder.endpoints.size();
        targetX10 = new AtomicLong(10 * maxInflight); // 10x the actual value to allow for smaller updates.
    }

    @Override
    public void sent(long inflight, CompletableFuture<HttpResponse> vessel) { }

    @Override
    public void success() {
        targetX10.incrementAndGet();
    }

    @Override
    public void throttled(long inflight) {
        targetX10.set(max(inflight * 5, minInflight * 10));
    }

    @Override
    public long targetInflight() {
        return min(maxInflight, targetX10.get() / 10);
    }

}
