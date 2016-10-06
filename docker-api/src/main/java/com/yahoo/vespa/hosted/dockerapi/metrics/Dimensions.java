// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi.metrics;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author valerijf
 */
public class Dimensions {
    public final Map<String, Object> dimensionsMap;

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

    public static class Builder {
        private final Map<String, Object> dimensionsMap = new HashMap<>();

        public Dimensions.Builder add(String dimensionName, Object dimensionValue) {
            dimensionsMap.put(dimensionName, dimensionValue);
            return this;
        }

        public Dimensions build() {
            return new Dimensions(Collections.unmodifiableMap(dimensionsMap));
        }
    }
}
