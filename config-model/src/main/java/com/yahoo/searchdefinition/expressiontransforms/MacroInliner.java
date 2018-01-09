// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.Map;

/**
 * Inlines macros in ranking expressions
 *
 * @author bratseth
 */
public class MacroInliner extends ExpressionTransformer {

    private final Map<String, RankProfile.Macro> macros;

    public MacroInliner(Map<String, RankProfile.Macro> macros) {
        this.macros = macros;
    }

    @Override
    public ExpressionNode transform(ExpressionNode node) {
        if (node instanceof ReferenceNode)
            return transformFeatureNode((ReferenceNode)node);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node);
        return node;
    }

    private ExpressionNode transformFeatureNode(ReferenceNode feature) {
        RankProfile.Macro macro = macros.get(feature.getName());
        if (macro == null) return feature;
        return transform(macro.getRankingExpression().getRoot()); // inline recursively and return
    }

}
