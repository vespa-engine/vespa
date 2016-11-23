// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.Tensor;

import java.util.Deque;
import java.util.List;

/**
 * A node which joins two tensors
 *
 * @author bratseth
 */
 @Beta
public class TensorJoinNode extends CompositeNode {

    /** The tensor to aggregate over */
    private final ExpressionNode argument1, argument2;
    
    private final LambdaFunctionNode doubleJoiner;
    
    public TensorJoinNode(ExpressionNode argument1, ExpressionNode argument2, LambdaFunctionNode doubleJoiner) {
        this.argument1 = argument1;
        this.argument2 = argument2;
        this.doubleJoiner = doubleJoiner;
    }

    @Override
    public List<ExpressionNode> children() {
        return ImmutableList.of(argument1, argument2, doubleJoiner);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 3) 
            throw new IllegalArgumentException("A tensor join node must have two tensors and one joiner");
        return new TensorJoinNode(children.get(0), children.get(1), (LambdaFunctionNode)children.get(2));
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "join(" + argument1.toString(context, path, parent) + ", " + 
                         argument2.toString(context, path, parent) + ", " + 
                         doubleJoiner.toString() + ")";
    }

    @Override
    public Value evaluate(Context context) {
        Tensor argument1Value = asTensor(argument1.evaluate(context), argument1);
        Tensor argument2Value = asTensor(argument2.evaluate(context), argument2);
        return new TensorValue(argument1Value.join(argument2Value, doubleJoiner.asDoubleBinaryOperator()));
    }
    
    private Tensor asTensor(Value value, ExpressionNode producingNode) {
        if ( ! ( value instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to join '" + producingNode + "', " +
                                               "but this returns " + value + ", not a tensor");
        return ((TensorValue)value).asTensor();
    }

}
