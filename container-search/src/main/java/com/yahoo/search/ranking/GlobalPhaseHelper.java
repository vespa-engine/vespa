// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.Model;
import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class GlobalPhaseHelper {

    private final RankingExpressionEvaluatorFactory factory;
    private final Set<String> skipProcessing = new HashSet<>();
    private final Map<String, HitRescorer> hitRescorers = new HashMap<>();

    @Inject
    public GlobalPhaseHelper(RankingExpressionEvaluatorFactory factory) {
        this.factory = factory;
        System.out.println("new GlobalPhaseHelper with factory: " + factory);
    }

    public void process(Query query, Result result, String schema) {
        String rankProfile = query.getRanking().getProfile();
        String key = schema + " with rank profile " + rankProfile;
        if (skipProcessing.contains(key)) {
            return;
        }
        HitRescorer rescorer = hitRescorers.get(key);
        if (rescorer == null) {
            try {
                var proxy = factory.proxyForSchema(schema);
                var model = proxy.modelForRankProfile(rankProfile);
                var evaluator = model.evaluatorOf("globalphase");
                if (evaluator != null) {
                    rescorer = new HitRescorer(() -> model.evaluatorOf("globalphase"));
                }
            } catch(IllegalArgumentException e) {
                System.err.println("no global-phase for " + key);
            }
            if (rescorer == null) {
                skipProcessing.add(key);
                return;
            }
            hitRescorers.put(key, rescorer);
        }
        rerankHits(query, result, rescorer);
    }

    void rerankHits(Query query, Result result, HitRescorer hitRescorer) {
        // TODO need to get rerank-count somehow
        int rerank = 42;
        // TODO consider doing recursive iteration instead of deepIterator
        for (var iterator = result.hits().deepIterator(); iterator.hasNext();) {
            Hit hit = iterator.next();
            if (hit.isMeta() || hit instanceof HitGroup) {
                continue;
            }
            // what about hits inside grouping results?
            if (rerank > 0) {
                boolean didRerank = hitRescorer.rescoreHit(hit);
                if (didRerank) {
                    --rerank;
                } else {
                    // or leave it alone?
                    hit.setRelevance(Double.MIN_VALUE);
                }
            } else {
                // too low quality
                hit.setRelevance(Double.MIN_VALUE);
            }
        }
        result.hits().sort();
    }

}
