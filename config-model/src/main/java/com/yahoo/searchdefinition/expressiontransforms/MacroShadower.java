// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.*;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.Map;

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

    private final Map<String, RankProfile.Macro> macros;

    public MacroShadower(Map<String, RankProfile.Macro> macros) {
        this.macros = macros;
    }

    @Override
    public RankingExpression transform(RankingExpression expression) {
        String name = expression.getName();
        ExpressionNode node = expression.getRoot();
        ExpressionNode result = transform(node);
        return new RankingExpression(name, result);
    }

    @Override
    public ExpressionNode transform(ExpressionNode node) {
        if (node instanceof FunctionNode)
            return transformFunctionNode((FunctionNode) node);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node);
        return node;
    }

    protected ExpressionNode transformFunctionNode(FunctionNode function) {
        String name = function.getFunction().toString();
        RankProfile.Macro macro = macros.get(name);
        if (macro == null) {
            return transformChildren(function);
        }

        int functionArity = function.getFunction().arity();
        int macroArity = macro.getFormalParams() != null ? macro.getFormalParams().size() : 0;
        if (functionArity != macroArity) {
            return transformChildren(function);
        }

        ReferenceNode node = new ReferenceNode(name, function.children(), null);
        return transformChildren(node);
    }

}
