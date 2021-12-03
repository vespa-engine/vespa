// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.ranking.features;

import com.yahoo.api.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Collections;
import java.util.Map;

/**
 * A set of (immutable) computed features
 *
 * @author bratseth
 */
@Beta
public class Features {

    private Map<String, Value> features;

    /** Creates a set of features by assigning ownership of map of features to this */
    Features(Map<String, Value> features) {
        this.features = Collections.unmodifiableMap(features);
    }

    /** Returns the Value of a feature, or null if it is not present in this */
    public Value get(String featureName) {
        return features.get(featureName);
    }

}
