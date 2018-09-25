// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * CloseableChannel is an interface for running a search query and getting document summaries against some content node, node group or
 * dispatcher while abstracting the specifics of the invocation target. ClosebleChannel objects are stateful and should not be reused.
 *
 * @author ollivir
 */
public abstract class CloseableChannel implements Closeable {
    /** Retrieve the hits for the given {@link Query}. The channel may return more than one result, in
     * which case the caller is responsible for merging the results. If multiple results are returned
     * and the search query had a hit offset other than zero, that offset will be set to zero and the
     * number of requested hits will be adjusted accordingly. */
    public List<Result> search(Query query, QueryPacket queryPacket, CacheKey cacheKey) throws IOException {
        sendSearchRequest(query, queryPacket);
        return getSearchResults(cacheKey);
    }

    protected abstract void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException;

    protected abstract List<Result> getSearchResults(CacheKey cacheKey) throws IOException;

    /** Retrieve document summaries for the unfilled hits in the given {@link Result} */
    public void partialFill(Result result, String summaryClass) {
        sendPartialFillRequest(result, summaryClass);
        getPartialFillResults(result, summaryClass);
    }

    protected abstract void getPartialFillResults(Result result, String summaryClass);

    protected abstract void sendPartialFillRequest(Result result, String summaryClass);

    protected abstract void closeChannel();

    private Runnable teardown = null;

    public void teardown(Runnable teardown) {
        this.teardown = teardown;
    }

    @Override
    public final void close() {
        if (teardown != null) {
            teardown.run();
            teardown = null;
        }
        closeChannel();
    }
}
