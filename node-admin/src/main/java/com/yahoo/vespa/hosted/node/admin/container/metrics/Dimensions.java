// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * @author freva
 */
public class Dimensions {

    public static final Dimensions NONE = new Dimensions(Map.of());

    private final Map<String, String> dimensionsMap;

    public Dimensions(Map<String, String> dimensionsMap) {
        this.dimensionsMap = Map.copyOf(dimensionsMap);
    }

    public Map<String, String> asMap() {
        return dimensionsMap;
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
            return new Dimensions(dimensionsMap);
        }
    }
}
