package com.yahoo.vespa.tenant.cd.metrics;

/**
 * Known statistic types.
 */
public enum Type {

    /** 95th percentile measurement. */
    percentile95,

    /** 99th percentile measurement. */
    percentile99,

    /** Average over all measurements. */
    average,

    /** Number of measurements. */
    count,

    /** Last measurement. */
    last,

    /** Maximum measurement. */
    max,

    /** Minimum measurement. */
    min,

    /** Number of measurements per second. */
    rate;

}
