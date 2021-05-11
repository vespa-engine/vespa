// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import java.io.Closeable;
import java.util.concurrent.Future;

/**
 * @author bjorncs
 */
public interface FeedClient extends Closeable {
    Future<Result> put(String documentId, String documentJson, OperationParameters params, ResultCallback callback);
    Future<Result> remove(String documentId, OperationParameters params, ResultCallback callback);
    Future<Result> update(String documentId, String documentJson, OperationParameters params, ResultCallback callback);

    interface ResultCallback {
        void completed(Result result);
        void failed(FeedException e);
    }

}
