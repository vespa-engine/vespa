// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A search invoker that will immediately produce an error that occurred during invoker construction.
 * Currently used for invalid searchpath values.
 *
 * @author ollivir
 */
public class SearchErrorInvoker extends SearchInvoker {
    private final String message;
    private Query query;

    public SearchErrorInvoker(String message) {
        this.message = message;
    }

    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        this.query = query;
    }

    @Override
    protected List<Result> getSearchResults(CacheKey cacheKey) throws IOException {
        return Arrays.asList(new Result(query, ErrorMessage.createIllegalQuery(message)));
    }

    @Override
    protected void release() {
        // nothing to do
    }

}
