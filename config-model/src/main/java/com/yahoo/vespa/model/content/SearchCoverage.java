// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.google.common.base.Preconditions;

/**
 * @author Simon Thoresen Hult
 */
public class SearchCoverage {

    private final Double minimum;
    private final Double minWaitAfterCoverageFactor;
    private final Double maxWaitAfterCoverageFactor;

    private SearchCoverage(Builder builder) {
        minimum = builder.minimum;
        minWaitAfterCoverageFactor = builder.minWaitAfterCoverageFactor;
        maxWaitAfterCoverageFactor = builder.maxWaitAfterCoverageFactor;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Double getMinWaitAfterCoverageFactor() {
        return minWaitAfterCoverageFactor;
    }

    public Double getMaxWaitAfterCoverageFactor() {
        return maxWaitAfterCoverageFactor;
    }

    public static class Builder {

        private Double minimum;
        private Double minWaitAfterCoverageFactor;
        private Double maxWaitAfterCoverageFactor;

        public SearchCoverage build() {
            return new SearchCoverage(this);
        }

        public Builder setMinimum(Double value) {
            Preconditions.checkArgument(value == null || (value >= 0 && value <= 1),
                                        "Expected value in range [0, 1], got " + value + ".");
            minimum = value;
            return this;
        }

        public Builder setMinWaitAfterCoverageFactor(Double value) {
            Preconditions.checkArgument(value == null || (value >= 0 && value <= 1),
                                        "Expected value in range [0, 1], got " + value + ".");
            Preconditions.checkArgument(value == null || maxWaitAfterCoverageFactor == null ||
                                        value <= maxWaitAfterCoverageFactor,
                                        "Minimum wait (got %s) must be no larger than maximum wait (was %s).",
                                        value, maxWaitAfterCoverageFactor);
            minWaitAfterCoverageFactor = value;
            return this;
        }

        public Builder setMaxWaitAfterCoverageFactor(Double value) {
            Preconditions.checkArgument(value == null || (value >= 0 && value <= 1),
                                        "Expected value in range [0, 1], got " + value + ".");
            Preconditions.checkArgument(value == null || minWaitAfterCoverageFactor == null ||
                                        value >= minWaitAfterCoverageFactor,
                                        "Maximum wait (got %s) must be no smaller than minimum wait (was %s).",
                                        value, minWaitAfterCoverageFactor);
            maxWaitAfterCoverageFactor = value;
            return this;
        }
    }
}
