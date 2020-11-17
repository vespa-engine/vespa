// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.path.Path;
import com.yahoo.searchdefinition.ImmutableSearch;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.vespa.model.ml.ConvertedModel;
import com.yahoo.vespa.model.ml.FeatureArguments;
import com.yahoo.vespa.model.ml.ModelName;

import java.util.List;

/**
 * Transforms ONNX model features of the forms:
 *
 *     onnxModel(config_name)
 *     onnxModel(config_name).output
 *     onnxModel("path/to/model")
 *     onnxModel("path/to/model").output
 *     onnxModel("path/to/model", "path/to/output")
 *     onnxModel("path/to/model", "unused", "path/to/output")    // signature is unused
 *     onnx(...)   // same as with onnxModel, onnx is an alias of onnxModel
 *
 * To the format expected by the backend:
 *
 *     onnxModel(config_name).output
 *
 * @author lesters
 */
public class OnnxModelTransformer extends ExpressionTransformer<RankProfileTransformContext> {

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
        if (context.rankProfile() == null) return feature;
        if (context.rankProfile().getSearch() == null) return feature;
        return transformFeature(feature, context.rankProfile());
    }

    public static ExpressionNode transformFeature(ReferenceNode feature, RankProfile rankProfile) {
        ImmutableSearch search = rankProfile.getSearch();
        final String featureName = feature.getName();
        if ( ! featureName.equals("onnxModel") && ! featureName.equals("onnx")) return feature;

        Arguments arguments = feature.getArguments();
        if (arguments.isEmpty())
            throw new IllegalArgumentException("An " + featureName + " feature must take an argument referring to a " +
                    "onnx-model config or an ONNX file.");
        if (arguments.expressions().size() > 3)
            throw new IllegalArgumentException("An " + featureName + " feature can have at most 3 arguments.");

        // Check that the model configuration "onnx-model" exists. If not defined, it should have been added
        // by the "OnnxModelConfigGenerator" processor. If it still doesn't exist, it is because we can't find
        // the actual ONNX file, which can happen if we are restarting or upgrading an application using an
        // ONNX file that was transformed to Vespa ranking expressions. We then assume it is in the model store.

        String modelConfigName = getModelConfigName(feature.reference());
        OnnxModel onnxModel = search.onnxModels().get(modelConfigName);
        if (onnxModel == null) {
            String path = asString(arguments.expressions().get(0));
            ModelName modelName = new ModelName(null, Path.fromString(path), true);
            ConvertedModel convertedModel = ConvertedModel.fromStore(modelName, path, rankProfile);
            FeatureArguments featureArguments = new FeatureArguments(arguments);
            return convertedModel.expression(featureArguments, null);
        }

        String defaultOutput = onnxModel.getOutputMap().get(onnxModel.getDefaultOutput());
        String output = getModelOutput(feature.reference(), defaultOutput);
        if (! onnxModel.getOutputMap().containsValue(output)) {
            throw new IllegalArgumentException(featureName + " argument '" + output +
                    "' output not found in model '" + onnxModel.getFileName() + "'");
        }
        return new ReferenceNode("onnxModel", List.of(new ReferenceNode(modelConfigName)), output);
    }

    public static String getModelConfigName(Reference reference) {
        if (reference.arguments().size() > 0) {
            ExpressionNode expr = reference.arguments().expressions().get(0);
            if (expr instanceof ReferenceNode) {  // refers to onnx-model config
                return expr.toString();
            }
            if (expr instanceof ConstantNode) {  // refers to an file path
                return asValidIdentifier(expr);
            }
        }
        return null;
    }

    public static String getModelOutput(Reference reference, String defaultOutput) {
        if (reference.output() != null) {
            return reference.output();
        } else if (reference.arguments().expressions().size() == 2) {
            return asValidIdentifier(reference.arguments().expressions().get(1));
        } else if (reference.arguments().expressions().size() > 2) {
            return asValidIdentifier(reference.arguments().expressions().get(2));
        }
        return defaultOutput;
    }

    public static String stripQuotes(String s) {
        if (isNotQuoteSign(s.codePointAt(0))) return s;
        if (isNotQuoteSign(s.codePointAt(s.length() - 1)))
            throw new IllegalArgumentException("argument [" + s + "] is missing end quote");
        return s.substring(1, s.length()-1);
    }

    public static String asValidIdentifier(String str) {
        return str.replaceAll("[^\\w\\d\\$@_]", "_");
    }

    private static String asValidIdentifier(ExpressionNode node) {
        return asValidIdentifier(asString(node));
    }

    private static boolean isNotQuoteSign(int c) {
        return c != '\'' && c != '"';
    }

    public static String asString(ExpressionNode node) {
        if ( ! (node instanceof ConstantNode))
            throw new IllegalArgumentException("Expected a constant string as argument, but got '" + node);
        return stripQuotes(((ConstantNode)node).sourceString());
    }

}
