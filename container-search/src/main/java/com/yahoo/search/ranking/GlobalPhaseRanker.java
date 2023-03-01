// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import ai.vespa.models.evaluation.Model;
import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.ranking.RankProfilesEvaluator.GlobalPhaseData;
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

public class GlobalPhaseRanker {

    private static final Logger logger = Logger.getLogger(GlobalPhaseRanker.class.getName());
    private final RankProfilesEvaluatorFactory factory;

    @Inject
    public GlobalPhaseRanker(RankProfilesEvaluatorFactory factory) {
        this.factory = factory;
        logger.info("using factory: " + factory);
    }

    public void process(Query query, Result result, String schema) {
        var proxy = factory.proxyForSchema(schema);
        String rankProfile = query.getRanking().getProfile();
        var optData = proxy.getGlobalPhaseData(rankProfile);
        if (optData.isEmpty())
            return;
        GlobalPhaseData data = optData.get();
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
