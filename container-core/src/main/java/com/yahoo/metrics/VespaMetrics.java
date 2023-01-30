package com.yahoo.metrics;

/**
 * @author gjoranv
 */
interface VespaMetrics {

    String baseName();
    Unit unit();
    String description();

    default String descriptionWitUnit() {
        return description() + " (unit: " + unit().shortName() + ")";
    }

    private String withSuffix(Suffix suffix) {
        return baseName() + "." + suffix.suffix();
    }

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
