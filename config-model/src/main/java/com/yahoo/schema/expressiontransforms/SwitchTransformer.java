// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.SwitchNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

/**
 * Transforms switch nodes into equivalent nested if-expressions.
 *
 * Transformation:
 *   switch(d) { case v1: r1, case v2: r2, default: def }
 * becomes:
 *   if(d == v1, r1, if(d == v2, r2, def))
 *
 * @author johsol
 */
public class SwitchTransformer extends ExpressionTransformer<TransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof CompositeNode composite) {
            node = transformChildren(composite, context);
        }

        if (node instanceof SwitchNode switchNode) {
            node = switchNode.toIfNode();
        }

        return node;
    }

}
