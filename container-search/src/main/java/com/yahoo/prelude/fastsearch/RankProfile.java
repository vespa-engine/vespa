// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.tensor.TensorType;

import java.util.Map;

/**
 * Information about a rank profile
 *
 * @author bratseth
 */
class RankProfile {

    private final String name;
    private final boolean hasSummaryFeatures;
    private final boolean hasRankFeatures;
    private final Map<String, TensorType> inputs;

    public RankProfile(String name,
                       boolean hasSummaryFeatures,
                       boolean hasRankFeatures,
                       Map<String, TensorType> inputs) {
        this.name = name;
        this.hasSummaryFeatures = hasSummaryFeatures;
        this.hasRankFeatures = hasRankFeatures;
        this.inputs = Map.copyOf(inputs);
    }

    public String getName() { return name; }

    /** Returns true if this rank profile has summary features. */
    public boolean hasSummaryFeatures() { return hasSummaryFeatures; }

    /** Returns true if this rank profile has rank features. */
    public boolean hasRankFeatures() { return hasRankFeatures; }

    /** Returns the inputs explicitly declared in this rank profile. */
    public Map<String, TensorType> inputs() { return inputs; }

}
