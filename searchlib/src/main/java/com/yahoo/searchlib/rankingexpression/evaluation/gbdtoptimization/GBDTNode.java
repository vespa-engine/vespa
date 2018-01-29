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
 * An optimized version of a decision tree.
 *
 * @author bratseth
 */
public final class GBDTNode extends ExpressionNode {

    // The GBDT node vm works by reading doubles one at a time and interpreting them
    // as either constant data or a mangling of opcode and variable reference:
    // The value space is as follows:
    // n=[0,MAX_LEAF_VALUE>                                             : n is data (tree leaf constant value)
    // n=[MAX_LEAF_VALUE+MAX_VARIABLES*0,MAX_LEAF_VALUE+MAX_VARIABLES*1>: < than var at index n
    // n=[MAX_LEAF_VALUE+MAX_VARIABLES*1,MAX_LEAF_VALUE+MAX_VARIABLES*2>: = to var at index n-MAX_VARIABLES
    // n=[MAX_LEAF_VALUE+MAX_VARIABLES*2,MAX_LEAF_VALUE+MAX_VARIABLES*3]: n-MAX_VARIABLES*2 is IN the following set

    // The full layout of an IF instruction is
    // COMPARISON,TRUE_BRANCH_LENGTH,TRUE_BRANCH,FALSE_BRANCH
    // where COMPARISON is VARIABLE_AND_OPCODE,COMPARE_CONSTANT if the opcode is < or =,
    // and                 VARIABLE_AND_OPCODE,COMPARE_CONSTANTS_LENGTH,COMPARE_CONSTANTS if the opcode is IN


    // If any change is made to this encoding, this change must also be reflected in GBDTNodeOptimizer

    /** The max (absolute) supported value an optimized leaf may have */
    public final static int MAX_LEAF_VALUE=2*1000*1000*1000;

    /** The max number of variables (features) supported in the context */
    public final static int MAX_VARIABLES=1*1000*1000;

    private final double[] values;

    public GBDTNode(double[] values) {
        this.values=values;
    }

    /** Returns a direct reference to the values of this. The returned array must not be modified. */
    public final double[] values() { return values; }

    @Override
    public final ValueType type(Context context) { return ValueType.doubleType(); }

    @Override
    public final Value evaluate(Context context) {
        return new DoubleValue(evaluate(values,0,context));
    }

    public static double evaluate(double[] values, int startOffset, Context context) {
        int pc = startOffset;
        while (true) {
            double nextValue = values[pc++];
            if (nextValue >= MAX_LEAF_VALUE) { // a condition node
                int offset = (int)nextValue - MAX_LEAF_VALUE;
                boolean comparisonIsTrue = false;
                if (offset < MAX_VARIABLES) {
                    comparisonIsTrue = context.getDouble(offset)<values[pc++];
                }
                else if (offset < MAX_VARIABLES*2) {
                    comparisonIsTrue = context.getDouble(offset-MAX_VARIABLES)==values[pc++];
                }
                else { // offset<MAX_VARIABLES*3
                    double testValue = context.getDouble(offset-MAX_VARIABLES*2);
                    int setValuesLeft = (int)values[pc++];
                    while (setValuesLeft > 0) { // test each value in the set
                        setValuesLeft--;
                        if (testValue == values[pc++]) {
                            comparisonIsTrue=true;
                            break;
                        }
                    }
                    pc += setValuesLeft; // jump to after the set
                }

                if (comparisonIsTrue)
                    pc++; // true branch - skip the jump value
                else
                    pc += values[pc]; // false branch - jump
            }
            else { // a leaf
                return nextValue;
            }
        }
    }

    /** Returns "(optimized condition tree)" */
    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "(optimized condition tree)";
    }
}
