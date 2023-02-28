// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;

import java.util.function.Supplier;
import java.util.logging.Logger;

class HitRescorer {

    private static final Logger logger = Logger.getLogger(HitRescorer.class.getName());
    
    private final Supplier<Evaluator> evaluatorSource;

    public HitRescorer(Supplier<Evaluator> evaluatorSource) {
        this.evaluatorSource = evaluatorSource;
    }

    boolean rescoreHit(Hit hit) {
        var features = hit.getField("matchfeatures");
        if (features instanceof FeatureData matchFeatures) {
            var scorer = evaluatorSource.get();
            for (String argName : scorer.needInputs()) {
                var asTensor = matchFeatures.getTensor(argName);
                if (asTensor == null) {
                    asTensor = matchFeatures.getTensor(alternate(argName));
                }
                if (asTensor != null) {
                    scorer.bind(argName, asTensor);
                } else {
                    logger.warning("Missing match-feature for Evaluator argument: " + argName);
                    return false;
                }
            }
            double newScore = scorer.evaluateScore();
            hit.setRelevance(newScore);
            return true;
        } else {
            logger.warning("Hit without match-features: " + hit);
            return false;
        }
    }

    private static final String RE_PREFIX = "rankingExpression(";
    private static final String RE_SUFFIX = ")";
    private static final int RE_PRE_LEN = RE_PREFIX.length();
    private static final int RE_SUF_LEN = RE_SUFFIX.length();

    static String alternate(String argName) {
        if (argName.startsWith(RE_PREFIX) && argName.endsWith(RE_SUFFIX)) {
            return argName.substring(RE_PRE_LEN, argName.length() - RE_SUF_LEN);
        }
        return argName;
    }
}
