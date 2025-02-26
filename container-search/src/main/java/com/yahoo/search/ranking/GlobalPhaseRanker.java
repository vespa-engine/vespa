// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.tensor.Tensor;
import com.yahoo.data.access.helpers.MatchFeatureData;
import com.yahoo.data.access.helpers.MatchFeatureFilter;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class GlobalPhaseRanker {

    private static final Logger logger = Logger.getLogger(GlobalPhaseRanker.class.getName());
    private final RankProfilesEvaluatorFactory factory;

    @Inject
    public GlobalPhaseRanker(RankProfilesEvaluatorFactory factory) {
        this.factory = factory;
        logger.fine(() -> "Using factory: " + factory);
    }

    public int getRerankCount(Query query, String schema) {
        var setup = globalPhaseSetupFor(query, schema).orElse(null);
        return resolveRerankCount(setup, query);
    }

    public Optional<ErrorMessage> validateNoSorting(Query query, String schema) {
        var setup = globalPhaseSetupFor(query, schema).orElse(null);
        if (setup == null) return Optional.empty();
        var sorting = query.getRanking().getSorting();
        if (sorting == null) return Optional.empty();
        if (sorting.fieldOrders() == null || sorting.fieldOrders().isEmpty()) {
            return Optional.of(ErrorMessage.createIllegalQuery("Invalid sorting for global phase"));
        }
        for (var fieldOrder : sorting.fieldOrders()) {
            if (!fieldOrder.getSorter().getName().equals("[rank]")
                    || fieldOrder.getSortOrder() != Sorting.Order.DESCENDING) {
                return Optional.of(ErrorMessage.createIllegalQuery("Sorting is not supported with global phase"));
            }
        }
        return Optional.empty();
    }

    static void rerankHitsImpl(GlobalPhaseSetup setup, Query query, Result result) {
        var mainSpec = setup.globalPhaseEvalSpec;
        var mainSrc = withQueryPrep(mainSpec.evalSource(), mainSpec.fromQuery(), setup.defaultValues, query);
        int rerankCount = resolveRerankCount(setup, query);
        double rankScoreDropLimit = resolveRankScoreDropLimit(setup, query);
        var normalizers = new ArrayList<NormalizerContext>();
        for (var nSetup : setup.normalizers) {
            var normSpec = nSetup.inputEvalSpec();
            var normEvalSrc = withQueryPrep(normSpec.evalSource(), normSpec.fromQuery(), setup.defaultValues, query);
            normalizers.add(new NormalizerContext(nSetup.name(), nSetup.supplier().get(), normEvalSrc, normSpec.fromMF()));
        }
        var rescorer = new HitRescorer(mainSrc, mainSpec.fromMF(), normalizers);
        var reranker = new ResultReranker(rescorer, rerankCount);
        reranker.rerankHits(result);
        removeBelowRankScoreDropLimit(result, rankScoreDropLimit);
        hideImplicitMatchFeatures(result, setup.matchFeaturesToHide);
    }

    private static void removeBelowRankScoreDropLimit(Result result, double rankScoreDropLimit) {
        // If this is the Default value we don't need to iterate
        if (rankScoreDropLimit == -Double.MAX_VALUE) return;

        // Collect the hits whose relevance is below rankScoreDropLimit
        List<Hit> toBeRemoved = result.hits().asList().stream()
                .filter(hit -> hit.getRelevance().getScore() <= rankScoreDropLimit).toList();

        // Remove hits that have too low relevance score
        for (Hit hitToRemove : toBeRemoved) {
            result.hits().remove(hitToRemove.getId());
        }
        // Adjust the totalHitCount
        int removedCount = toBeRemoved.size();
        result.setTotalHitCount(result.getTotalHitCount() - removedCount);
        // TODO: There could be a metric that specifies how many hits were removed
    }

    public void rerankHits(Query query, Result result, String schema) {
        var setup = globalPhaseSetupFor(query, schema);
        if (setup.isPresent()) {
            rerankHitsImpl(setup.get(), query, result);
        }
    }

    static Supplier<Evaluator> withQueryPrep(Supplier<Evaluator> evalSource, List<String> queryFeatures, Map<String, Tensor> defaultValues, Query query) {
        var prepared = PreparedInput.findFromQuery(query, queryFeatures, defaultValues);
        Supplier<Evaluator> supplier = () -> {
            var evaluator = evalSource.get();
            for (var entry : prepared) {
                evaluator.bind(entry.name(), entry.value());
            }
            return evaluator;
        };
        return supplier;
    }

    private static void hideImplicitMatchFeatures(Result result, Collection<String> namesToHide) {
        if (namesToHide.size() == 0) return;
        var filter = new MatchFeatureFilter(namesToHide);
        for (var iterator = result.hits().deepIterator(); iterator.hasNext();) {
            Hit hit = iterator.next();
            if (hit.isMeta() || hit instanceof HitGroup) {
                continue;
            }
            if (hit.getField("matchfeatures") instanceof FeatureData matchFeatures) {
                if (matchFeatures.inspect() instanceof MatchFeatureData.HitValue hitValue) {
                    var newValue = hitValue.subsetFilter(filter);
                    if (newValue.fieldCount() == 0) {
                        hit.removeField("matchfeatures");
                    } else {
                        hit.setField("matchfeatures", new FeatureData(newValue));
                    }
                }
            }
        }
    }

    private Optional<GlobalPhaseSetup> globalPhaseSetupFor(Query query, String schema) {
        return factory.evaluatorForSchema(schema)
                .flatMap(evaluator -> evaluator.getGlobalPhaseSetup(query.getRanking().getProfile()));
    }

    private static int resolveRerankCount(GlobalPhaseSetup setup, Query query) {
        if (setup == null) {
            // there is no global-phase at all (ignore override)
            return 0;
        }
        Integer override = query.getRanking().getGlobalPhase().getRerankCount();
        if (override != null) {
            return override;
        }
        return setup.rerankCount;
    }

    private static double resolveRankScoreDropLimit(GlobalPhaseSetup setup, Query query) {
        if (setup == null) {
            // there is no global-phase at all (ignore override)
            return -Double.MAX_VALUE;
        }
        Double override = query.getRanking().getGlobalPhase().getRankScoreDropLimit();
        if (override != null) {
            return override;
        }
        return setup.rankScoreDropLimit;
    }
}
