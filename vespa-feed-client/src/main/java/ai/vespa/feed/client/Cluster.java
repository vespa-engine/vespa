// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.io.Closeable;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Allows dispatch to a Vespa cluster.
 */
interface Cluster extends Closeable {

    /** Dispatch the request to the cluster, causing the response vessel to complete at a later time. May not throw. */
    void dispatch(HttpRequest request, CompletableFuture<HttpResponse> vessel);

    @Override
    default void close() { }

    default OperationStats stats() { return new OperationStats(0, Collections.emptyMap(), 0, 0, 0, 0, 0, 0, 0); }

}
