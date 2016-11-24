// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.evaluation.Context;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;

import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * A node which performs a dimension rename in a tensor
 *
 * @author bratseth
 */
 @Beta
public class TensorRenameNode extends CompositeNode {

    private final ExpressionNode argument;

    private final ImmutableList<String> fromDimensions, toDimensions;

    public TensorRenameNode(ExpressionNode argument, List<String> fromDimensions, List<String> toDimensions) {
        if (fromDimensions.size() < 1)
            throw new IllegalArgumentException("from dimensions is empty, must rename at least one dimension");
        if (fromDimensions.size() != toDimensions.size())
            throw new IllegalArgumentException("Rename from and to dimensions must be equal, was " +
                                               fromDimensions.size() + " and " + toDimensions.size());
        this.argument = argument;
        this.fromDimensions = ImmutableList.copyOf(fromDimensions);
        this.toDimensions = ImmutableList.copyOf(toDimensions);
    }

    @Override
    public List<ExpressionNode> children() {
        return Collections.singletonList(argument);
    }

    @Override
    public CompositeNode setChildren(List<ExpressionNode> children) {
        if (children.size() != 1) throw new IllegalArgumentException("A tensor rename node must have one tensor argument");
        return new TensorRenameNode(children.get(0), fromDimensions, toDimensions);
    }

    @Override
    public String toString(SerializationContext context, Deque<String> path, CompositeNode parent) {
        return "rename(" + argument.toString(context, path, parent) + ", " + 
                       vector(fromDimensions) + ", " + vector(toDimensions) + ")";
    }
    
    private String vector(List<String> list) {
        if (list.size() == 1) return list.get(0);
        
        StringBuilder b = new StringBuilder("[");
        for (String element : list)
            b.append(element).append(",");
        b.setLength(b.length() - 1);
        b.append("]");
        return b.toString();
    }

    @Override
    public Value evaluate(Context context) {
        Value argumentValue = argument.evaluate(context);
        if ( ! ( argumentValue instanceof TensorValue))
            throw new IllegalArgumentException("Attempted to rename dimensions in '" + argument + "', " +
                                               "but this returns " + argumentValue + ", not a tensor");
        TensorValue tensorArgument = (TensorValue)argumentValue;
        return new TensorValue(tensorArgument.asTensor().rename(fromDimensions, toDimensions));
    }

}
