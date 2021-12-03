// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import com.yahoo.api.annotations.Beta;

/**
 * All information needed for creating any extra data structures associated with
 * a single metric, outside of its basic type.
 *
 * @author steinar
 */
@Beta
public final class MetricSettings {

    /**
     * A builder for the immutable MetricSettings instances.
     */
    @Beta
    public static final class Builder {
        private boolean histogram = false;

        /**
         * Create a new builder for a MetricSettings instance with default
         * settings.
         */
        public Builder() {
        }

        /**
         * Set whether a resulting metric should have a histogram. Default is
         * false.
         *
         * @param histogram
         *            whether to generate a histogram
         * @return this, to facilitate chaining
         */
        public Builder histogram(boolean histogram) {
            this.histogram = histogram;
            return this;
        }

        /**
         * Build a fresh MetricSettings instance.
         *
         * @return a MetricSettings instance containing the values set in this
         *         builder
         */
        public MetricSettings build() {
            return new MetricSettings(histogram);
        }
    }

    private final int significantDigits; // could have been static, but would
                                         // just introduce bugs when we must
                                         // expose this setting
    private final boolean histogram;

    private MetricSettings(boolean histogram) {
        this.histogram = histogram;
        this.significantDigits = 2;
    }

    int getSignificantdigits() {
        return significantDigits;
    }

    boolean isHistogram() {
        return histogram;
    }
}
