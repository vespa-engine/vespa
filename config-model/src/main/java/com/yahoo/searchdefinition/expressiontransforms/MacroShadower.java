// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.FunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

/**
 * Transforms function nodes to reference nodes if a macro shadows a built-in function.
 * This has the effect of allowing macros to redefine built-in functions.
 * Another effect is that we can more or less add built-in functions over time
 * without fear of breaking existing users' macros with the same name.
 *
 * However, there is a (largish) caveat. If a user has a macro with a certain number
 * of arguments, and we add in a built-in function with a different arity,
 * this will cause parse errors as the Java parser gives precedence to
 * built-in functions.
 *
 * @author lesters
 */
public class MacroShadower extends ExpressionTransformer {

    @Override
    public RankingExpression transform(RankingExpression expression, TransformContext context) {
        String name = expression.getName();
        ExpressionNode node = expression.getRoot();
        ExpressionNode result = transform(node, context);
        return new RankingExpression(name, result);
    }

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof FunctionNode)
            return transformFunctionNode((FunctionNode) node, context);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        return node;
    }

    protected ExpressionNode transformFunctionNode(FunctionNode function, TransformContext context) {
        String name = function.getFunction().toString();
        RankProfile.Macro macro = ((RankProfileTransformContext)context).rankProfile().getMacros().get(name);
        if (macro == null) {
            return transformChildren(function, context);
        }

        int functionArity = function.getFunction().arity();
        int macroArity = macro.getFormalParams() != null ? macro.getFormalParams().size() : 0;
        if (functionArity != macroArity) {
            return transformChildren(function, context);
        }

        ReferenceNode node = new ReferenceNode(name, function.children(), null);
        return transformChildren(node, context);
    }

}
