// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container.metrics;

import java.util.HashMap;
import java.util.Map;

/**
 * @author freva
 */
public record Dimensions(Map<String, String> dimensionsMap) {

    public static final Dimensions NONE = new Dimensions(Map.of());

    public Dimensions(Map<String, String> dimensionsMap) {
        this.dimensionsMap = Map.copyOf(dimensionsMap);
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
