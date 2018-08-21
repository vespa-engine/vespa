// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.integration.ml.XgboostImporter;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.io.UncheckedIOException;

/**
 * Replaces instances of the xgboost(model-path)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author grace-lam
 */
public class XgboostFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    private final XgboostImporter xgboostImporter = new XgboostImporter();

    @Override
    public ExpressionNode transform(ExpressionNode node, RankProfileTransformContext context) {
        if (node instanceof ReferenceNode)
            return transformFeature((ReferenceNode) node, context);
        else if (node instanceof CompositeNode)
            return super.transformChildren((CompositeNode) node, context);
        else
            return node;
    }

    private ExpressionNode transformFeature(ReferenceNode feature, RankProfileTransformContext context) {
        if (!feature.getName().equals("xgboost")) return feature;

        try {
            ConvertedModel.FeatureArguments arguments = asFeatureArguments(feature.getArguments());
            ConvertedModel.ModelStore store = new ConvertedModel.ModelStore(context.rankProfile().getSearch().sourceApplication(),
                                                                            arguments.modelPath());
            RankingExpression expression = xgboostImporter.parseModel(store.modelDir().toString());
            return expression.getRoot();
        } catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use XGBoost model from " + feature, e);
        }
    }

    private ConvertedModel.FeatureArguments asFeatureArguments(Arguments arguments) {
        if (arguments.isEmpty())
            throw new IllegalArgumentException("An xgboost node must take an argument pointing to " +
                                               "the xgboost model directory under [application]/models");
        if (arguments.expressions().size() > 1)
            throw new IllegalArgumentException("An xgboost feature can have at most 1 argument");

        return new ConvertedModel.FeatureArguments(arguments);
    }

}
