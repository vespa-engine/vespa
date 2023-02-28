// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

class ResultReranker {

    private static final Logger logger = Logger.getLogger(ResultReranker.class.getName());

    // scale and adjust the score according to the range
    // of the original and final score values to avoid that
    // a score from the backend is larger than finalScores_low
    static class Ranges {
        private double initialScores_high = -Double.MAX_VALUE;
        private double initialScores_low = Double.MAX_VALUE;
        private double finalScores_high = -Double.MAX_VALUE;
        private double finalScores_low = Double.MAX_VALUE;

        boolean valid() {
            return (initialScores_high >= initialScores_low
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

    static void rerankHits(Result result, HitRescorer hitRescorer, int rerankCount) {
        List<Hit> hitsToRescore = new ArrayList<>();
        // consider doing recursive iteration explicitly instead of using deepIterator?
        for (var iterator = result.hits().deepIterator(); iterator.hasNext();) {
            Hit hit = iterator.next();
            if (hit.isMeta() || hit instanceof HitGroup) {
                continue;
            }
            // what about hits inside grouping results?
            // they are inside GroupingListHit, we won't recurse into it; so we won't see them.
            hitsToRescore.add(hit);
        }
        // we can't be 100% certain that hits were sorted according to relevance:
        hitsToRescore.sort(Comparator.naturalOrder());
        var ranges = new Ranges();
        for (var iterator = hitsToRescore.iterator(); rerankCount > 0 && iterator.hasNext(); ) {
            Hit hit = iterator.next();
            double oldScore = hit.getRelevance().getScore();
            boolean didRerank = hitRescorer.rescoreHit(hit);
            if (didRerank) {
                ranges.withInitialScore(oldScore);
                ranges.withFinalScore(hit.getRelevance().getScore());
                --rerankCount;
                iterator.remove();
            }
        }
        // if any hits are left in the list, they need rescaling:
        if (ranges.valid()) {
            double scale = ranges.scale();
            double bias = ranges.bias();
            for (Hit hit : hitsToRescore) {
                double oldScore = hit.getRelevance().getScore();
                hit.setRelevance(oldScore * scale + bias);
            }
        }
        result.hits().sort();
    }

}
