// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.HttpResponse;
import ai.vespa.feed.client.OperationStats;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * Allows dispatch of HTTP requests to a remote Vespa cluster.
 *
 * @author jonmv
 */
interface Cluster extends Closeable {

    /** Dispatch the request to the cluster, causing the response vessel to complete at a later time. May not throw! */
    void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel);

    @Override
    default void close() { }

    default OperationStats stats() { throw new UnsupportedOperationException("Benchmarking has been disabled"); }

}
