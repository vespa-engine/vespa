// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

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
 * Replaces instances of the onnx(model-path, output)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author bratseth
 * @author lesters
 */
public class OnnxFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<Path, ConvertedModel> convertedOnnxModels = new HashMap<>();

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
        if ( ! feature.getName().equals("onnx_vespa")) return feature;

        try {
            FeatureArguments arguments = asFeatureArguments(feature.getArguments());
            ConvertedModel convertedModel =
                    convertedOnnxModels.computeIfAbsent(arguments.path(),
                                                        path -> ConvertedModel.fromSourceOrStore(path, true, context));
            return convertedModel.expression(arguments, context);
        }
        catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use Onnx model from " + feature, e);
        }
    }

    private FeatureArguments asFeatureArguments(Arguments arguments) {
        if (arguments.isEmpty())
            throw new IllegalArgumentException("An ONNX node must take an argument pointing to " +
                                               "the ONNX model file under [application]/models");
        if (arguments.expressions().size() > 3)
            throw new IllegalArgumentException("An onnx feature can have at most 3 arguments");

        return new FeatureArguments(arguments);
    }

}
