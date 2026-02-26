// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.tensor.TensorType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Information about a rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final String      name;
    private final boolean     hasSummaryFeatures;
    private final boolean     hasRankFeatures;
    private final boolean     useSignificanceModel;
    private final OptionalInt keepRankCount;
    private final OptionalInt totalKeepRankCount;
    private final Map<String, InputType> inputs;
    private final SecondPhase secondPhase;

    // Assigned when this is added to a schema
    private Schema schema = null;

    private RankProfile(Builder builder) {
        this.name = builder.name;
        this.hasSummaryFeatures = builder.hasSummaryFeatures;
        this.hasRankFeatures = builder.hasRankFeatures;
        this.useSignificanceModel = builder.useSignificanceModel;
        this.inputs = Collections.unmodifiableMap(builder.inputs);
        this.keepRankCount = builder.keepRankCount;
        this.totalKeepRankCount = builder.totalKeepRankCount;
        this.secondPhase = builder.secondPhase;
    }

    public String name() { return name; }

    /** Returns the schema owning this, or null if this is not added to a schema */
    public Schema schema() { return schema; }

    void setSchema(Schema schema) {
        if ( this.schema != null)
            throw new IllegalStateException("Cannot add rank profile '" + name + "' to schema '" + schema.name() +
                                            "' as it is already added to schema '" + this.schema.name() + "'");
        this.schema = schema;
    }

    /** Returns true if this rank profile has summary features. */
    public boolean hasSummaryFeatures() { return hasSummaryFeatures; }

    /** Returns true if this rank profile has rank features. */
    public boolean hasRankFeatures() { return hasRankFeatures; }

    /** Returns true if this rank profile should use significance models. */
    public boolean useSignificanceModel() { return useSignificanceModel; }

    /** Returns the inputs explicitly declared in this rank profile. */
    public Map<String, InputType> inputs() { return inputs; }

    /** Returns the number of hits to keep rank data for in first phase on each node, or empty to use the default. */
    public OptionalInt keepRankCount() { return keepRankCount; }

    /** Returns the number of hits to keep rank data for in first phase across all nodes, or empty to use keepRankCount. */
    public OptionalInt totalKeepRankCount() { return totalKeepRankCount; }

    /** Returns information about the second phase reranking of this. */
    public SecondPhase secondPhase() { return secondPhase; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof RankProfile other)) return false;
        if ( ! other.name.equals(this.name)) return false;
        if ( other.hasSummaryFeatures != this.hasSummaryFeatures) return false;
        if ( other.hasRankFeatures != this.hasRankFeatures) return false;
        if ( other.useSignificanceModel != this.useSignificanceModel) return false;
        if ( ! other.inputs.equals(this.inputs)) return false;
        if ( ! other.keepRankCount.equals(this.keepRankCount)) return false;
        if ( ! other.totalKeepRankCount.equals(this.totalKeepRankCount)) return false;
        if ( ! other.secondPhase.equals(this.secondPhase)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, hasSummaryFeatures, hasRankFeatures, useSignificanceModel, inputs, keepRankCount, totalKeepRankCount, secondPhase);
    }

    @Override
    public String toString() {
        return "rank profile '" + name + "'" + (schema == null ? "" : " in " + schema);
    }

    public record InputType(TensorType tensorType, boolean declaredString) {

        @Override
        public String toString() {
            return declaredString ? "string" : tensorType.toString();
        }

        public static InputType fromSpec(String spec) {
            if ("string".equals(spec)) {
                return new InputType(TensorType.empty, true);
            }
            if ("double".equals(spec)) {
                return new InputType(TensorType.empty, false);
            }
            return new InputType(TensorType.fromSpec(spec), false);
        }

    }

    public static class Builder {

        private final String name;
        private boolean hasSummaryFeatures = true;
        private boolean hasRankFeatures = true;
        private boolean useSignificanceModel = false;
        private final Map<String, InputType> inputs = new LinkedHashMap<>();
        private OptionalInt keepRankCount = OptionalInt.empty();
        private OptionalInt totalKeepRankCount = OptionalInt.empty();
        private SecondPhase secondPhase = new SecondPhase.Builder().build();

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

        public Builder addInput(String name, InputType type) {
            inputs.put(name, type);
            return this;
        }

        public Builder setUseSignificanceModel(boolean use) { this.useSignificanceModel = use; return this; }

        public Builder setKeepRankCount(int keepRankCount) {
            this.keepRankCount = OptionalInt.of(keepRankCount);
            return this;
        }

        public Builder setTotalKeepRankCount(int totalKeepRankCount) {
            this.totalKeepRankCount = OptionalInt.of(totalKeepRankCount);
            return this;
        }

        public Builder setSecondPhase(SecondPhase secondPhase) {
            this.secondPhase = Objects.requireNonNull(secondPhase);
            return this;
        }

        public RankProfile build() {
            return new RankProfile(this);
        }

    }

}
