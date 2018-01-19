// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.expressiontransforms;

import com.google.common.base.Joiner;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
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
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Replaces instances of the tensorflow(model-path, signature, output)
 * pseudofeature with the native Vespa ranking expression implementing
 * the same computation.
 *
 * @author bratseth
 */
// TODO: Verify types of macros
// TODO: Avoid name conflicts across models for constants
public class TensorFlowFeatureConverter extends ExpressionTransformer<RankProfileTransformContext> {

    private static final Logger log = Logger.getLogger(TensorFlowFeatureConverter.class.getName());

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
        if ( ! feature.getName().equals("tensorflow")) return feature;

        try {
            ModelStore store = new ModelStore(context.rankProfile().getSearch().sourceApplication(),
                                                   feature.getArguments());
            if (store.hasTensorFlowModels())
                return transformFromTensorFlowModel(store, context.rankProfile());
            else // is should have previously stored model information instead
                return transformFromStoredModel(store, context.rankProfile());
        }
        catch (IllegalArgumentException | UncheckedIOException e) {
            throw new IllegalArgumentException("Could not use tensorflow model from " + feature, e);
        }
    }

    private ExpressionNode transformFromTensorFlowModel(ModelStore store, RankProfile profile) {
        TensorFlowModel model = importedModels.computeIfAbsent(store.arguments().modelPath(),
                                                               k -> tensorFlowImporter.importModel(store.tensorFlowModelDir()));

        // Find the specified expression
        Signature signature = chooseSignature(model, store.arguments().signature());
        String output = chooseOutput(signature, store.arguments().output());
        RankingExpression expression = model.expressions().get(output);
        store.writeConverted(expression);

        model.constants().forEach((k, v) -> transformConstant(store, profile, k, v));
        return expression.getRoot();
    }

    private ExpressionNode transformFromStoredModel(ModelStore store, RankProfile profile) {
        for (RankingConstant constant : store.readRankingConstants()) {
            if ( ! profile.getSearch().getRankingConstants().containsKey(constant.getName()))
                profile.getSearch().addRankingConstant(constant);
        }
        return store.readConverted().getRoot();
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

    private void transformConstant(ModelStore store, RankProfile profile, String constantName, Tensor constantValue) {
        Path constantPath = store.writeConstant(constantName, constantValue);

        if ( ! profile.getSearch().getRankingConstants().containsKey(constantName))
            profile.getSearch().addRankingConstant(new RankingConstant(constantName, constantValue.type(),
                                                                       constantPath.toString()));
    }

    private String skippedOutputsDescription(TensorFlowModel.Signature signature) {
        if (signature.skippedOutputs().isEmpty()) return "";
        StringBuilder b = new StringBuilder(": ");
        signature.skippedOutputs().forEach((k, v) -> b.append("Skipping output '").append(k).append("': ").append(v));
        return b.toString();
    }

    /**
     * Provides read/write access to the correct directories of the application package given by the feature arguments
     */
    private static class ModelStore {

        private final ApplicationPackage application;
        private final FeatureArguments arguments;

        public ModelStore(ApplicationPackage application, Arguments arguments) {
            this.application = application;
            this.arguments = new FeatureArguments(arguments);
        }

        public FeatureArguments arguments() { return arguments; }

        public boolean hasTensorFlowModels() {
            try {
                return application.getFileReference(ApplicationPackage.MODELS_DIR).exists();
            }
            catch (UnsupportedOperationException e) {
                return false; // No files -> no TensorFlow models
            }
        }

        /**
         * Returns the directory which (if hasTensorFlowModels is true)
         * contains the source model to use for these arguments
         */
        public File tensorFlowModelDir() {
            return application.getFileReference(ApplicationPackage.MODELS_DIR.append(arguments.modelPath()));
        }

        /**
         * Adds this expression to the application package, such that it can be read later.
         */
        public void writeConverted(RankingExpression expression) {
            log.info("Writing converted TensorFlow expression to " + arguments.expressionPath());
            application.getFile(arguments.expressionPath())
                       .writeFile(new StringReader(expression.getRoot().toString()));
        }

        /** Reads the previously stored ranking expression for these arguments */
        public RankingExpression readConverted() {
            try {
                log.info("Reading converted TensorFlow expression from " + arguments.expressionPath());
                return new RankingExpression(application.getFile(arguments.expressionPath()).createReader());
            }
            catch (IOException e) {
                throw new UncheckedIOException("Could not read " + arguments.expressionPath(), e);
            }
            catch (ParseException e) {
                throw new IllegalStateException("Could not parse " + arguments.expressionPath(), e);
            }
        }

        /**
         * Reads the information about all the constants stored in the application package
         * (the constant value itself is replicated with file distribution).
         */
        public List<RankingConstant> readRankingConstants() {
            try {
                List<RankingConstant> constants = new ArrayList<>();
                for (ApplicationFile constantFile : application.getFile(arguments.rankingConstantsPath()).listFiles()) {
                    String[] parts = IOUtils.readAll(constantFile.createReader()).split(":");
                    constants.add(new RankingConstant(parts[0], TensorType.fromSpec(parts[1]), parts[2]));
                }
                return constants;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Adds this constant to the application package as a file,
         * such that it can be distributed using file distribution.
         *
         * @return the path to the stored constant, relative to the application package root
         */
        public Path writeConstant(String name, Tensor constant) {
            Path constantsPath = ApplicationPackage.MODELS_GENERATED_DIR.append(arguments.modelPath).append("constants");

            // "tbf" ending for "typed binary format" - recognized by the nodes receiving the file:
            // Use an absolute path to the constant file to avoid writing it to the .preprocessed sub-directory
            // then attempting to read it from a context where the root is outside the .preprocessed directory.
            File constantFile = application.getFileReference(constantsPath.append(name + ".tbf")).getAbsoluteFile();

            // Remember the constant in a file we replicate in ZooKeeper
            log.info("Writing converted TensorFlow constant information to " + arguments.rankingConstantsPath().append(name + ".constant"));
            application.getFile(arguments.rankingConstantsPath().append(name + ".constant"))
                       .writeFile(new StringReader(name + ":" + constant.type() + ":" + constantFile));

            // Write content explicitly as a file on the file system as this is distributed using file distribution
            log.info("Writing converted TensorFlow constant to " + constantFile);
            createIfNeeded(constantsPath);
            IOUtils.writeFile(constantFile, TypedBinaryFormat.encode(constant));
            return Path.fromString(constantFile.toString());
        }

        private void createIfNeeded(Path path) {
            File dir = application.getFileReference(path);
            if ( ! dir.exists()) {
                if (!dir.mkdirs())
                    throw new IllegalStateException("Could not create " + dir);
            }
        }

    }

    /** Encapsulates the 1, 2 or 3 arguments to a tensorflow feature */
    private static class FeatureArguments {

        private final Path modelPath;

        /** Optional arguments */
        private final Optional<String> signature, output;

        public FeatureArguments(Arguments arguments) {
            if (arguments.isEmpty())
                throw new IllegalArgumentException("A tensorflow node must take an argument pointing to " +
                                                   "the tensorflow model directory under [application]/models");
            if (arguments.expressions().size() > 3)
                throw new IllegalArgumentException("A tensorflow feature can have at most 3 arguments");

            modelPath = Path.fromString(asString(arguments.expressions().get(0)));
            signature = optionalArgument(1, arguments);
            output = optionalArgument(2, arguments);
        }

        /** Returns relative path to this model below the "models/" dir in the application package */
        public Path modelPath() { return modelPath; }
        public Optional<String> signature() { return signature; }
        public Optional<String> output() { return output; }

        public Path rankingConstantsPath() {
            return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(modelPath).append("constants");
        }

        public Path expressionPath() {
            return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR
                    .append(modelPath).append("expressions").append(expressionFileName());
        }

        private String expressionFileName() {
            StringBuilder fileName = new StringBuilder();
            signature.ifPresent(s -> fileName.append(s).append("."));
            output.ifPresent(s -> fileName.append(s).append("."));
            if (fileName.length() == 0) // single signature and output
                fileName.append("single.");
            fileName.append("expression");
            return fileName.toString();
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

}
