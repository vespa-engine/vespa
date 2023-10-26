// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    private final HitRescorer hitRescorer;
    private final int rerankCount;
    private final List<WrappedHit> hitsToRescore = new ArrayList<>();
    private final RangeAdjuster ranges = new RangeAdjuster();

    ResultReranker(HitRescorer hitRescorer, int rerankCount) {
        this.hitRescorer = hitRescorer;
        this.rerankCount = rerankCount;
    }

    void rerankHits(Result result) {
        gatherHits(result);
        runPreProcessing();
        hitRescorer.runNormalizers();
        runProcessing();
        runPostProcessing();
        result.hits().sort();
    }

    private void gatherHits(Result result) {
        for (var iterator = result.hits().deepIterator(); iterator.hasNext();) {
            Hit hit = iterator.next();
            if (hit.isMeta() || hit instanceof HitGroup) {
                continue;
            }
            // what about hits inside grouping results?
            // they did not show up here during manual testing.
            var wrapped = WrappedHit.from(hit);
            if (wrapped != null) hitsToRescore.add(wrapped);
        }
    }

    private void runPreProcessing() {
        // we can't be 100% certain that hits were sorted according to relevance:
        hitsToRescore.sort(Comparator.naturalOrder());
        int count = 0;
        for (WrappedHit hit : hitsToRescore) {
            if (count == rerankCount) break;
            hitRescorer.preprocess(hit);
            ++count;
        }
    }

    private void runProcessing() {
        int count = 0;
        for (var iterator = hitsToRescore.iterator(); count < rerankCount && iterator.hasNext(); ) {
            WrappedHit wrapped = iterator.next();
            double oldScore = wrapped.getScore();
            double newScore = hitRescorer.rescoreHit(wrapped);
            ranges.withInitialScore(oldScore);
            ranges.withFinalScore(newScore);
            ++count;
            iterator.remove();
        }
    }

    private void runPostProcessing() {
        // if any hits are left in the list, they may need rescaling:
        if (ranges.rescaleNeeded() && ! hitsToRescore.isEmpty()) {
            double scale = ranges.scale();
            double bias = ranges.bias();
            for (WrappedHit wrapped : hitsToRescore) {
                double oldScore = wrapped.getScore();
                wrapped.setScore(oldScore * scale + bias);
            }
        }
    }

}
