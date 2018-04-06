// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

/**
 * A interface for unique count estimation algorithms. The goal of this interface is
 * to aid unit testing of {@link HyperLogLogEstimator} users.
 *
 * @author bjorncs
 */
public interface UniqueCountEstimator<T> {

    long estimateCount(T sketch);

}
