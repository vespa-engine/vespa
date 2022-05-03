// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Information about a rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final String name;
    private final boolean hasSummaryFeatures;
    private final boolean hasRankFeatures;
    private final Map<String, TensorType> inputs;

    private RankProfile(Builder builder) {
        this.name = builder.name;
        this.hasSummaryFeatures = builder.hasSummaryFeatures;
        this.hasRankFeatures = builder.hasRankFeatures;
        this.inputs = Collections.unmodifiableMap(builder.inputs);
    }

    public String name() { return name; }

    /** Returns true if this rank profile has summary features. */
    public boolean hasSummaryFeatures() { return hasSummaryFeatures; }

    /** Returns true if this rank profile has rank features. */
    public boolean hasRankFeatures() { return hasRankFeatures; }

    /** Returns the inputs explicitly declared in this rank profile. */
    public Map<String, TensorType> inputs() { return inputs; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof RankProfile)) return false;
        RankProfile other = (RankProfile)o;
        if ( ! other.name.equals(this.name)) return false;
        if ( other.hasSummaryFeatures != this.hasSummaryFeatures) return false;
        if ( other.hasRankFeatures != this.hasRankFeatures) return false;
        if ( ! other.inputs.equals(this.inputs)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, hasSummaryFeatures, hasRankFeatures, inputs);
    }

    @Override
    public String toString() {
        return "rank profile '" + name + "'";
    }

    public static class Builder {

        private final String name;
        private boolean hasSummaryFeatures = true;
        private boolean hasRankFeatures = true;
        private final Map<String, TensorType> inputs = new LinkedHashMap<>();

        public Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder setHasSummaryFeatures(boolean hasSummaryFeatures) {
            this.hasSummaryFeatures = hasSummaryFeatures;
            return this;
        }

        public Builder setHasRankFeatures(boolean hasRankFeatures) {
            this.hasRankFeatures = hasRankFeatures;
            return this;
        }

        public Builder addInput(String name, TensorType type) {
            inputs.put(name, type);
            return this;
        }

        public RankProfile build() {
            return new RankProfile(this);
        }

    }

}
