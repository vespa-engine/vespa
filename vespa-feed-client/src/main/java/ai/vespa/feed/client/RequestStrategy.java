// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Controls execution of feed operations.
 *
 * @author jonmv
 */
public interface RequestStrategy<T> {

    /** Whether this has failed, and we should stop. */
    boolean hasFailed();

    /** Enqueue the given operation, which is dispatched to a vessel future when ready. */
    CompletableFuture<T> enqueue(DocumentId documentId, Consumer<CompletableFuture<T>> dispatch);

}
