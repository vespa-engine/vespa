// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

/**
 * Tuning of dispatching to content nodes, see the
 * <a href="https://docs.vespa.ai/en/reference/services-content.html#dispatch-tuning">dispatch tuning documentation</a>.
 *
 * @author Simon Thoresen Hult
 */
public class DispatchTuning {

    public static final DispatchTuning empty = new DispatchTuning.Builder().build();

    public enum DispatchPolicy {
        ROUNDROBIN,
        LATENCY_AMORTIZED_OVER_REQUESTS,
        LATENCY_AMORTIZED_OVER_TIME,
        BEST_OF_RANDOM_2,
        ADAPTIVE
    }

    private final Integer maxHitsPerPartition;
    private final DispatchPolicy dispatchPolicy;
    private final Double minActiveDocsCoverage;

    public Double getTopkProbability() {
        return topkProbability;
    }

    private final Double topkProbability;

    private DispatchTuning(Builder builder) {
        maxHitsPerPartition = builder.maxHitsPerPartition;
        dispatchPolicy = builder.dispatchPolicy;
        minActiveDocsCoverage = builder.minActiveDocsCoverage;
        topkProbability = builder.topKProbability;
    }

    /** Returns the max number of hits to fetch from each partition, or null to fetch all */
    public Integer getMaxHitsPerPartition() { return maxHitsPerPartition; }

    /** Returns the policy used to select which group to dispatch a query to */
    public DispatchPolicy getDispatchPolicy() { return dispatchPolicy; }

    /** Returns the percentage of documents which must be available in a group for that group to receive queries */
    public Double getMinActiveDocsCoverage() { return minActiveDocsCoverage; }

    public static class Builder {

        private Integer maxHitsPerPartition;
        private DispatchPolicy dispatchPolicy;
        private Double minActiveDocsCoverage;
        private Double topKProbability;

        public DispatchTuning build() {
            return new DispatchTuning(this);
        }

        public Builder setMaxHitsPerPartition(Integer maxHitsPerPartition) {
            this.maxHitsPerPartition = maxHitsPerPartition;
            return this;
        }
        public Builder setTopKProbability(Double topKProbability) {
            this.topKProbability = topKProbability;
            return this;
        }
        public Builder setDispatchPolicy(String policy) {
            if (policy != null)
                dispatchPolicy = toDispatchPolicy(policy);
            return this;
        }

        public static DispatchPolicy toDispatchPolicy(String policy) {
            switch (policy.toLowerCase()) {
                case "adaptive": case "random": return DispatchPolicy.ADAPTIVE; // TODO: Deprecate 'random' on Vespa 9
                case "round-robin": return DispatchPolicy.ROUNDROBIN;
                case "latency-amortized-over-requests" : return DispatchPolicy.LATENCY_AMORTIZED_OVER_REQUESTS;
                case "latency-amortized-over-time" : return DispatchPolicy.LATENCY_AMORTIZED_OVER_TIME;
                case "best-of-random-2" : return DispatchPolicy.BEST_OF_RANDOM_2;
                default: throw new IllegalArgumentException("Unknown dispatch policy '" + policy + "'");
            }
        }

        public Builder setMinActiveDocsCoverage(Double minCoverage) {
            this.minActiveDocsCoverage = minCoverage;
            return this;
        }
    }

}
