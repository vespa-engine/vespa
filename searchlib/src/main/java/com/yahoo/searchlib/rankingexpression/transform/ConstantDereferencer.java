// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces "features" which found in the given constants by their constant value
 *
 * @author bratseth
 */
public class ConstantDereferencer extends ExpressionTransformer {

    /** The map of constants to dereference */
    private final Map<String, Value> constants;

    public ConstantDereferencer(Map<String, Value> constants) {
        this.constants = constants;
    }

    @Override
    public ExpressionNode transform(ExpressionNode node) {
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node);
        else if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node);
        else
            return node;
    }

    private ExpressionNode transformFeature(ReferenceNode node) {
        if (!node.getArguments().isEmpty())
            return transformArguments(node);
        else
            return transformConstantReference(node);
    }

    private ExpressionNode transformArguments(ReferenceNode node) {
        List<ExpressionNode> arguments = node.getArguments().expressions();
        List<ExpressionNode> transformedArguments = new ArrayList<>(arguments.size());
        for (ExpressionNode argument : arguments)
            transformedArguments.add(transform(argument));
        return node.setArguments(transformedArguments);
    }

    private ExpressionNode transformConstantReference(ReferenceNode node) {
        Value value = constants.get(node.getName());
        if (value == null || (value instanceof TensorValue)) {
            return node; // not a value constant reference
        }
        return new ConstantNode(value.freeze());
    }

}
