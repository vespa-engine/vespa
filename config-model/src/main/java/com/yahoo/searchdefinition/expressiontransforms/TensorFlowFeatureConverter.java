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
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.TensorFlowModel.Signature;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
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
    private static final boolean constantsInConfig = false;

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

            // modelPath: The relative path to this model below the "models/" dir in the application package
            Path modelPath = Path.fromString(asString(feature.getArguments().expressions().get(0)));
            Optional<String> signatureArg = optionalArgument(1, feature.getArguments());
            Optional<String> outputArg = optionalArgument(2, feature.getArguments());
            if (new File(ApplicationPackage.MODELS_DIR.append(modelPath).getRelative()).getCanonicalFile().exists())
                return transformFromTensorFlowModel(modelPath, signatureArg, outputArg, context.rankProfile());
            else
                return transformFromStoredConvertedModel(modelPath, signatureArg, outputArg);
        }
        catch (IllegalArgumentException | IOException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    /**
     * Returns the specified, existing signature, or the only signature if none is specified.
     * Throws IllegalArgumentException in all other cases.
     */
    private Signature chooseSignature(TensorFlowModel importResult, Optional<String> signatureName) {
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
            Signature signature = importResult.signatures().get(signatureName.get());
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
    private String chooseOutput(Signature signature, Optional<String> outputName) {
        if ( ! outputName.isPresent()) {
            if (signature.outputs().size() == 0)
                throw new IllegalArgumentException("No outputs are available" + skippedOutputsDescription(signature));
            if (signature.outputs().size() > 1)
                throw new IllegalArgumentException(signature + " has multiple outputs (" +
                                                   Joiner.on(", ").join(signature.outputs().keySet()) +
                                                   "), one must be specified " +
                                                   "as a third argument to tensorflow()");
            return signature.outputs().get(signature.outputs().keySet().stream().findFirst().get());
        }
        else {
            String output = signature.outputs().get(outputName.get());
            if (output == null) {
                if (signature.skippedOutputs().containsKey(outputName.get()))
                    throw new IllegalArgumentException("Could not use output '" + outputName.get() + "': " +
                                                       signature.skippedOutputs().get(outputName.get()));
                else
                    throw new IllegalArgumentException("Model does not have the specified output '" +
                                                       outputName.get() + "'");
            }
            return output;
        }
    }

    private ExpressionNode transformFromTensorFlowModel(Path modelPath,
                                                        Optional<String> signatureArg,
                                                        Optional<String> outputArg,
                                                        RankProfile rankProfile) {
        TensorFlowModel model = importedModels.computeIfAbsent(modelPath, k -> importModel(modelPath));

        // Find the specified expression
        Signature signature = chooseSignature(model, signatureArg);
        String output = chooseOutput(signature, outputArg);
        RankingExpression expression = model.expressions().get(output);
        writeConverted(modelPath, signatureArg, outputArg, expression);

        // Add all constants (after finding outputs to fail faster when the output is not found)
        if (constantsInConfig)
            model.constants().forEach((k, v) -> rankProfile.addConstantTensor(k, new TensorValue(v)));
        else // correct way, disabled for now
            model.constants().forEach((k, v) -> transformConstant(modelPath, rankProfile, k, v));

        return expression.getRoot();
    }

    private ExpressionNode transformFromStoredConvertedModel(Path modelPath,
                                                             Optional<String> signatureArg,
                                                             Optional<String> outputArg) {
        File expressionFile = null;
        try {
            expressionFile = expressionFile(modelPath, signatureArg, outputArg);
            return new RankingExpression(IOUtils.readFile(expressionFile)).getRoot();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Could not read " + expressionFile, e);
        }
        catch (ParseException e) {
            throw new IllegalStateException("Could not parse " + expressionFile, e);
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

    private void writeConverted(Path modelPath, Optional<String> signatureArg, Optional<String> outputArg, RankingExpression expression) {
        try {
            IOUtils.writeFile(expressionFile(modelPath, signatureArg, outputArg), expression.getRoot().toString(), false);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void transformConstant(Path modelPath, RankProfile profile, String constantName, Tensor constantValue) {
        try {
            if (profile.getSearch().getRankingConstants().containsKey(constantName)) return;

            File constantFilePath = new File(ApplicationPackage.MODELS_GENERATED_DIR.append(modelPath)
                                                                                    .append("constants")
                                                                                    .getRelative())
                                    .getCanonicalFile();
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

    private File expressionFile(Path modelPath, Optional<String> signatureArg, Optional<String> outputArg) {
        try {
            StringBuilder fileName = new StringBuilder();
            signatureArg.ifPresent(s -> fileName.append(s).append("."));
            outputArg.ifPresent(s -> fileName.append(s).append("."));
            if (fileName.length() == 0) // single signature and output
                fileName.append("single.");
            fileName.append("expression");

            return new File(ApplicationPackage.MODELS_GENERATED_DIR.append(modelPath)
                                                                   .append("expressions")
                                                                   .append(fileName.toString())
                                                                   .getRelative())
                    .getCanonicalFile();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
