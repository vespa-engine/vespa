// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.search.Query;
import com.yahoo.search.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * InterleavedSearchInvoker uses multiple {@link SearchInvoker} objects to interface with content
 * nodes in parallel. Operationally it first sends requests to all contained invokers and then
 * collects the results. The user of this class is responsible for merging the results if needed.
 *
 * @author ollivir
 */
public class InterleavedSearchInvoker extends SearchInvoker {
    private final Collection<SearchInvoker> invokers;

    public InterleavedSearchInvoker(Map<Integer, SearchInvoker> invokers) {
        this.invokers = new ArrayList<>(invokers.values());
    }

    /**
     * Sends search queries to the contained {@link SearchInvoker} sub-invokers. If the search
     * query has an offset other than zero, it will be reset to zero and the expected hit amount
     * will be adjusted accordingly.
     */
    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        for (SearchInvoker invoker : invokers) {
            Query subquery = query.clone();

            subquery.setHits(subquery.getHits() + subquery.getOffset());
            subquery.setOffset(0);
            invoker.sendSearchRequest(subquery, null);
        }
    }

    @Override
    protected List<Result> getSearchResults(CacheKey cacheKey) throws IOException {
        List<Result> results = new ArrayList<>();

        for (SearchInvoker invoker : invokers) {
            results.addAll(invoker.getSearchResults(cacheKey));
        }
        return results;
    }

    @Override
    protected void release() {
        if (!invokers.isEmpty()) {
            invokers.forEach(SearchInvoker::close);
            invokers.clear();
        }
    }
}
