// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

record PreparedInput(String name, Tensor value) {

    static List<PreparedInput> findFromQuery(Query query, Collection<String> queryFeatures) {
        List<PreparedInput> result = new ArrayList<>();
        var ranking = query.getRanking();
        var rankFeatures = ranking.getFeatures();
        var rankProps = ranking.getProperties().asMap();
        for (String queryFeatureName : queryFeatures) {
            String needed = "query(" + queryFeatureName + ")";
            // searchers are recommended to place query features here:
            var feature = rankFeatures.getTensor(queryFeatureName);
            if (feature.isPresent()) {
                result.add(new PreparedInput(needed, feature.get()));
            } else {
                // but other ways of setting query features end up in the properties:
                var objList = rankProps.get(queryFeatureName);
                if (objList != null && objList.size() == 1 && objList.get(0) instanceof Tensor t) {
                    result.add(new PreparedInput(needed, t));
                } else {
                    throw new IllegalArgumentException("missing query feature: " + queryFeatureName);
                }
            }
        }
        return result;
    }

}
