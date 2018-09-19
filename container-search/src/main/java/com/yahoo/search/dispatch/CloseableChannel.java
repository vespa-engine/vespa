// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

import java.io.Closeable;
import java.io.IOException;

/**
 * CloseableChannel is an interface for running a search query and getting document summaries against some
 * content node, node group or dispatcher while abstracting the specifics of the invocation target.
 *
 * @author ollivir
 */
public abstract class CloseableChannel implements Closeable {
    /** Retrieve the hits for the given {@link Query} */
    public abstract Result search(Query query, QueryPacket queryPacket, CacheKey cacheKey) throws IOException;

    /** Retrieve document summaries for the unfilled hits in the given {@link Result} */
    public abstract void partialFill(Result result, String summaryClass);

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
