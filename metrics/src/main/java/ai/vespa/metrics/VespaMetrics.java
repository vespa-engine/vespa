// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metrics;

/**
 * @author gjoranv
 */
public interface VespaMetrics {

    String baseName();
    Unit unit();
    String description();

    default String descriptionWitUnit() {
        return description() + " (unit: " + unit().shortName() + ")";
    }

    private String withSuffix(Suffix suffix) {
        return baseName() + "." + suffix.suffix();
    }

    // TODO: make the below methods return Metric objects instead of Strings.

    default String ninety_five_percentile() {
        return withSuffix(Suffix.ninety_five_percentile);
    }

    default String ninety_nine_percentile() {
        return withSuffix(Suffix.ninety_nine_percentile);
    }

    default String average() {
        return withSuffix(Suffix.average);
    }

    default String count() {
        return withSuffix(Suffix.count);
    }

    default String last() {
        return withSuffix(Suffix.last);
    }

    default String max() {
        return withSuffix(Suffix.max);
    }

    default String min() {
        return withSuffix(Suffix.min);
    }

    default String rate() {
        return withSuffix(Suffix.rate);
    }

    default String sum() {
        return withSuffix(Suffix.sum);
    }

}
