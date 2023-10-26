// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

class LinearNormalizer extends Normalizer {

    LinearNormalizer(int maxSize) {
        super(maxSize);
    }

    void normalize() {
        double min = Float.MAX_VALUE;
        double max = -Float.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            double val = data[i];
            if (val < Float.MAX_VALUE && val > -Float.MAX_VALUE) {
                min = Math.min(min, data[i]);
                max = Math.max(max, data[i]);
            }
        }
        double scale = 0.0;
        double midpoint = 0.0;
        if (max > min) {
            scale = 1.0 / (max - min);
            midpoint = (min + max) * 0.5;
        }
        for (int i = 0; i < size; i++) {
            double old = data[i];
            data[i] = 0.5 + scale * (old - midpoint);
        }
    }

    String normalizing() { return "linear"; }
}
