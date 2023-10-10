// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.aggregation.hll;

import com.yahoo.vespa.objects.Identifiable;

/**
 * Represents a sketch. All sketch types must provide a merge method.
 *
 * @param <T> The type of the sub-class.
 */
public abstract class Sketch<T extends Sketch<T>> extends Identifiable {
    /**
     * Merge content of other into 'this'.
     *
     * @param other Other sketch
     */
    public abstract void merge(T other);

    /**
     * Aggregates the hash values.
     *
     * @param hashValues Provides an iterator for the hash values
     */
    public abstract void aggregate(Iterable<Integer> hashValues);

     /**
     * Aggregates the hash value.
     *
     * @param hash Hash value.
     */
    public abstract void aggregate(int hash);
}
