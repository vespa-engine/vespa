// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Arrays;
import java.util.Optional;

/**
 * A GBDT node representing a set inclusion test: feature IN [value-list] where values can be strings or numbers.
 *
 * @author bratseth
 */
public final class CategoryFeatureNode extends FeatureNode {

    private final Value[] values;

    public CategoryFeatureNode(String feature, Value[] values, Optional<Integer> samples, TreeNode left, TreeNode right) {
        super(feature, samples, left, right);
        this.values = Arrays.copyOf(values, values.length);
    }

    /** Returns a copy of the array of values in this */
    public Value[] values() {
        return Arrays.copyOf(values, values.length);
    }

    @Override
    protected String rankingExpressionCondition() {
        return " in " + Arrays.toString(values);
    }

}
