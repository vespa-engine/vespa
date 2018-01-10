package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.base.Joiner;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
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
            ImportResult.Signature signature = chooseSignature(result,
                                                               optionalArgument(1, feature.getArguments()));
            RankingExpression expression = chooseOutput(signature,
                                                        optionalArgument(2, feature.getArguments()));

            // Add all constants (after finding outputs to fail faster when the output is not found)
            result.constants().forEach((k, v) -> context.rankProfile().addConstantTensor(k, new TensorValue(v)));

            return expression.getRoot();
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    /**
     * Returns the specified, existing signature, or the only signature if none is specified.
     * Throws IllegalArgumentException in all other cases.
     */
    private ImportResult.Signature chooseSignature(ImportResult importResult, Optional<String> signatureName) {
        if ( ! signatureName.isPresent()) {
            if (importResult.signatures().size() == 0)
                throw new IllegalArgumentException("No signatures are available");
            if (importResult.signatures().size() > 1)
                throw new IllegalArgumentException("Model has multiple signatures (" +
                                                   Joiner.on(", ").join(importResult.signatures().keySet()) +
                                                   "), one must be specified " +
                                                   "as a second argument to tensorflow()");
            return importResult.signatures().values().stream().findFirst().get();
        }
        else {
            ImportResult.Signature signature = importResult.signatures().get(signatureName.get());
            if (signature == null)
                throw new IllegalArgumentException("Model does not have the specified signature '" +
                                                   signatureName.get() + "'");
            return signature;
        }
    }

    /**
     * Returns the specified, existing output expression, or the only output expression if no output name is specified.
     * Throws IllegalArgumentException in all other cases.
     */
    private RankingExpression chooseOutput(ImportResult.Signature signature, Optional<String> outputName) {
        if ( ! outputName.isPresent()) {
            if (signature.outputs().size() == 0)
                throw new IllegalArgumentException("No outputs are available" + skippedOutputsDescription(signature));
            if (signature.outputs().size() > 1)
                throw new IllegalArgumentException(signature + " has multiple outputs (" +
                                                   Joiner.on(", ").join(signature.outputs().keySet()) +
                                                   "), one must be specified " +
                                                   "as a third argument to tensorflow()");
            return signature.outputExpression(signature.outputs().keySet().stream().findFirst().get());
        }
        else {
            RankingExpression expression = signature.outputExpression(outputName.get());
            if (expression == null) {
                if (signature.skippedOutputs().containsKey(outputName.get()))
                    throw new IllegalArgumentException("Could not use output '" + outputName.get() + "': " +
                                                       signature.skippedOutputs().get(outputName.get()));
                else
                    throw new IllegalArgumentException("Model does not have the specified output '" +
                                                       outputName.get() + "'");
            }
            return expression;
        }
    }

    private String skippedOutputsDescription(ImportResult.Signature signature) {
        if (signature.skippedOutputs().isEmpty()) return "";
        StringBuilder b = new StringBuilder(": ");
        signature.skippedOutputs().forEach((k, v) -> b.append("Skipping output '").append(k).append("': ").append(v));
        return b.toString();
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
