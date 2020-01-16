// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

/**
 * @author Simon Thoresen Hult
 */
public class DispatchTuning {

    private final Integer maxHitsPerPartition;
    public enum DispatchPolicy { ROUNDROBIN, ADAPTIVE};
    private final DispatchPolicy dispatchPolicy;
    private final Double minGroupCoverage;
    private final Double minActiveDocsCoverage;

    private DispatchTuning(Builder builder) {
        maxHitsPerPartition = builder.maxHitsPerPartition;
        dispatchPolicy = builder.dispatchPolicy;
        minGroupCoverage = builder.minGroupCoverage;
        minActiveDocsCoverage = builder.minActiveDocsCoverage;
    }

    public Integer getMaxHitsPerPartition() {
        return maxHitsPerPartition;
    }

    public DispatchPolicy getDispatchPolicy() { return dispatchPolicy; }
    public Double getMinGroupCoverage() { return minGroupCoverage; }
    public Double getMinActiveDocsCoverage() { return minActiveDocsCoverage; }

    public static class Builder {

        private Integer maxHitsPerPartition;
        private DispatchPolicy dispatchPolicy;
        private Double minGroupCoverage;
        private Double minActiveDocsCoverage;

        public DispatchTuning build() {
            return new DispatchTuning(this);
        }

        public Builder setMaxHitsPerPartition(Integer maxHitsPerPartition) {
            this.maxHitsPerPartition = maxHitsPerPartition;
            return this;
        }
        public Builder setDispatchPolicy(String policy) {
            if (policy != null)
                dispatchPolicy = toDispatchPolicy(policy);
            return this;
        }

        private DispatchPolicy toDispatchPolicy(String policy) {
            switch (policy.toLowerCase()) {
                case "adaptive": case "random": return DispatchPolicy.ADAPTIVE; // TODO: Deprecate 'random' on Java 8
                case "round-robin": return DispatchPolicy.ROUNDROBIN;
                default: throw new IllegalArgumentException("Unknown dispatch policy '" + policy + "'");
            }
        }

        public Builder setMinGroupCoverage(Double minGroupCoverage) {
            this.minGroupCoverage = minGroupCoverage;
            return this;
        }

        public Builder setMinActiveDocsCoverage(Double minCoverage) {
            this.minActiveDocsCoverage = minCoverage;
            return this;
        }
    }

}
