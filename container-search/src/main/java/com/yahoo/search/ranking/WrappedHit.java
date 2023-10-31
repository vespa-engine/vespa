// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.ranking;

import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;

import static com.yahoo.searchlib.rankingexpression.Reference.RANKING_EXPRESSION_WRAPPER;

import java.util.logging.Logger;

class WrappedHit implements Comparable<WrappedHit> {

    private static final Logger logger = Logger.getLogger(WrappedHit.class.getName());
    private final Hit hit;
    private final FeatureData matchFeatures;
    private int idx = -1;

    private WrappedHit(Hit hit, FeatureData matchFeatures) {
        this.hit = hit;
        this.matchFeatures = matchFeatures;
    }

    static WrappedHit from(Hit hit) {
        if (hit.getField("matchfeatures") instanceof FeatureData mf) {
            return new WrappedHit(hit, mf);
        } else {
            return null;
        }
    }

    double getScore() {
        return hit.getRelevance().getScore();
    }

    void setScore(double value) {
        hit.setRelevance(value);
    }

    int getIdx() {
        if (idx < 0) {
            throw new IllegalStateException("Missing index");
        }
        return idx;
    }

    void setIdx(int value) {
        if (idx == value) {
            return;
        } else if (idx < 0) {
            idx = value;
        } else {
            throw new IllegalArgumentException("Cannot re-assign index " + idx + " -> " + value);
        }
    }

    public int compareTo(WrappedHit other) {
        return hit.compareTo(other.hit);
    }

    Tensor getTensor(String argName) {
        var asTensor = matchFeatures.getTensor(argName);
        if (asTensor == null) {
            asTensor = matchFeatures.getTensor(alternate(argName));
        }
        return asTensor;
    }

    private static final String RE_PREFIX = RANKING_EXPRESSION_WRAPPER + "(";
    private static final String RE_SUFFIX = ")";
    private static final int RE_PRE_LEN = RE_PREFIX.length();
    private static final int RE_SUF_LEN = RE_SUFFIX.length();

    // rankingExpression(foo) <-> foo
    static String alternate(String argName) {
        if (argName.startsWith(RE_PREFIX) && argName.endsWith(RE_SUFFIX)) {
            return argName.substring(RE_PRE_LEN, argName.length() - RE_SUF_LEN);
        } else {
            return RE_PREFIX + argName + RE_SUFFIX;
        }
    }

}
