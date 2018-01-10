package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.ImportResult;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.TensorFlowImporter;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces instances of the tensorflow(model-path, signature, output)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author bratseth
 */
public class TensorFlowFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    private final TensorFlowImporter tensorFlowImporter = new TensorFlowImporter();

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<String, ImportResult> importedModels = new HashMap<>();

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
        try {
            if ( ! feature.getName().equals("tensorflow")) return feature;

            if (feature.getArguments().isEmpty())
                throw new IllegalArgumentException("A tensorflow node must take an argument pointing to " +
                                                   "the tensorflow model directory under [application]/models");

            String modelPath = asString(feature.getArguments().expressions().get(0));
            ImportResult result = importedModels.computeIfAbsent(modelPath, k -> tensorFlowImporter.importModel(modelPath));

            // Find the specified expression
            ImportResult.Signature signature = chooseOrDefault("signatures", result.signatures(),
                                                               optionalArgument(1, feature.getArguments()));
            String output = chooseOrDefault("outputs", signature.outputs(),
                                            optionalArgument(2, feature.getArguments()));

            // Add all constants
            result.constants().forEach((k, v) -> context.rankProfile().addConstantTensor(k, new TensorValue(v)));

            return result.expressions().get(output).getRoot();
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import tensorflow model from " + feature, e);
        }
    }

    /**
     * Returns the specified, existing map value, or the only map value if no key is specified.
     * Throws IllegalArgumentException in all other cases.
     */
    private <T> T chooseOrDefault(String valueDescription, Map<String, T> map, Optional<String> key) {
        if ( ! key.isPresent()) {
            if (map.size() == 0)
                throw new IllegalArgumentException("No " + valueDescription + " are present");
            if (map.size() > 1)
                throw new IllegalArgumentException("Model has multiple " + valueDescription + ", but no " +
                                                   valueDescription + " argument is specified");
            return map.values().stream().findFirst().get();
        }
        else {
            T value = map.get(key.get());
            if (value == null)
                throw new IllegalArgumentException("Model does not have the specified " +
                                                   valueDescription + " '" + key.get() + "'");
            return value;
        }
    }

    private Optional<String> optionalArgument(int argumentIndex, Arguments arguments) {
        if (argumentIndex >= arguments.expressions().size())
            return Optional.empty();
        return Optional.of(asString(arguments.expressions().get(argumentIndex)));
    }

    private String asString(ExpressionNode node) {
        if ( ! (node instanceof ConstantNode))
            throw new IllegalArgumentException("Expected a constant string as tensorflow argument, but got '" + node);
        return stripQuotes(((ConstantNode)node).sourceString());
    }

    private String stripQuotes(String s) {
        if ( ! isQuoteSign(s.codePointAt(0))) return s;
        if ( ! isQuoteSign(s.codePointAt(s.length() - 1 )))
            throw new IllegalArgumentException("tensorflow argument [" + s + "] is missing endquote");
        return s.substring(1, s.length()-1);
    }

    private boolean isQuoteSign(int c) {
        return c == '\'' || c == '"';
    }


}
