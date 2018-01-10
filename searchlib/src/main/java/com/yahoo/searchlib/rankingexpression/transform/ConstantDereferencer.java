// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces "features" which found in the given constants by their constant value
 *
 * @author bratseth
 */
public class ConstantDereferencer extends ExpressionTransformer {

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node, context);
        else if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        else
            return node;
    }

    private ExpressionNode transformFeature(ReferenceNode node, TransformContext context) {
        if (!node.getArguments().isEmpty())
            return transformArguments(node, context);
        else
            return transformConstantReference(node, context);
    }

    private ExpressionNode transformArguments(ReferenceNode node, TransformContext context) {
        List<ExpressionNode> arguments = node.getArguments().expressions();
        List<ExpressionNode> transformedArguments = new ArrayList<>(arguments.size());
        for (ExpressionNode argument : arguments)
            transformedArguments.add(transform(argument, context));
        return node.setArguments(transformedArguments);
    }

    private ExpressionNode transformConstantReference(ReferenceNode node, TransformContext context) {
        Value value = context.constants().get(node.getName());
        if (value == null || (value instanceof TensorValue)) {
            return node; // not a value constant reference
        }
        return new ConstantNode(value.freeze());
    }

}
