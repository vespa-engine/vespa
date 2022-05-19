// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.schema.RankProfile;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

/**
 * Inlines functions in ranking expressions
 *
 * @author bratseth
 */
public class FunctionInliner extends ExpressionTransformer<RankProfileTransformContext> {

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeatureNode((ReferenceNode)node, context);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node, context);
        return node;
    }

    private ExpressionNode transformFeatureNode(ReferenceNode feature, RankProfileTransformContext context) {
        if (feature.getArguments().size() > 0) return feature;  // From RankProfile: only inline no-arg functions
        RankProfile.RankingExpressionFunction rankingExpressionFunction = context.inlineFunctions().get(feature.getName());
        if (rankingExpressionFunction == null) return feature;
        return transform(rankingExpressionFunction.function().getBody().getRoot(), context); // inline recursively and return
    }

}
