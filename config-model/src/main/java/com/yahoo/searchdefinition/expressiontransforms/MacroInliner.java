// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.searchlib.rankingexpression.transform.TransformContext;

/**
 * Inlines macros in ranking expressions
 *
 * @author bratseth
 */
public class MacroInliner extends ExpressionTransformer {

    @Override
    public ExpressionNode transform(ExpressionNode node, TransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeatureNode((ReferenceNode)node, (RankProfileTransformContext)context);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        return node;
    }

    private ExpressionNode transformFeatureNode(ReferenceNode feature, RankProfileTransformContext context) {
        RankProfile.Macro macro = context.inlineMacros().get(feature.getName());
        if (macro == null) return feature;
        return transform(macro.getRankingExpression().getRoot(), context); // inline recursively and return
    }

}
