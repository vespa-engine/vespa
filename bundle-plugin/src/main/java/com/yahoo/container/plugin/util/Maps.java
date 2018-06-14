// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Maps {
    public static <K, V> Map<K, V> combine(Map<K, V> left, Map<K, V> right, BiFunction<V, V, V> combiner) {
        Map<K, V> ret = new HashMap<>();
        Set<K> keysRight = new HashSet<>(right.keySet());

        left.forEach((k, v) -> {
            if (keysRight.contains(k)) {
                ret.put(k, combiner.apply(v, right.get(k)));
                keysRight.remove(k);
            } else {
                ret.put(k, v);
            }
        });
        keysRight.forEach(k -> ret.put(k, right.get(k)));

        return ret;
    }
}
