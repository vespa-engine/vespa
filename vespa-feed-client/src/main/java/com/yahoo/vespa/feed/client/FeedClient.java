// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;

/**
 * @author bjorncs
 * @author jonmv
 */
public interface FeedClient extends Closeable {

    CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params);
    CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params);
    CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params);

}
