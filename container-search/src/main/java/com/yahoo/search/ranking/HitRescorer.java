// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import ai.vespa.models.evaluation.FunctionEvaluator;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;

import java.util.function.Supplier;

public class HitRescorer {

    private final Supplier<FunctionEvaluator> evaluatorSource;

    private static final String RE_PREFIX = "rankingExpression(";
    private static final String RE_SUFFIX = ")";
    private static final int RE_PRE_LEN = RE_PREFIX.length();
    private static final int RE_SUF_LEN = RE_SUFFIX.length();

    public HitRescorer(Supplier<FunctionEvaluator> evaluatorSource) {
        this.evaluatorSource = evaluatorSource;
    }

    // initial version only
    // we will need to supply values from the query also
    boolean rescoreHit(Hit hit) {
        System.err.println("rescore hit: "+hit);
        var features = hit.getField("matchfeatures");
        if (features instanceof FeatureData matchFeatures) {
            var scorer = evaluatorSource.get();
            System.err.println("scorer: " + scorer);
            System.err.println("scorer function: " + scorer.function());
            for (String argName : scorer.function().arguments()) {
                System.err.println("[1] scorer wants input: " + argName);
            }
            for (String argName : scorer.function().arguments()) {
                System.err.println("[2] scorer wants input: " + argName);
                var asTensor = matchFeatures.getTensor(argName);
                if (asTensor == null && argName.startsWith(RE_PREFIX) && argName.endsWith(RE_SUFFIX)) {
                    String tryExpr = argName.substring(RE_PRE_LEN, argName.length() - RE_SUF_LEN);
                    System.err.println("[3] try substring " + tryExpr);
                    asTensor = matchFeatures.getTensor(tryExpr);
                }
                if (asTensor == null) {
                    System.err.println("MISSING match-feature for FunctionEvaluator argument: " + argName);
                    System.err.println("hit has match features: " + matchFeatures);
                    return false;
                } else {
                    System.err.println("scorer gets input: " + asTensor);
                    scorer.bind(argName, asTensor);
                }
            }
            double newScore = scorer.evaluate().asDouble();
            hit.setRelevance(newScore);
            return true;
        } else {
            System.err.println("Hit without match-features: " + hit);
            return false;
        }
    }

}
