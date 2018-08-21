// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.integration.ml.OnnxImporter;
import com.yahoo.searchlib.rankingexpression.integration.ml.TensorFlowImporter;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Replaces instances of the tensorflow(model-path, signature, output)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author bratseth
 */
public class TensorFlowFeatureConverter extends ExpressionTransformer<RankProfileTransformContext>  {

    private final ImportedModels importedModels = new ImportedModels(new TensorFlowImporter());

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
        if ( ! feature.getName().equals("tensorflow")) return feature;

        try {
            Path modelPath = Path.fromString(ConvertedModel.FeatureArguments.asString(feature.getArguments().expressions().get(0)));
            // TODO: Increase scope of this instance to a rank profile:
            ConvertedModel convertedModel = new ConvertedModel(modelPath, context, importedModels, new ConvertedModel.FeatureArguments(feature.getArguments()));
            return convertedModel.expression(asFeatureArguments(feature.getArguments()));
        }
        catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    private ConvertedModel.FeatureArguments asFeatureArguments(Arguments arguments) {
        if (arguments.isEmpty())
            throw new IllegalArgumentException("A tensorflow node must take an argument pointing to " +
                                               "the tensorflow model directory under [application]/models");
        if (arguments.expressions().size() > 3)
            throw new IllegalArgumentException("A tensorflow feature can have at most 3 arguments");

        return new ConvertedModel.FeatureArguments(arguments);
    }

}
