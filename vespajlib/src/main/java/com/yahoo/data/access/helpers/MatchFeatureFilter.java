// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.helpers;

import com.yahoo.collections.Hashlet;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.function.Function;

/**
 *  Helper class to remove (filter) some names from a Hashlet
 *  @author arnej
 */
public class MatchFeatureFilter implements Function<Hashlet<String,Integer>, Hashlet<String,Integer>> {

    private final IdentityHashMap<Hashlet<String,Integer>, Hashlet<String,Integer>> mappings = new IdentityHashMap<>();
    private final Collection<String> removeList;

    public MatchFeatureFilter(Collection<String> removeList) {
        this.removeList = removeList;
    }

    Hashlet<String,Integer> filter(Hashlet<String,Integer> input) {
        Hashlet<String,Integer> result = new Hashlet<>();
        result.reserve(input.size());
        for (int i = 0; i < input.size(); i++) {
            String k = input.key(i);
            if (! removeList.contains(k)) {
                Integer v = input.value(i);
                result.put(k, v);
            }
        }
        return result;
    }

    public Hashlet<String,Integer> apply(Hashlet<String,Integer> input) {
        return mappings.computeIfAbsent(input, k -> filter(k));
    }

}
