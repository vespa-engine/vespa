// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.container.handler.Timing;
import com.yahoo.container.logging.HitCounts;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Some leftover static methods.
 *
 * @author Steinar Knutsen
 */
public class SearchResponse {

    // Remove (the empty) summary feature field if not requested.
    static void removeEmptySummaryFeatureFields(Result result) {
        // TODO: Move to some searcher in Vespa backend search chains
        if ( ! result.hits().getQuery().getRanking().getListFeatures())
            for (Iterator<Hit> i = result.hits().unorderedIterator(); i.hasNext();)
                i.next().removeField(Hit.RANKFEATURES_FIELD);
    }

    static void trimHits(Result result) {
        if (result.getConcreteHitCount() > result.hits().getQuery().getHits()) {
            result.hits().trim(0, result.hits().getQuery().getHits());
        }
    }

    static Iterator<? extends ErrorMessage> getErrorIterator(ErrorHit h) {
        if (h == null) {
            return new ArrayList<ErrorMessage>(0).iterator();
        } else {
            return h.errorIterator();
        }
    }

    static boolean isSuccess(Result r) {
        if (r.hits().getErrorHit() == null) return true;
        for (Hit hit : r.hits())
            if ( ! hit.isMeta()) return true; // contains data : success
        return false;
    }

    @SuppressWarnings("deprecation")
    public static Timing createTiming(Query query, Result result) {
        return new Timing(result.getElapsedTime().firstFill(),
                          0,
                          result.getElapsedTime().first(),
                          query.getTimeout());
    }

    public static HitCounts createHitCounts(Query query, Result result) {
        return new HitCounts(result.getHitCount(),
                             result.getConcreteHitCount(),
                             result.getTotalHitCount(),
                             query.getHits(),
                             query.getOffset());
    }

}


