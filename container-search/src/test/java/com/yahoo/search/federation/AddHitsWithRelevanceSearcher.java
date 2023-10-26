// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * @author Tony Vaagenes
 */
class AddHitsWithRelevanceSearcher extends Searcher {
    public static final int numHitsAdded = 5;

    private final String chainName;
    private final int relevanceMultiplier;

    public AddHitsWithRelevanceSearcher(String chainName, int rankMultiplier) {
        this.chainName = chainName;
        this.relevanceMultiplier = rankMultiplier;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);
        for (int i = 1; i <= numHitsAdded; ++i) {
            result.hits().add(createHit(i));
        }
        return result;
    }

    private Hit createHit(int i) {
        int relevance = i * relevanceMultiplier;
        return new Hit(chainName + "-" + relevance, relevance);
    }
}
