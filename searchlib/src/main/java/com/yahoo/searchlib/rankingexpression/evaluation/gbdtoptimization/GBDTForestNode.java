// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.evaluation.gbdtoptimization;

import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.evaluation.ValueType;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;

import java.util.Deque;

/**
 * An optimized version of a sum of consecutive decision trees.
 *
 * @author bratseth
 */
public class GBDTForestNode extends ExpressionNode {

    private final double[] values;

    public GBDTForestNode(double[] values) {
        this.values=values;
    }

    @Override
    public final ValueType type(Context context) { return ValueType.doubleType(); }

    @Override
    public final Value evaluate(Context context) {
        int pc = 0;
        double treeSum = 0;
        while (pc < values.length) {
            int nextTree = (int)values[pc++];
            treeSum += GBDTNode.evaluate(values, pc, context);
            pc += nextTree;
        }
        return new DoubleValue(treeSum);
    }

    /** Returns (optimized sum of condition trees) */
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "(optimized sum of condition trees of size " + (values.length*8) + " bytes)";
    }

}
