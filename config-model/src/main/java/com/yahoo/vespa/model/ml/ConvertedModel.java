// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.ml;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlFunction;
import com.google.common.collect.ImmutableMap;
import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.FeatureNames;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchdefinition.expressiontransforms.RankProfileTransformContext;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A machine learned model imported from the models/ directory in the application package, for a single rank profile.
 * This encapsulates the difference between reading a model
 * - from a file application package, where it is represented by an ImportedModel, and
 * - from a ZK application package, where the models/ directory is unavailable and models are read from
 *   generated files stored in file distribution or ZooKeeper.
 *
 * @author bratseth
 */
public class ConvertedModel {

    private final ModelName modelName;
    private final String modelDescription;
    private final ImmutableMap<String, ExpressionFunction> expressions;

    /** The source importedModel, or empty if this was created from a stored converted model */
    private final Optional<ImportedMlModel> sourceModel;

    private ConvertedModel(ModelName modelName,
                           String modelDescription,
                           Map<String, ExpressionFunction> expressions,
                           Optional<ImportedMlModel> sourceModel) {
        this.modelName = modelName;
        this.modelDescription = modelDescription;
        this.expressions = ImmutableMap.copyOf(expressions);
        this.sourceModel = sourceModel;
    }

    /**
     * Create and store a converted model for a rank profile given from either an imported model,
     * or (if unavailable) from stored application package data.
     *
     * @param modelPath the path to the model
     * @param pathIsFile true if that path (this kind of model) is stored in a file, false if it is in a directory
     */
    public static ConvertedModel fromSourceOrStore(Path modelPath, boolean pathIsFile, RankProfileTransformContext context) {
        ImportedMlModel sourceModel = // TODO: Convert to name here, make sure its done just one way
                context.importedModels().get(sourceModelFile(context.rankProfile().applicationPackage(), modelPath));
        ModelName modelName = new ModelName(context.rankProfile().getName(), modelPath, pathIsFile);

        if (sourceModel == null && ! new ModelStore(context.rankProfile().applicationPackage(), modelName).exists())
            throw new IllegalArgumentException("No model '" + modelPath + "' is available. Available models: " +
                                               context.importedModels().all().stream().map(ImportedMlModel::source).collect(Collectors.joining(", ")));

        if (sourceModel != null) {
            return fromSource(modelName,
                              modelPath.toString(),
                              context.rankProfile(),
                              context.queryProfiles(),
                              sourceModel);
        }
        else {
            return fromStore(modelName,
                             modelPath.toString(),
                             context.rankProfile());
        }
    }

    public static ConvertedModel fromSource(ModelName modelName,
                                            String modelDescription,
                                            RankProfile rankProfile,
                                            QueryProfileRegistry queryProfileRegistry,
                                            ImportedMlModel importedModel) {
        try {
            ModelStore modelStore = new ModelStore(rankProfile.applicationPackage(), modelName);
            return new ConvertedModel(modelName,
                                      modelDescription,
                                      convertAndStore(importedModel, rankProfile, queryProfileRegistry, modelStore),
                                      Optional.of(importedModel));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("In " + rankProfile + ": Could not create model '" + modelName +
                                               " (" + modelDescription + ")", e);
        }
    }

    public static ConvertedModel fromStore(ModelName modelName,
                                           String modelDescription,
                                           RankProfile rankProfile) {
        try {
            ModelStore modelStore = new ModelStore(rankProfile.applicationPackage(), modelName);
            return new ConvertedModel(modelName,
                                      modelDescription,
                                      convertStored(modelStore, rankProfile),
                                      Optional.empty());
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("In " + rankProfile + ": Could not create model '" + modelName +
                                               " (" + modelDescription + ")", e);
        }
    }

    /**
     * Returns all the output expressions of this indexed by name. The names consist of one or two parts
     * separated by dot, where the first part is the signature name
     * if signatures are used, or the expression name if signatures are not used and there are multiple
     * expressions, and the second is the output name if signature names are used.
     */
    public Map<String, ExpressionFunction> expressions() { return expressions; }

    /**
     * Returns the expression matching the given arguments.
     */
    public ExpressionNode expression(FeatureArguments arguments, RankProfileTransformContext context) {
        ExpressionFunction expression = selectExpression(arguments);
        if (sourceModel.isPresent() && context != null) // we should verify
            verifyInputs(expression.getBody(), sourceModel.get(), context.rankProfile(), context.queryProfiles());
        return expression.getBody().getRoot();
    }

    private ExpressionFunction selectExpression(FeatureArguments arguments) {
        if (expressions.isEmpty())
            throw new IllegalArgumentException("No expressions available in " + this);

        ExpressionFunction expression = expressions.get(arguments.toName());
        if (expression != null) return expression;

        expression = expressions.get("default." + arguments.toName());
        if (expression != null) return expression;

        if (arguments.signature().isEmpty()) {
            if (expressions.size() > 1)
                throw new IllegalArgumentException("Multiple candidate expressions " + missingExpressionMessageSuffix());
            return expressions.values().iterator().next();
        }

        if (arguments.output().isEmpty()) {
            List<Map.Entry<String, ExpressionFunction>> entriesWithTheRightPrefix =
                    expressions.entrySet().stream().filter(entry -> entry.getKey().startsWith(arguments.signature().get() + ".")).collect(Collectors.toList());
            if (entriesWithTheRightPrefix.size() < 1)
                throw new IllegalArgumentException("No expressions named '" + arguments.signature().get() +
                                                   missingExpressionMessageSuffix());
            if (entriesWithTheRightPrefix.size() > 1)
                throw new IllegalArgumentException("Multiple candidate expression named '" + arguments.signature().get() +
                                                   missingExpressionMessageSuffix());
            return entriesWithTheRightPrefix.get(0).getValue();
        }

        throw new IllegalArgumentException("No expression '" + arguments.toName() + missingExpressionMessageSuffix());
    }

    private String missingExpressionMessageSuffix() {
        return "' in model '" + modelDescription + "'. " +
               "Available expressions: " + expressions.keySet().stream().collect(Collectors.joining(", "));
    }

    // ----------------------- Static model conversion/storage below here

    private static Map<String, ExpressionFunction> convertAndStore(ImportedMlModel model,
                                                                   RankProfile profile,
                                                                   QueryProfileRegistry queryProfiles,
                                                                   ModelStore store) {
        // Add constants
        Set<String> constantsReplacedByFunctions = new HashSet<>();
        model.smallConstants().forEach((k, v) -> transformSmallConstant(store, profile, k, v));
        model.largeConstants().forEach((k, v) -> transformLargeConstant(store, profile, queryProfiles,
                                                                        constantsReplacedByFunctions, k, v));

        // Add functions
        addGeneratedFunctions(model, profile);

        // Add expressions
        Map<String, ExpressionFunction> expressions = new HashMap<>();
        for (ImportedMlFunction outputFunction : model.outputExpressions()) {
            ExpressionFunction expression = asExpressionFunction(outputFunction);
            addExpression(expression, expression.getName(),
                          constantsReplacedByFunctions,
                          model, store, profile, queryProfiles,
                          expressions);
        }

        // Transform and save function - must come after reading expressions due to optimization transforms
        // and must use the function expression added to the profile, which may differ from the one saved in the model,
        // after rewrite
        model.functions().forEach((k, v) -> transformGeneratedFunction(store, constantsReplacedByFunctions, k,
                                                                       profile.getFunctions().get(k).function().getBody()));

        return expressions;
    }

    private static ExpressionFunction asExpressionFunction(ImportedMlFunction function) {
        try {
            Map<String, TensorType> argumentTypes = new HashMap<>();
            for (Map.Entry<String, String> entry : function.argumentTypes().entrySet())
                argumentTypes.put(entry.getKey(), TensorType.fromSpec(entry.getValue()));

            return new ExpressionFunction(function.name(),
                                          function.arguments(),
                                          new RankingExpression(function.expression()),
                                          argumentTypes,
                                          function.returnType().map(TensorType::fromSpec));
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Gor an illegal argument from importing " + function.name(), e);
        }
    }

    private static void addExpression(ExpressionFunction expression,
                                      String expressionName,
                                      Set<String> constantsReplacedByFunctions,
                                      ImportedMlModel model,
                                      ModelStore store,
                                      RankProfile profile,
                                      QueryProfileRegistry queryProfiles,
                                      Map<String, ExpressionFunction> expressions) {
        expression = expression.withBody(replaceConstantsByFunctions(expression.getBody(), constantsReplacedByFunctions));
        store.writeExpression(expressionName, expression);
        expressions.put(expressionName, expression);
    }

    private static Map<String, ExpressionFunction> convertStored(ModelStore store, RankProfile profile) {
        for (Pair<String, Tensor> constant : store.readSmallConstants())
            profile.addConstant(constant.getFirst(), asValue(constant.getSecond()));

        for (RankingConstant constant : store.readLargeConstants()) {
            if ( ! profile.rankingConstants().asMap().containsKey(constant.getName()))
                profile.rankingConstants().add(constant);
        }

        for (Pair<String, RankingExpression> function : store.readFunctions()) {
            addGeneratedFunctionToProfile(profile, function.getFirst(), function.getSecond());
        }

        return store.readExpressions();
    }

    private static void transformSmallConstant(ModelStore store, RankProfile profile, String constantName,
                                               String constantValueString) {
        Tensor constantValue = Tensor.from(constantValueString);
        store.writeSmallConstant(constantName, constantValue);
        profile.addConstant(constantName, asValue(constantValue));
    }

    private static void transformLargeConstant(ModelStore store,
                                               RankProfile profile,
                                               QueryProfileRegistry queryProfiles,
                                               Set<String> constantsReplacedByFunctions,
                                               String constantName,
                                               String constantValueString) {
        Tensor constantValue = Tensor.from(constantValueString);
        RankProfile.RankingExpressionFunction rankingExpressionFunctionOverridingConstant = profile.getFunctions().get(constantName);
        if (rankingExpressionFunctionOverridingConstant != null) {
            TensorType functionType = rankingExpressionFunctionOverridingConstant.function().getBody().type(profile.typeContext(queryProfiles));
            if ( ! constantValue.type().isAssignableTo(functionType))
                throw new IllegalArgumentException("Function '" + constantName + "' replaces the constant with this name. " +
                                                   typeMismatchExplanation(constantValue.type(), functionType));
            constantsReplacedByFunctions.add(constantName); // will replace constant(constantName) by constantName later
        }
        else {
            Path constantPath = store.writeLargeConstant(constantName, constantValue);
            if ( ! profile.rankingConstants().asMap().containsKey(constantName)) {
                profile.rankingConstants().add(new RankingConstant(constantName, constantValue.type(),
                                                                   constantPath.toString()));
            }
        }
    }

    private static void transformGeneratedFunction(ModelStore store,
                                                   Set<String> constantsReplacedByFunctions,
                                                   String functionName,
                                                   RankingExpression expression) {

        expression = replaceConstantsByFunctions(expression, constantsReplacedByFunctions);
        store.writeFunction(functionName, expression);
    }

    private static void addGeneratedFunctionToProfile(RankProfile profile, String functionName, RankingExpression expression) {
        if (profile.getFunctions().containsKey(functionName)) {
            if ( ! profile.getFunctions().get(functionName).function().getBody().equals(expression))
                throw new IllegalArgumentException("Generated function '" + functionName + "' already exists in " + profile +
                                                   " - with a different definition" +
                                                   ": Has\n" + profile.getFunctions().get(functionName).function().getBody() +
                                                   "\nwant to add " + expression + "\n");
            return;
        }
        profile.addFunction(new ExpressionFunction(functionName, expression), false);  // TODO: Inline if only used once
    }

    /**
     * Verify that the inputs declared in the given expression exists in the given rank profile as functions,
     * and return tensors of the correct types.
     */
    private static void verifyInputs(RankingExpression expression, ImportedMlModel model,
                                     RankProfile profile, QueryProfileRegistry queryProfiles) {
        Set<String> functionNames = new HashSet<>();
        addFunctionNamesIn(expression.getRoot(), functionNames, model);
        for (String functionName : functionNames) {
            Optional<TensorType> requiredType = model.inputTypeSpec(functionName).map(TensorType::fromSpec);
            if ( ! requiredType.isPresent()) continue; // Not a required function

            RankProfile.RankingExpressionFunction rankingExpressionFunction = profile.getFunctions().get(functionName);
            if (rankingExpressionFunction == null)
                throw new IllegalArgumentException("Model refers input '" + functionName +
                                                   "' of type " + requiredType.get() +
                                                   " but this function is not present in " + profile);
            // TODO: We should verify this in the (function reference(s) this is invoked (starting from first/second
            // phase and summary features), as it may only resolve correctly given those bindings
            // Or, probably better, annotate the functions with type constraints here and verify during general
            // type verification
            TensorType actualType = rankingExpressionFunction.function().getBody().getRoot().type(profile.typeContext(queryProfiles));
            if ( actualType == null)
                throw new IllegalArgumentException("Model refers input '" + functionName +
                                                   "' of type " + requiredType.get() +
                                                   " which must be produced by a function in the rank profile, but " +
                                                   "this function references a feature which is not declared");
            if ( ! actualType.isAssignableTo(requiredType.get()))
                throw new IllegalArgumentException("Model refers input '" + functionName + "'. " +
                                                   typeMismatchExplanation(requiredType.get(), actualType));
        }
    }

    private static String typeMismatchExplanation(TensorType requiredType, TensorType actualType) {
        return "The required type of this is " + requiredType + ", but this function returns " + actualType +
               (actualType.rank() == 0 ? ". This is often due to missing declaration of query tensor features " +
                                         "in query profile types - see the documentation."
                                       : "");
    }

    /** Add the generated functions to the rank profile */
    private static void addGeneratedFunctions(ImportedMlModel model, RankProfile profile) {
        model.functions().forEach((k, v) -> addGeneratedFunctionToProfile(profile, k, RankingExpression.from(v)));
    }

    /**
     * If a constant c is overridden by a function, we need to replace instances of "constant(c)" by "c" in expressions.
     * This method does that for the given expression and returns the result.
     */
    private static RankingExpression replaceConstantsByFunctions(RankingExpression expression,
                                                                 Set<String> constantsReplacedByFunctions) {
        if (constantsReplacedByFunctions.isEmpty()) return expression;
        return new RankingExpression(expression.getName(),
                                     replaceConstantsByFunctions(expression.getRoot(),
                                                                 constantsReplacedByFunctions));
    }

    private static ExpressionNode replaceConstantsByFunctions(ExpressionNode node, Set<String> constantsReplacedByFunctions) {
        if (node instanceof ReferenceNode) {
            Reference reference = ((ReferenceNode)node).reference();
            if (FeatureNames.isSimpleFeature(reference) && reference.name().equals("constant")) {
                String argument = reference.simpleArgument().get();
                if (constantsReplacedByFunctions.contains(argument))
                    return new ReferenceNode(argument);
            }
        }
        if (node instanceof CompositeNode) { // not else: this matches some of the same nodes as the outer if above
            CompositeNode composite = (CompositeNode)node;
            return composite.setChildren(composite.children().stream()
                                                  .map(child -> replaceConstantsByFunctions(child, constantsReplacedByFunctions))
                                                  .collect(Collectors.toList()));
        }
        return node;
    }

    private static void addFunctionNamesIn(ExpressionNode node, Set<String> names, ImportedMlModel model) {
        if (node instanceof ReferenceNode) {
            ReferenceNode referenceNode = (ReferenceNode)node;
            if (referenceNode.getOutput() == null) { // function references cannot specify outputs
                if (names.add(referenceNode.getName())) {
                    if (model.functions().containsKey(referenceNode.getName())) {
                        addFunctionNamesIn(RankingExpression.from(model.functions().get(referenceNode.getName())).getRoot(), names, model);
                    }
                }
            }
        }
        else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children())
                addFunctionNamesIn(child, names, model);
        }
    }

    private static Value asValue(Tensor tensor) {
        if (tensor.type().rank() == 0)
            return new DoubleValue(tensor.asDouble()); // the backend gets offended by dimensionless tensors
        else
            return new TensorValue(tensor);
    }

    @Override
    public String toString() { return "model '" + modelName + "'"; }

    /**
     * Returns the directory which contains the source model to use for these arguments
     */
    public static File sourceModelFile(ApplicationPackage application, Path sourceModelPath) {
        return application.getFileReference(ApplicationPackage.MODELS_DIR.append(sourceModelPath));
    }

    /**
     * Provides read/write access to the correct directories of the application package given by the feature arguments
     */
    static class ModelStore {

        private final ApplicationPackage application;
        private final ModelFiles modelFiles;

        ModelStore(ApplicationPackage application, ModelName modelName) {
            this.application = application;
            this.modelFiles = new ModelFiles(modelName);
        }

        /** Returns whether a model store for this application and model name exists */
        public boolean exists() {
            return application.getFile(modelFiles.storedModelReplicatedPath()).exists();
        }

        /**
         * Adds this expression to the application package, such that it can be read later.
         *
         * @param name the name of this ranking expression - may have 1-3 parts separated by dot where the first part
         *             is always the model name
         */
        void writeExpression(String name, ExpressionFunction expression) {
            StringBuilder b = new StringBuilder(expression.getBody().getRoot().toString());
            for (Map.Entry<String, TensorType> input : expression.argumentTypes().entrySet())
                b.append('\n').append(input.getKey()).append('\t').append(input.getValue());
            application.getFile(modelFiles.expressionPath(name)).writeFile(new StringReader(b.toString()));
        }

        Map<String, ExpressionFunction> readExpressions() {
            Map<String, ExpressionFunction> expressions = new HashMap<>();
            ApplicationFile expressionPath = application.getFile(modelFiles.expressionsPath());
            if ( ! expressionPath.exists() || ! expressionPath.isDirectory()) return Collections.emptyMap();
            for (ApplicationFile expressionFile : expressionPath.listFiles()) {
                try (BufferedReader reader = new BufferedReader(expressionFile.createReader())){
                    String name = expressionFile.getPath().getName();
                    expressions.put(name, readExpression(name, reader));
                }
                catch (IOException e) {
                    throw new UncheckedIOException("Failed reading " + expressionFile.getPath(), e);
                }
                catch (ParseException e) {
                    throw new IllegalStateException("Invalid stored expression in " + expressionFile, e);
                }
            }
            return expressions;
        }

        private ExpressionFunction readExpression(String name, BufferedReader reader)
                throws IOException, ParseException {
            // First line is expression
            RankingExpression expression = new RankingExpression(name, reader.readLine());
            // Next lines are inputs on the format name\ttensorTypeSpec
            Map<String, TensorType> inputs = new LinkedHashMap<>();
            String line;
            while (null != (line = reader.readLine())) {
                String[] parts = line.split("\t");
                inputs.put(parts[0], TensorType.fromSpec(parts[1]));
            }
            return new ExpressionFunction(name, new ArrayList<>(inputs.keySet()), expression, inputs, Optional.empty());
        }

        /** Adds this function expression to the application package so it can be read later. */
        public void writeFunction(String name, RankingExpression expression) {
            application.getFile(modelFiles.functionsPath()).appendFile(name + "\t" +
                                                                       expression.getRoot().toString() + "\n");
        }

        /** Reads the previously stored function expressions for these arguments */
        List<Pair<String, RankingExpression>> readFunctions() {
            try {
                ApplicationFile file = application.getFile(modelFiles.functionsPath());
                if ( ! file.exists()) return Collections.emptyList();

                List<Pair<String, RankingExpression>> functions = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(file.createReader())) {
                    String line;
                    while (null != (line = reader.readLine())) {
                        String[] parts = line.split("\t");
                        String name = parts[0];
                        try {
                            RankingExpression expression = new RankingExpression(parts[0], parts[1]);
                            functions.add(new Pair<>(name, expression));
                        } catch (ParseException e) {
                            throw new IllegalStateException("Could not parse " + name, e);
                        }
                    }
                    return functions;
                }
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Reads the information about all the large (aka ranking) constants stored in the application package
         * (the constant value itself is replicated with file distribution).
         */
        List<RankingConstant> readLargeConstants() {
            try {
                List<RankingConstant> constants = new ArrayList<>();
                for (ApplicationFile constantFile : application.getFile(modelFiles.largeConstantsInfoPath()).listFiles()) {
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
        Path writeLargeConstant(String name, Tensor constant) {
            Path constantsPath = modelFiles.largeConstantsContentPath();

            // "tbf" ending for "typed binary format" - recognized by the nodes receiving the file:
            Path constantPath = constantsPath.append(name + ".tbf");

            // Remember the constant in a file we replicate in ZooKeeper
            application.getFile(modelFiles.largeConstantsInfoPath().append(name + ".constant"))
                       .writeFile(new StringReader(name + ":" + constant.type() + ":" + correct(constantPath)));

            // Write content explicitly as a file on the file system as this is distributed using file distribution
            // - but only if this is a global model to avoid writing the same constants for each rank profile
            //   where they are used
            if (modelFiles.modelName.isGlobal()) {
                createIfNeeded(constantsPath);
                IOUtils.writeFile(application.getFileReference(constantPath), TypedBinaryFormat.encode(constant));
            }
            return correct(constantPath);
        }

        private List<Pair<String, Tensor>> readSmallConstants() {
            try {
                ApplicationFile file = application.getFile(modelFiles.smallConstantsPath());
                if ( ! file.exists()) return Collections.emptyList();

                List<Pair<String, Tensor>> constants = new ArrayList<>();
                BufferedReader reader = new BufferedReader(file.createReader());
                String line;
                while (null != (line = reader.readLine())) {
                    String[] parts = line.split("\t");
                    String name = parts[0];
                    TensorType type = TensorType.fromSpec(parts[1]);
                    Tensor tensor = Tensor.from(type, parts[2]);
                    constants.add(new Pair<>(name, tensor));
                }
                return constants;
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Append this constant to the single file used for small constants distributed as config
         */
        public void writeSmallConstant(String name, Tensor constant) {
            // Secret file format for remembering constants:
            application.getFile(modelFiles.smallConstantsPath()).appendFile(name + "\t" +
                                                                            constant.type().toString() + "\t" +
                                                                            constant.toString() + "\n");
        }

        /** Workaround for being constructed with the .preprocessed dir as root while later being used outside it */
        private Path correct(Path path) {
            if (application.getFileReference(Path.fromString("")).getAbsolutePath().endsWith(FilesApplicationPackage.preprocessed)
                && ! path.elements().contains(FilesApplicationPackage.preprocessed)) {
                return Path.fromString(FilesApplicationPackage.preprocessed).append(path);
            }
            else {
                return path;
            }
        }

        private void createIfNeeded(Path path) {
            File dir = application.getFileReference(path);
            if ( ! dir.exists()) {
                if (!dir.mkdirs())
                    throw new IllegalStateException("Could not create " + dir);
            }
        }

    }

    static class ModelFiles {

        ModelName modelName;

        public ModelFiles(ModelName modelName) {
            this.modelName = modelName;
        }

        /** Files stored below this path will be replicated in zookeeper */
        public Path storedModelReplicatedPath() {
            return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(modelName.fullName());
        }

        /**
         * Files stored below this path will not be replicated in zookeeper.
         * Large constants are only stored under the global (not rank-profile-specific)
         * path to avoid storing the same large constant multiple times.
         */
        public Path storedGlobalModelPath() {
            return ApplicationPackage.MODELS_GENERATED_DIR.append(modelName.localName());
        }

        public Path expressionPath(String name) {
            return expressionsPath().append(name);
        }

        public Path expressionsPath() {
            return storedModelReplicatedPath().append("expressions");
        }

        public Path smallConstantsPath() {
            return storedModelReplicatedPath().append("constants.txt");
        }

        /** Path to the large (ranking) constants directory */
        public Path largeConstantsContentPath() {
            return storedGlobalModelPath().append("constants");
        }

        /** Path to the large (ranking) constants directory */
        public Path largeConstantsInfoPath() {
            return storedModelReplicatedPath().append("constants");
        }

        /** Path to the functions file */
        public Path functionsPath() {
            return storedModelReplicatedPath().append("functions.txt");
        }

    }

}
