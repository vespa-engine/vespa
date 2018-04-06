// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class TuningDispatch {

    private final Integer maxHitsPerPartition;
    public enum DispatchPolicy { ROUNDROBIN, ADAPTIVE};
    private final DispatchPolicy dispatchPolicy;
    private final Boolean useLocalNode;
    private final Double minGroupCoverage;
    private final Double minActiveDocsCoverage;

    private TuningDispatch(Builder builder) {
        maxHitsPerPartition = builder.maxHitsPerPartition;
        dispatchPolicy = builder.dispatchPolicy;
        useLocalNode = builder.useLocalNode;
        minGroupCoverage = builder.minGroupCoverage;
        minActiveDocsCoverage = builder.minActiveDocsCoverage;
    }

    public Integer getMaxHitsPerPartition() {
        return maxHitsPerPartition;
    }

    public DispatchPolicy getDispatchPolicy() { return dispatchPolicy; }
    public Boolean getUseLocalNode() { return useLocalNode; }
    public Double getMinGroupCoverage() { return minGroupCoverage; }
    public Double getMinActiveDocsCoverage() { return minActiveDocsCoverage; }

    public static class Builder {

        private Integer maxHitsPerPartition;
        private DispatchPolicy dispatchPolicy = DispatchPolicy.ADAPTIVE;
        private Boolean useLocalNode;
        private Double minGroupCoverage;
        private Double minActiveDocsCoverage;

        public TuningDispatch build() {
            return new TuningDispatch(this);
        }

        public Builder setMaxHitsPerPartition(Integer maxHitsPerPartition) {
            this.maxHitsPerPartition = maxHitsPerPartition;
            return this;
        }
        public Builder setDispatchPolicy(String policy) {
            if (policy == null) {
            } else if ("random".equals(policy.toLowerCase())) {
                dispatchPolicy = DispatchPolicy.ADAPTIVE;
            } else if ("round-robin".equals(policy.toLowerCase())) {
                dispatchPolicy = DispatchPolicy.ROUNDROBIN;
            } else {
                dispatchPolicy = DispatchPolicy.valueOf(policy.toUpperCase());
            }
            return this;
        }

        public Builder setUseLocalNode(Boolean useLocalNode) {
            this.useLocalNode = useLocalNode;
            return this;
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
