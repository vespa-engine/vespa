// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

class LinearNormalizer extends Normalizer {

    LinearNormalizer(String name, String input, int maxSize) {
        super(name, input, maxSize);
    }

    void normalize() {
        double min = data[0];
        double max = data[0];
        for (int i = 1; i < size; i++) {
            min = Math.min(min, data[i]);
            max = Math.max(max, data[i]);
        }
        min = Math.max(min, -Float.MAX_VALUE);
        max = Math.min(max, Float.MAX_VALUE);
        double scale = 0.0;
        double midpoint = (min + max) * 0.5;
        if (max > min) {
            scale = 1.0 / (max - min);
        }
        for (int i = 0; i < size; i++) {
            double old = data[i];
            data[i] = 0.5 + scale * (old - midpoint);
        }
    }

    String normalizing() { return "linear"; }
}
