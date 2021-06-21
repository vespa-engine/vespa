// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Reduces max throughput whenever throttled; increases it slowly whenever successful responses are obtained.
 *
 * @author jonmv
 */
public class StaticThrottler implements FeedClient.Throttler {

    protected final long maxInflight;
    protected final long minInflight;
    private final AtomicLong targetX10;

    public StaticThrottler(FeedClientBuilder builder) {
        this.maxInflight = builder.connectionsPerEndpoint * (long) builder.maxStreamsPerConnection;
        this.minInflight = builder.connectionsPerEndpoint * (long) min(16, builder.maxStreamsPerConnection);
        this.targetX10 = new AtomicLong(10 * maxInflight); // 10x the actual value to allow for smaller updates.
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
