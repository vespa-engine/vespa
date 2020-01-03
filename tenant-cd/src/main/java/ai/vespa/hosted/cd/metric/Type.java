// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.cd.metric;

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
