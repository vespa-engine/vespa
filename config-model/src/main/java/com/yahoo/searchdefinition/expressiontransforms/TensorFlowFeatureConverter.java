// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.base.Joiner;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.TensorFlowImporter;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.TensorFlowModel;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
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
// TODO: - Verify types of macros
//       - Avoid name conflicts across models for constants
public class TensorFlowFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    // TODO: Make system test work with this set to true, then remove the "true" path
    private static final boolean constantsInConfig = true;

    private final TensorFlowImporter tensorFlowImporter = new TensorFlowImporter();

    /** A cache of imported models indexed by model path. This avoids importing the same model multiple times. */
    private final Map<Path, TensorFlowModel> importedModels = new HashMap<>();

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

            Path modelPath = Path.fromString(asString(feature.getArguments().expressions().get(0)));
            TensorFlowModel result = importedModels.computeIfAbsent(modelPath, k -> importModel(modelPath));

            // Find the specified expression
            TensorFlowModel.Signature signature = chooseSignature(result,
                                                                  optionalArgument(1, feature.getArguments()));
            RankingExpression expression = chooseOutput(signature,
                                                        optionalArgument(2, feature.getArguments()));

            // Add all constants (after finding outputs to fail faster when the output is not found)
            if (constantsInConfig)
                result.constants().forEach((k, v) -> context.rankProfile().addConstantTensor(k, new TensorValue(v)));
            else // correct way, disabled for now
                result.constants().forEach((k, v) -> transformConstant(modelPath, context.rankProfile(), k, v));

            return expression.getRoot();
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    private TensorFlowModel importModel(Path modelPath) {
        try {
            return tensorFlowImporter.importModel(new File(ApplicationPackage.MODELS_DIR.append(modelPath)
                                                                                        .getRelative())
                                                                                        .getCanonicalPath());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the specified, existing signature, or the only signature if none is specified.
     * Throws IllegalArgumentException in all other cases.
     */
    private TensorFlowModel.Signature chooseSignature(TensorFlowModel importResult, Optional<String> signatureName) {
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
            TensorFlowModel.Signature signature = importResult.signatures().get(signatureName.get());
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
    private RankingExpression chooseOutput(TensorFlowModel.Signature signature, Optional<String> outputName) {
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

    private void transformConstant(Path modelPath, RankProfile profile, String constantName, Tensor constantValue) {
        try {
            if (profile.getSearch().getRankingConstants().containsKey(constantName)) return;

            System.out.println("modelPath is " + modelPath);
            File constantFilePath = new File(ApplicationPackage.MODELS_GENERATED_DIR.append(modelPath)
                                                                                    .append("constants")
                                                                                    .getRelative())
                                    .getCanonicalFile();
            System.out.println("constant file path is " + constantFilePath);
            if ( ! constantFilePath.exists())
                if ( ! constantFilePath.mkdir())
                    throw new IOException("Could not create directory " + constantFilePath);

            // "tbf" ending for "typed binary format" - recognized by the nodes receiving the file:
            File constantFile = new File(constantFilePath, constantName + ".tbf");
            IOUtils.writeFile(constantFile, TypedBinaryFormat.encode(constantValue));
            profile.getSearch().addRankingConstant(new RankingConstant(constantName, constantValue.type(), constantFile.getPath()));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String skippedOutputsDescription(TensorFlowModel.Signature signature) {
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
