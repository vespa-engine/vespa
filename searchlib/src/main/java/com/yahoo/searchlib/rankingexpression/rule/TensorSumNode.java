// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * A node which sums over all cells in the argument tensor
 *
 * @author bratseth
 */
 @Beta
public class TensorSumNode extends CompositeNode {

    /** The tensor to sum */
    private final ExpressionNode argument;

    /** The dimension to sum over, or empty to sum all cells to a scalar */
    private final Optional<String> dimension;

    public TensorSumNode(ExpressionNode argument, Optional<String> dimension) {
        this.argument = argument;
        this.dimension = dimension;
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(argument);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 1) throw new IllegalArgumentException("A tensor sum node must have one tensor argument");
        return new TensorSumNode(children.get(0), dimension);
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "sum(" +
                    argument.toString(context, path, parent) +
                    ( dimension.isPresent() ? ", " + dimension.get() : "" ) +
                    ")";
    }

    @Override
    public Value evaluate(Context context) {
        Value argumentValue = argument.evaluate(context);
        if ( ! ( argumentValue instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to take the tensor sum of argument '" + argument + "', " +
                                               "but this returns " + argumentValue + ", not a tensor");
        TensorValue tensorArgument = (TensorValue)argumentValue;
        if (dimension.isPresent())
            return tensorArgument.sum(dimension.get());
        else
            return tensorArgument.sum();
    }

}
