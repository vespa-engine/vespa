// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * Allows dispatch to a Vespa cluster. {@link #dispatch} should be called by a single thread, i.e., it is not thread-safe.
 */
interface Cluster extends Closeable {

    /** Dispatch the request to the cluster, causing the response vessel to complete at a later time. */
    void dispatch(SimpleHttpRequest request, CompletableFuture<SimpleHttpResponse> vessel);

    @Override
    default void close() { }

}
