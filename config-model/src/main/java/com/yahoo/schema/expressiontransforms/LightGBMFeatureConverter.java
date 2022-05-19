// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.vespa.model.ml.ConvertedModel;
import com.yahoo.vespa.model.ml.FeatureArguments;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces instances of the lightgbm(model-path) pseudofeature with the
 * native Vespa ranking expression implementing the same computation.
 *
 * @author lesters
 */
public class LightGBMFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<Path, ConvertedModel> convertedLightGBMModels = new HashMap<>();

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
        if ( ! feature.getName().equals("lightgbm")) return feature;

        try {
            FeatureArguments arguments = asFeatureArguments(feature.getArguments());
            ConvertedModel convertedModel =
                    convertedLightGBMModels.computeIfAbsent(arguments.path(),
                                                           path -> ConvertedModel.fromSourceOrStore(path, true, context));
            return convertedModel.expression(arguments, context);
        } catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use LightGBM model from " + feature, e);
        }
    }

    private FeatureArguments asFeatureArguments(Arguments arguments) {
        if (arguments.size() != 1)
            throw new IllegalArgumentException("A lightgbm node must take a single argument pointing to " +
                                               "the LightGBM model file under [application]/models");
        return new FeatureArguments(arguments);
    }

}
