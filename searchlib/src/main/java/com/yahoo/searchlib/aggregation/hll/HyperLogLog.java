// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

/**
 * Contain constants relevant for HyperLogLog classes.
 *
 * @author bjorncs
 */
public interface HyperLogLog {
    /**
     * Default HLL precision.
     */
    int DEFAULT_PRECISION = 10;
    /**
     * Threshold to convert sparse sketch to normal sketch.
     */
    int SPARSE_SKETCH_CONVERSION_THRESHOLD = 256;
}
