// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

// scale and adjust the score according to the range
// of the original and final score values to avoid that
// a score from the backend is larger than finalScores_low
class RangeAdjuster {
    private double initialScores_high = -Double.MAX_VALUE;
    private double initialScores_low = Double.MAX_VALUE;
    private double finalScores_high = -Double.MAX_VALUE;
    private double finalScores_low = Double.MAX_VALUE;

    boolean rescaleNeeded() {
        return (initialScores_low > finalScores_low
                &&
                initialScores_high >= initialScores_low
                &&
                finalScores_high >= finalScores_low);
    }
    void withInitialScore(double score) {
        if (score < initialScores_low) initialScores_low = score;
        if (score > initialScores_high) initialScores_high = score;
    }
    void withFinalScore(double score) {
        if (score < finalScores_low) finalScores_low = score;
        if (score > finalScores_high) finalScores_high = score;
    }
    private double initialRange() {
        double r = initialScores_high - initialScores_low;
        if (r < 1.0) r = 1.0;
        return r;
    }
    private double finalRange() {
        double r = finalScores_high - finalScores_low;
        if (r < 1.0) r = 1.0;
        return r;
    }
    double scale() { return finalRange() / initialRange(); }
    double bias() { return finalScores_low - initialScores_low * scale(); }
}
