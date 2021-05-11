// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.feed.client;

import java.util.concurrent.Future;

/**
 * @author bjorncs
 */
class HttpFeedClient implements FeedClient {

    HttpFeedClient(FeedClientBuilder builder) {

    }

    @Override
    public Future<Result> put(String documentId, String documentJson, OperationParameters params, ResultCallback callback) {
        return null;
    }

    @Override
    public Future<Result> remove(String documentId, OperationParameters params, ResultCallback callback) {
        return null;
    }

    @Override
    public Future<Result> update(String documentId, String documentJson, OperationParameters params, ResultCallback callback) {
        return null;
    }
}
