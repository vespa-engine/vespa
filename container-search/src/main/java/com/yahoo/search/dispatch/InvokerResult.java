// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.Sorting;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Result and a flat, skinny hit list
 *
 * @author baldersheim
 */
public class InvokerResult {

    private final Result result;
    private final List<LeanHit> leanHits;

    public InvokerResult(Result result) {
        this.result = result;
        this.leanHits = List.of();
    }

    public InvokerResult(Query query, int expectedHits) {
        result = new Result(query);
        leanHits = new ArrayList<>(expectedHits);
    }

    public Result getResult() {
        return result;
    }

    public List<LeanHit> getLeanHits() {
        return leanHits;
    }

    void complete() {
        Query query = result.getQuery();
        Sorting sorting = query.getRanking().getSorting();
        for (LeanHit hit : leanHits) {
            FastHit fastHit = new FastHit(hit.getGid(), hit.getRelevance(), hit.getGroup(), hit.getPartId(), hit.getDistributionKey());
            if (hit.hasSortData()) {
                fastHit.setSortData(hit.getSortData(), sorting);
            }
            fastHit.setQuery(query);
            fastHit.setFillable();
            if (hit.hasMatchFeatures()) {
                fastHit.setField("matchfeatures", hit.getMatchFeatures());
                fastHit.setFilled("[f:matchfeatures]");
            }
            fastHit.setCached(false);
            result.hits().add(fastHit);
        }
        if (!leanHits.isEmpty())
            leanHits.clear();
    }

}
