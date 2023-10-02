// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import java.util.Arrays;

class ReciprocalRankNormalizer extends Normalizer {

    private final double k;

    ReciprocalRankNormalizer(String name, String input, int maxSize, double k) {
        super(name, input, maxSize);
	this.k = k;
    }

    static record IdxScore(int index, double score) {}

    void normalize() {
        if (size < 1) return;
        IdxScore[] temp = new IdxScore[size];
        for (int i = 0; i < size; i++) {
            temp[i] = new IdxScore(i, data[i]);
        }
        Arrays.sort(temp, (a, b) -> Double.compare(b.score, a.score));
        for (int i = 0; i < size; i++) {
            int idx = temp[i].index;
            double old = data[idx];
            data[idx] = 1.0 / (k + 1.0 + i);
        }
    }

    String normalizing() { return "reciprocal-rank{k:" + k + "}"; }
}
