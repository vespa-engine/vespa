// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Determines the number of requests to have inflight at any point.
 *
 * @author jonmv
 */
interface Throttler {

    /**
     * A request was just sent with {@code vessel}, with {@code inflight} total in flight.
     */
    void sent(long inflight, CompletableFuture<HttpResponse> vessel);

    /**
     * A successful response was obtained.
     */
    void success();

    /**
     * A throttle signal was obtained from the server.
     */
    void throttled(long inflight);

    /**
     * The target inflight operations right now.
     */
    long targetInflight();

}
