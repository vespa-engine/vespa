// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.FetchVector;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class FetchVectorHelper {

    public static Map<String, String> toWire(FetchVector vector) {
        Map<Dimension, String> map = vector.toMap();
        if (map.isEmpty()) return null;
        return map.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().toWire(),
                Map.Entry::getValue));
    }

    public static FetchVector fromWire(Map<String, String> wireMap) {
        if (wireMap == null) return new FetchVector();
        return FetchVector.fromMap(wireMap.entrySet().stream().collect(Collectors.toMap(
                entry -> Dimension.fromWire(entry.getKey()),
                Map.Entry::getValue)));
    }

}
