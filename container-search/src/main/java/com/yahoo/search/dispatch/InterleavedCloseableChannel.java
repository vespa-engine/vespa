// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * InterleavedCloseableChannel uses multiple {@link CloseableChannel} objects to interface with
 * content nodes in parallel. Operationally it first sends requests to all channels and then
 * collects the results. The invoker of this class is responsible for merging the results if
 * needed.
 *
 * @author ollivir
 */
public class InterleavedCloseableChannel extends CloseableChannel {
    private final Map<Integer, CloseableChannel> subchannels;
    private Map<Integer, Result> expectedFillResults = null;

    public InterleavedCloseableChannel(Map<Integer, CloseableChannel> subchannels) {
        this.subchannels = subchannels;
    }

    /** Sends search queries to the contained {@link CloseableChannel} subchannels. If the
     * search query has an offset other than zero, it will be reset to zero and the expected
     * hit amount will be adjusted accordingly. */
    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        for (CloseableChannel subchannel : subchannels.values()) {
            Query subquery = query.clone();

            subquery.setHits(subquery.getHits() + subquery.getOffset());
            subquery.setOffset(0);
            subchannel.sendSearchRequest(subquery, null);
        }
    }

    @Override
    protected List<Result> getSearchResults(CacheKey cacheKey) throws IOException {
        List<Result> results = new ArrayList<>();

        for (CloseableChannel subchannel : subchannels.values()) {
            results.addAll(subchannel.getSearchResults(cacheKey));
        }
        return results;
    }

    @Override
    protected void sendPartialFillRequest(Result result, String summaryClass) {
        expectedFillResults = new HashMap<>();

        for (Iterator<Hit> it = result.hits().deepIterator(); it.hasNext();) {
            Hit hit = it.next();
            if (hit instanceof FastHit) {
                FastHit fhit = (FastHit) hit;
                Result res = expectedFillResults.computeIfAbsent(fhit.getDistributionKey(), dk -> new Result(result.getQuery()));
                res.hits().add(fhit);
            }
        }
        expectedFillResults.forEach((distKey, partialResult) -> {
            CloseableChannel channel = subchannels.get(distKey);
            if (channel != null) {
                channel.sendPartialFillRequest(partialResult, summaryClass);
            }
        });
    }

    @Override
    protected void getPartialFillResults(Result result, String summaryClass) {
        if (expectedFillResults == null) {
            return;
        }
        expectedFillResults.forEach((distKey, partialResult) -> {
            CloseableChannel channel = subchannels.get(distKey);
            if (channel != null) {
                channel.getPartialFillResults(partialResult, summaryClass);
            }
        });
    }

    @Override
    protected void closeChannel() {
        if (!subchannels.isEmpty()) {
            subchannels.values().forEach(CloseableChannel::close);
            subchannels.clear();
        }
    }
}
