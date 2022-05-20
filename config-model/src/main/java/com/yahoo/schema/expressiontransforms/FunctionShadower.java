// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.RankProfile;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

/**
 * Transforms function nodes to reference nodes if a rank profile function shadows a built-in function.
 * This has the effect of allowing rank profile functions to redefine built-in functions.
 * Another effect is that we can add built-in functions over time
 * without fear of breaking existing users' functions with the same name.
 *
 * However, there is a (largish) caveat. If a user has a function with a certain number
 * of arguments, and we add in a built-in function with a different arity,
 * this will cause parse errors as the Java parser gives precedence to
 * built-in functions.
 *
 * @author lesters
 */
public class FunctionShadower extends ExpressionTransformer<RankProfileTransformContext> {

    @Override
    public RankingExpression transform(RankingExpression expression, RankProfileTransformContext context) {
        ExpressionNode node = expression.getRoot();
        ExpressionNode result = transform(node, context);
        return (result == node)
                ? expression
                : new RankingExpression(expression.getName(), result);
    }

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof FunctionNode)
            return transformFunctionNode((FunctionNode) node, context);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        return node;
    }

    private ExpressionNode transformFunctionNode(FunctionNode function, RankProfileTransformContext context) {
        String name = function.getFunction().toString();
        RankProfile.RankingExpressionFunction rankingExpressionFunction = context.rankProfile().findFunction(name);
        if (rankingExpressionFunction == null)
            return transformChildren(function, context);

        int functionArity = function.getFunction().arity();
        if (functionArity != rankingExpressionFunction.function().arguments().size())
            return transformChildren(function, context);

        ReferenceNode node = new ReferenceNode(name, function.children(), null);
        return transformChildren(node, context);
    }

}
