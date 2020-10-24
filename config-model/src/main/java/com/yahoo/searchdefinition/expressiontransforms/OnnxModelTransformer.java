// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.ImmutableSearch;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.List;

/**
 * Transforms instances of the onnxModel ranking feature and generates
 * ONNX configuration if necessary.
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
        return transformFeature(feature, context.rankProfile().getSearch());
    }

    public static ReferenceNode transformFeature(ReferenceNode feature, ImmutableSearch search) {
        if (!feature.getName().equals("onnxModel")) return feature;

        Arguments arguments = feature.getArguments();
        if (arguments.isEmpty())
            throw new IllegalArgumentException("An onnxModel feature must take an argument referring to a " +
                    "onnx-model config or a ONNX file.");
        if (arguments.expressions().size() > 2)
            throw new IllegalArgumentException("An onnxModel feature can have at most 2 arguments.");

        // Validation that the file actually exists is handled when the file is added to file distribution.
        // Validation of inputs, outputs and corresponding types are currently handled by RankSetupValidator.

        String modelConfigName;
        OnnxModel onnxModel;
        if (arguments.expressions().get(0) instanceof ReferenceNode) {
            modelConfigName = arguments.expressions().get(0).toString();
            onnxModel = search.onnxModels().get(modelConfigName);
            if (onnxModel == null) {
                throw new IllegalArgumentException("onnxModel argument '" + modelConfigName + "' config not found");
            }
        } else if (arguments.expressions().get(0) instanceof ConstantNode) {
            String path = asString(arguments.expressions().get(0));
            modelConfigName = asValidIdentifier(path);
            onnxModel = search.onnxModels().get(modelConfigName);
            if (onnxModel == null) {
                onnxModel = new OnnxModel(modelConfigName, path);
                search.onnxModels().add(onnxModel);
            }
        } else {
            throw new IllegalArgumentException("Illegal argument to onnxModel: '" + arguments.expressions().get(0) + "'");
        }

        String output = null;
        if (feature.getOutput() != null) {
            output = feature.getOutput();
            if ( ! hasOutputMapping(onnxModel, output)) {
                onnxModel.addOutputNameMapping(output, output);
            }
        } else if (arguments.expressions().size() > 1) {
            String name = asString(arguments.expressions().get(1));
            output = asValidIdentifier(name);
            if ( ! hasOutputMapping(onnxModel, output)) {
                onnxModel.addOutputNameMapping(name, output);
            }
        }

        // Replace feature with name of config
        ExpressionNode argument = new ReferenceNode(modelConfigName);
        return new ReferenceNode("onnxModel", List.of(argument), output);

    }

    private static boolean hasOutputMapping(OnnxModel onnxModel, String as) {
        return onnxModel.getOutputMap().stream().anyMatch(m -> m.getVespaName().equals(as));
    }

    private static String asString(ExpressionNode node) {
        if ( ! (node instanceof ConstantNode))
            throw new IllegalArgumentException("Expected a constant string as argument, but got '" + node);
        return stripQuotes(((ConstantNode)node).sourceString());
    }

    private static String stripQuotes(String s) {
        if ( ! isQuoteSign(s.codePointAt(0))) return s;
        if ( ! isQuoteSign(s.codePointAt(s.length() - 1 )))
            throw new IllegalArgumentException("argument [" + s + "] is missing endquote");
        return s.substring(1, s.length()-1);
    }

    private static boolean isQuoteSign(int c) {
        return c == '\'' || c == '"';
    }

    private static String asValidIdentifier(String str) {
        return str.replaceAll("[^\\w\\d\\$@_]", "_");
    }

}
