// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

import java.io.IOException;
import java.util.List;

/**
 * SearchInvoker encapsulates an allocated connection for running a single search query.
 * The invocation object can be stateful and should not be reused.
 *
 * @author ollivir
 */
public abstract class SearchInvoker extends CloseableInvoker {
    /**
     * Retrieve the hits for the given {@link Query}. The invoker may return more than one result, in which case the caller is responsible
     * for merging the results. If multiple results are returned and the search query had a hit offset other than zero, that offset is
     * set to zero and the number of requested hits is adjusted accordingly.
     */
    public List<Result> search(Query query, QueryPacket queryPacket, CacheKey cacheKey) throws IOException {
        sendSearchRequest(query, queryPacket);
        return getSearchResults(cacheKey);
    }

    protected abstract void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException;

    protected abstract List<Result> getSearchResults(CacheKey cacheKey) throws IOException;
}
