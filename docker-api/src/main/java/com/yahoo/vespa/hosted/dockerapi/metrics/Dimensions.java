// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Each metric reported to secret agent has dimensions.
 *
 * @author valerijf
 */
public class Dimensions {
    final Map<String, Object> dimensionsMap;

    private Dimensions(Map<String, Object> dimensionsMap) {
        this.dimensionsMap = dimensionsMap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Dimensions that = (Dimensions) o;
        return dimensionsMap.equals(that.dimensionsMap);
    }

    @Override
    public int hashCode() {
        return dimensionsMap.hashCode();
    }

    @Override
    public String toString() {
        return dimensionsMap.toString();
    }

    public static class Builder {
        private final Map<String, String> dimensionsMap = new HashMap<>();

        public Dimensions.Builder add(String dimensionName, String dimensionValue) {
            dimensionsMap.put(dimensionName, dimensionValue);
            return this;
        }

        public Dimensions build() {
            return new Dimensions(Collections.unmodifiableMap(new HashMap<>(dimensionsMap)));
        }
    }
}
