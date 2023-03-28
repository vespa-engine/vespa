// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces constant reference pseudofeatures which are scalars by their constant value
 *
 * @author bratseth
 */
public class ConstantDereferencer extends ExpressionTransformer<TransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof TensorFunctionNode tfn) {
            node = tfn.withTransformedExpressions(expr -> transform(expr, context));
        }
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node, context);
        else if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        else
            return node;
    }

    /** Returns true if the given reference is an attribute, constant or query feature */
    private static boolean isSimpleFeature(Reference reference) {
        if ( ! reference.isSimple()) return false;
        String name = reference.name();
        return name.equals("attribute") || name.equals("constant") || name.equals("query");
    }

    private ExpressionNode transformFeature(ReferenceNode node, TransformContext context) {
        if ( ! node.getArguments().isEmpty() && ! isSimpleFeature(node.reference()))
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
        String name = node.getName();
        if (node.reference().name().equals("constant")) {
            ExpressionNode arg = node.getArguments().expressions().get(0);
            if (arg instanceof ReferenceNode) {
                name = ((ReferenceNode)arg).getName();
            }
        }
        Value value = context.constants().get(name);  // works if "constant(...)" is added
        if (value == null || value.type().rank() > 0) {
            return node; // not a number constant reference
        }
        return new ConstantNode(value.freeze());
    }

}
