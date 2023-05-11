// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.query.Sorting;
import com.yahoo.search.ranking.RankProfilesEvaluator.GlobalPhaseData;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.tensor.Tensor;
import com.yahoo.data.access.helpers.MatchFeatureData;
import com.yahoo.data.access.helpers.MatchFeatureFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    public Optional<ErrorMessage> validateNoSorting(Query query, String schema) {
        var data = globalPhaseDataFor(query, schema).orElse(null);
        if (data == null) return Optional.empty();
        var sorting = query.getRanking().getSorting();
        if (sorting == null || sorting.fieldOrders() == null) return Optional.empty();
        for (var fieldOrder : sorting.fieldOrders()) {
            if (!fieldOrder.getSorter().getName().equals("[rank]")
                    || fieldOrder.getSortOrder() != Sorting.Order.DESCENDING) {
                return Optional.of(ErrorMessage.createIllegalQuery("Sorting is not supported with global phase"));
            }
        }
        return Optional.empty();
    }

    public void rerankHits(Query query, Result result, String schema) {
        var data = globalPhaseDataFor(query, schema).orElse(null);
        if (data == null) return;
        var functionEvaluatorSource = data.functionEvaluatorSource();
        var prepared = findFromQuery(query, data.needInputs());
        Supplier<Evaluator> supplier = () -> {
            var evaluator = functionEvaluatorSource.get();
            var simple = new SimpleEvaluator(evaluator);
            for (var entry : prepared) {
                simple.bind(entry.name(), entry.value());
            }
            return simple;
        };
        int rerankCount = data.rerankCount();
        if (rerankCount < 0)
            rerankCount = 100;
        ResultReranker.rerankHits(result, new HitRescorer(supplier), rerankCount);
        hideImplicitMatchFeatures(result, data.matchFeaturesToHide());
    }

    private void hideImplicitMatchFeatures(Result result, Collection<String> namesToHide) {
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
                        hit.setField("matchfeatures", newValue);
                    }
                }
            }
        }
    }

    private Optional<GlobalPhaseData> globalPhaseDataFor(Query query, String schema) {
        return factory.evaluatorForSchema(schema)
                .flatMap(evaluator -> evaluator.getGlobalPhaseData(query.getRanking().getProfile()));
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

}
