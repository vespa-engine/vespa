// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.collections;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * Class holding the result of a partition-by-predicate operation.
 **/
public class PredicateSplit<E> {
    public final List<E> falseValues; /// list of values where the predicate returned false
    public final List<E> trueValues;  /// list of values where the predicate returned true

    private PredicateSplit() {
	falseValues = new ArrayList<E>();
	trueValues = new ArrayList<E>();
    }

    /**
     * Perform a partition-by-predicate operation.
     * Each value in the input is tested by the predicate and
     * added to either the falseValues list or the trueValues list.
     * @param collection The input collection.
     * @param predicate A test for selecting the target list.
     * @return Two lists bundled in an object.
     **/
    public static <V> PredicateSplit<V> partition(Iterable<V> collection, Predicate<? super V> predicate)
    {
        PredicateSplit<V> r = new PredicateSplit<V>();
        for (V value : collection) {
            if (predicate.test(value)) {
                r.trueValues.add(value);
            } else {
                r.falseValues.add(value);
            }
        }
        return r;
    }
}
