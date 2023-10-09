// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Arrays;
import java.util.Optional;

/**
 * A GBDT node representing a numeric "less than" comparison: feature &lt; numeric-value
 *
 * @author bratseth
 */
public final class NumericFeatureNode extends FeatureNode {

    private final Value value;

    public NumericFeatureNode(String feature, Value value, Optional<Integer> samples, TreeNode left, TreeNode right) {
        super(feature, samples, left, right);
        this.value = value;
    }

    /** Returns a copy of the array of values in this */
    public Value value() {
        return value;
    }

    @Override
    public String rankingExpressionCondition() {
        return " < " + value;
    }

}
