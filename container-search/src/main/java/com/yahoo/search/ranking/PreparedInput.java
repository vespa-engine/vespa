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

record PreparedInput(String name, Tensor value) {

    static List<PreparedInput> findFromQuery(Query query, Collection<String> queryFeatures, Map<String, Tensor> defaultValues) {
        List<PreparedInput> result = new ArrayList<>();
        var ranking = query.getRanking();
        var rankFeatures = ranking.getFeatures();
        var rankProps = ranking.getProperties();
        for (String queryFeatureName : queryFeatures) {
            String needed = "query(" + queryFeatureName + ")";
            // after prepare() the query tensor ends up here:
            var feature = rankProps.getAsTensor(queryFeatureName);
            if (feature.isEmpty()) {
                // searchers are recommended to place query features here:
                feature = rankFeatures.getTensor(needed);
            }
            if (feature.isEmpty()) {
                var t = defaultValues.get(needed);
                if (t != null) {
                    feature = Optional.of(t);
                }
            }
            if (feature.isEmpty()) {
                throw new IllegalArgumentException("missing query feature: " + queryFeatureName);
            }
            result.add(new PreparedInput(needed, feature.get()));
        }
        return result;
    }

}
