// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Superclass of expression transformers
 *
 * @author bratseth
 */
public abstract class ExpressionTransformer {

    public RankingExpression transform(RankingExpression expression, TransformContext context) {
        return new RankingExpression(expression.getName(), transform(expression.getRoot(), context));
    }

    /** Transforms an expression node and returns the transformed node */
    public abstract ExpressionNode transform(ExpressionNode node, TransformContext context);

    /**
     * Utility method which calls transform on each child of the given node and return the resulting transformed
     * composite
     */
    protected CompositeNode transformChildren(CompositeNode node, TransformContext context) {
        List<ExpressionNode> children = node.children();
        List<ExpressionNode> transformedChildren = new ArrayList<>(children.size());
        for (ExpressionNode child : children)
            transformedChildren.add(transform(child, context));
        return node.setChildren(transformedChildren);
    }


}
