// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.transform;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Superclass of expression transformers. The scope (lifetime) of a transformer instance is a single compilation
 * of all the expressions in one rank profile.
 *
 * @author bratseth
 */
public abstract class ExpressionTransformer<CONTEXT extends TransformContext> {

    public RankingExpression transform(RankingExpression expression, CONTEXT context) {
        return new RankingExpression(expression.getName(), transform(expression.getRoot(), context));
    }

    /**
     * Transforms an expression node and returns the transformed node.
     * This ic called with the root node of an expression to transform by clients of transformers.
     * Transforming nested expression nodes are left to each transformer.
     */
    public abstract ExpressionNode transform(ExpressionNode node, CONTEXT context);

    /**
     * Utility method which calls transform on each child of the given node and return the resulting transformed
     * composite
     */
    protected CompositeNode transformChildren(CompositeNode node, CONTEXT context) {
        List<ExpressionNode> children = node.children();
        List<ExpressionNode> transformedChildren = null;

        for (int i = 0; i < children.size(); ++i) {
            ExpressionNode child = children.get(i);
            ExpressionNode transformedChild = transform(child, context);
            if (child != transformedChild) {
                if (transformedChildren == null) {
                    transformedChildren = new ArrayList<>(children);
                }
                transformedChildren.set(i, transformedChild);
            }
        }

        return transformedChildren == null ? node : node.setChildren(transformedChildren);
    }


}
