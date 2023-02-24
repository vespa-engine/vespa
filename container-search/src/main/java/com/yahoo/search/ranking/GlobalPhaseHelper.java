// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.Model;
import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.tensor.Tensor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.function.Supplier;

public class GlobalPhaseHelper {

    private static final Logger logger = Logger.getLogger(GlobalPhaseHelper.class.getName());
    private final RankingExpressionEvaluatorFactory factory;
    private final Set<String> skipProcessing = new HashSet<>();
    private final Map<String, Supplier<FunctionEvaluator>> scorers = new HashMap<>();

    @Inject
    public GlobalPhaseHelper(RankingExpressionEvaluatorFactory factory) {
        this.factory = factory;
        logger.info("using factory: " + factory);
    }

    public void process(Query query, Result result, String schema) {
        var functionEvaluatorSource = underlying(query, schema);
        if (functionEvaluatorSource == null) {
            return;
        }
        var prepared = findFromQuery(query, functionEvaluatorSource.get().function().arguments());
        Supplier<Evaluator> supplier = () -> {
            var evaluator = functionEvaluatorSource.get();
            var simple = new SimpleEvaluator(evaluator);
            for (var entry : prepared) {
                simple.bind(entry.name(), entry.value());
            }
            return simple;
        };
        // TODO need to get rerank-count somehow
        int rerank = 7;
        rerankHits(query, result, new HitRescorer(supplier), rerank);
    }

    record NameAndValue(String name, Tensor value) { }

    /* do this only once per query: */
    List<NameAndValue> findFromQuery(Query query, List<String> needInputs) {
        List<NameAndValue> result = new ArrayList<>();
        var ranking = query.getRanking();
        var rankFeatures = ranking.getFeatures();
        var rankProps = ranking.getProperties().asMap();
        for (String needed : needInputs) {
            var optRef = com.yahoo.searchlib.rankingexpression.Reference.simple(needed);
            if (optRef.isEmpty()) continue;
            var ref = optRef.get();
            if (ref.name().equals("constant")) {
                // XXX in theory, we should be able to avoid this
                result.add(new NameAndValue(needed, null));
                continue;
            }
            if (ref.isSimple() && ref.name().equals("query")) {
                String queryFeatureName = ref.simpleArgument().get();
                // searchers are recommended to place query features here:
                var feature = rankFeatures.getTensor(queryFeatureName);
                if (feature.isPresent()) {
                    result.add(new NameAndValue(needed, feature.get()));
                } else {
                    // but other ways of setting query features end up in the properties:
                    var objList = rankProps.get(queryFeatureName);
                    if (objList != null && objList.size() == 1 && objList.get(0) instanceof Tensor t) {
                        result.add(new NameAndValue(needed, t));
                    }
                }
            }
        }
        return result;
    }

    void rerankHits(Query query, Result result, HitRescorer hitRescorer, int rerank) {
        double worstRerankedScore = Double.MAX_VALUE;
        double worstRerankedOldScore = Double.MAX_VALUE;
        // TODO consider doing recursive iteration instead of deepIterator
        for (var iterator = result.hits().deepIterator(); iterator.hasNext();) {
            Hit hit = iterator.next();
            if (hit.isMeta() || hit instanceof HitGroup) {
                continue;
            }
            // what about hits inside grouping results?
            if (rerank > 0) {
                double oldScore = hit.getRelevance().getScore();
                boolean didRerank = hitRescorer.rescoreHit(hit);
                if (didRerank) {
                    double newScore = hit.getRelevance().getScore();
                    if (oldScore < worstRerankedOldScore) worstRerankedOldScore = oldScore;
                    if (newScore < worstRerankedScore) worstRerankedScore = newScore;
                    --rerank;
                } else {
                    // failed to rescore this hit, what should we do?
                    hit.setRelevance(-Double.MAX_VALUE);
                }
            } else {
                // too low quality
                if (worstRerankedOldScore > worstRerankedScore) {
                    double penalty = worstRerankedOldScore - worstRerankedScore;
                    double oldScore = hit.getRelevance().getScore();
                    hit.setRelevance(oldScore - penalty);
                }
            }
        }
        result.hits().sort();
    }

    private Supplier<FunctionEvaluator> underlying(Query query, String schema) {
        String rankProfile = query.getRanking().getProfile();
        String key = schema + " with rank profile " + rankProfile;
        if (skipProcessing.contains(key)) {
            return null;
        }
        Supplier<FunctionEvaluator> supplier = scorers.get(key);
        if (supplier != null) {
            return supplier;
        }
        try {
            var proxy = factory.proxyForSchema(schema);
            var model = proxy.modelForRankProfile(rankProfile);
            supplier = () -> model.evaluatorOf("globalphase");
            if (supplier.get() == null) {
                supplier = null;
            }
        } catch (IllegalArgumentException e) {
            logger.info("no global-phase for " + key + " because: " + e.getMessage());
            supplier = null;
        }
        if (supplier == null) {
            skipProcessing.add(key);
        } else {
            scorers.put(key, supplier);
        }
        return supplier;
    }

}
