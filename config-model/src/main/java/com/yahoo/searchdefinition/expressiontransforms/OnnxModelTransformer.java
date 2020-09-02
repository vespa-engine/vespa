// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.List;

/**
 * Transforms instances of the onnxModel(model-path, output) ranking feature
 * by adding the model file to file distribution and rewriting this feature
 * to point to the generated configuration.
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
        if (!feature.getName().equals("onnxModel")) return feature;

        Arguments arguments = feature.getArguments();
        if (arguments.isEmpty())
            throw new IllegalArgumentException("An onnxModel feature must take an argument pointing to the ONNX file.");
        if (arguments.expressions().size() > 2)
            throw new IllegalArgumentException("An onnxModel feature can have at most 2 arguments.");

        String path = asString(arguments.expressions().get(0));
        String name = toModelName(path);
        String output = arguments.expressions().size() > 1 ? asString(arguments.expressions().get(1)) : null;

        // Validation that the file actually exists is handled when the file is added to file distribution.
        // Validation of inputs, outputs and corresponding types are currently handled by RankSetupValidator.

        // Add model to config
        context.rankProfile().getSearch().onnxModels().add(new OnnxModel(name, path));

        // Replace feature with name of config
        ExpressionNode argument = new ReferenceNode(name);
        return new ReferenceNode("onnxModel", List.of(argument), output);
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

    public static String toModelName(String path) {
        return path.replaceAll("[^\\w\\d\\$@_]", "_");
    }

}
