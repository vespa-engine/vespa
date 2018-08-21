package com.yahoo.searchdefinition.expressiontransforms;

import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.FeatureNames;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankingConstant;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.ImportedModel;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.tensor.serialization.TypedBinaryFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    private final String modelName;
    private final Path modelPath;

    /**
     * The ranking expressions of this, indexed by their name. which is a 1-3 part string separated by dots
     * where the first part is always the model name, the second the signature or (if none)
     * expression name (if more than one), and the third is the output name (if any).
     */
    private final Map<String, RankingExpression> expressions;

    /**
     * Create a converted model for a rank profile given from either an imported model,
     * or (if unavailable) from stored application package data.
     */
    public ConvertedModel(Path modelPath,
                          RankProfileTransformContext context,
                          ImportedModels importedModels) {
        this.modelPath = modelPath;
        this.modelName = toModelName(modelPath);
        ModelStore store = new ModelStore(context.rankProfile().getSearch().sourceApplication(), modelPath);
        if ( store.hasSourceModel())
            expressions = convertModel(store, context.rankProfile(), context.queryProfiles(), importedModels);
        else
            expressions = transformFromStoredModel(store, context.rankProfile());
    }

    private Map<String, RankingExpression> convertModel(ModelStore store,
                                                        RankProfile profile,
                                                        QueryProfileRegistry queryProfiles,
                                                        ImportedModels importedModels) {
        ImportedModel model = importedModels.imported(store.modelFiles.modelName(), store.sourceModelDir());
        return transformFromImportedModel(model, store, profile, queryProfiles);
    }

    /** Returns the expression matching the given arguments */
    public ExpressionNode expression(FeatureArguments arguments) {
        if (expressions.isEmpty())
            throw new IllegalArgumentException("No expressions available in " + this);

        RankingExpression expression = expressions.get(arguments.toName());
        if (expression != null) return expression.getRoot();

        if ( ! arguments.signature().isPresent()) {
            if (expressions.size() > 1)
                throw new IllegalArgumentException("Multiple candidate expressions " + missingExpressionMessageSuffix());
            return expressions.values().iterator().next().getRoot();
        }

        if ( ! arguments.output().isPresent()) {
            List<Map.Entry<String, RankingExpression>> entriesWithTheRightPrefix =
                    expressions.entrySet().stream().filter(entry -> entry.getKey().startsWith(modelName + "." + arguments.signature().get() + ".")).collect(Collectors.toList());
            if (entriesWithTheRightPrefix.size() < 1)
                throw new IllegalArgumentException("No expressions named '" + arguments.signature().get() +
                                                   missingExpressionMessageSuffix());
            if (entriesWithTheRightPrefix.size() > 1)
                throw new IllegalArgumentException("Multiple candidate expression named '" + arguments.signature().get() +
                                                   missingExpressionMessageSuffix());
            return entriesWithTheRightPrefix.get(0).getValue().getRoot();
        }

        throw new IllegalArgumentException("No expression '" + arguments.toName() + missingExpressionMessageSuffix());
    }

    private String missingExpressionMessageSuffix() {
        return "' in model '" + this.modelPath + "'. " +
               "Available expressions: " + expressions.keySet().stream().collect(Collectors.joining(", "));
    }

    private Map<String, RankingExpression> transformFromImportedModel(ImportedModel model,
                                                                      ModelStore store,
                                                                      RankProfile profile,
                                                                      QueryProfileRegistry queryProfiles) {
        // Add constants
        Set<String> constantsReplacedByMacros = new HashSet<>();
        model.smallConstants().forEach((k, v) -> transformSmallConstant(store, profile, k, v));
        model.largeConstants().forEach((k, v) -> transformLargeConstant(store, profile, queryProfiles,
                                                                        constantsReplacedByMacros, k, v));

        // Add macros
        addGeneratedMacros(model, profile);

        // Add expressions
        Map<String, RankingExpression> expressions = new HashMap<>();
        for (Map.Entry<String, ImportedModel.Signature> signatureEntry : model.signatures().entrySet()) {
            for (Map.Entry<String, String> outputEntry : signatureEntry.getValue().outputs().entrySet()) {
                addExpression(model.expressions().get(outputEntry.getValue()),
                              modelName + "." + signatureEntry.getKey() + "." + outputEntry.getKey(),
                              constantsReplacedByMacros,
                              model, store, profile, queryProfiles,
                              expressions);
            }
            if (signatureEntry.getValue().outputs().isEmpty()) { // fallback: Signature without outputs
                addExpression(model.expressions().get(signatureEntry.getKey()),
                              modelName + "." + signatureEntry.getKey(),
                              constantsReplacedByMacros,
                              model, store, profile, queryProfiles,
                              expressions);
            }
        }
        if (model.signatures().isEmpty()) { // fallback: Model without signatures
            if (model.expressions().size() == 1) { // Use just model name
                addExpression(model.expressions().values().iterator().next(),
                              modelName,
                              constantsReplacedByMacros,
                              model, store, profile, queryProfiles,
                              expressions);
            }
            else {
                for (Map.Entry<String, RankingExpression> expressionEntry : model.expressions().entrySet()) {
                    addExpression(expressionEntry.getValue(),
                                  modelName + "." + expressionEntry.getKey(),
                                  constantsReplacedByMacros,
                                  model, store, profile, queryProfiles,
                                  expressions);
                }
            }
        }

        // Transform and save macro - must come after reading expressions due to optimization transforms
        // and must use the macro expression added to the profile, which may differ from the one saved in the model,
        // after rewrite
        model.macros().forEach((k, v) -> transformGeneratedMacro(store, constantsReplacedByMacros, k,
                                                                 profile.getMacros().get(k).getRankingExpression()));

        return expressions;
    }

    private void addExpression(RankingExpression expression,
                               String expressionName,
                               Set<String> constantsReplacedByMacros,
                               ImportedModel model,
                               ModelStore store,
                               RankProfile profile,
                               QueryProfileRegistry queryProfiles,
                               Map<String, RankingExpression> expressions) {
        expression = replaceConstantsByMacros(expression, constantsReplacedByMacros);
        verifyRequiredMacros(expression, model, profile, queryProfiles);
        reduceBatchDimensions(expression, model, profile, queryProfiles);
        store.writeExpression(expressionName, expression);
        expressions.put(expressionName, expression);
    }

    private Map<String, RankingExpression> transformFromStoredModel(ModelStore store, RankProfile profile) {
        for (Pair<String, Tensor> constant : store.readSmallConstants())
            profile.addConstant(constant.getFirst(), asValue(constant.getSecond()));

        for (RankingConstant constant : store.readLargeConstants()) {
            if ( ! profile.getSearch().getRankingConstants().containsKey(constant.getName()))
                profile.getSearch().addRankingConstant(constant);
        }

        for (Pair<String, RankingExpression> macro : store.readMacros()) {
            addGeneratedMacroToProfile(profile, macro.getFirst(), macro.getSecond());
        }

        return store.readExpressions();
    }

    private void transformSmallConstant(ModelStore store, RankProfile profile, String constantName, Tensor constantValue) {
        store.writeSmallConstant(constantName, constantValue);
        profile.addConstant(constantName, asValue(constantValue));
    }

    private void transformLargeConstant(ModelStore store, RankProfile profile, QueryProfileRegistry queryProfiles,
                                        Set<String> constantsReplacedByMacros,
                                        String constantName, Tensor constantValue) {
        RankProfile.Macro macroOverridingConstant = profile.getMacros().get(constantName);
        if (macroOverridingConstant != null) {
            TensorType macroType = macroOverridingConstant.getRankingExpression().type(profile.typeContext(queryProfiles));
            if ( ! macroType.equals(constantValue.type()))
                throw new IllegalArgumentException("Macro '" + constantName + "' replaces the constant with this name. " +
                                                   typeMismatchExplanation(constantValue.type(), macroType));
            constantsReplacedByMacros.add(constantName); // will replace constant(constantName) by constantName later
        }
        else {
            Path constantPath = store.writeLargeConstant(constantName, constantValue);
            if ( ! profile.getSearch().getRankingConstants().containsKey(constantName)) {
                profile.getSearch().addRankingConstant(new RankingConstant(constantName, constantValue.type(),
                                                                           constantPath.toString()));
            }
        }
    }

    private void transformGeneratedMacro(ModelStore store,
                                         Set<String> constantsReplacedByMacros,
                                         String macroName,
                                         RankingExpression expression) {

        expression = replaceConstantsByMacros(expression, constantsReplacedByMacros);
        store.writeMacro(macroName, expression);
    }

    private void addGeneratedMacroToProfile(RankProfile profile, String macroName, RankingExpression expression) {
        if (profile.getMacros().containsKey(macroName)) {
            if ( ! profile.getMacros().get(macroName).getRankingExpression().equals(expression))
                throw new IllegalArgumentException("Generated macro '" + macroName + "' already exists in " + profile +
                                                   " - with a different definition");
            return;
        }
        profile.addMacro(macroName, false);  // todo: inline if only used once
        RankProfile.Macro macro = profile.getMacros().get(macroName);
        macro.setRankingExpression(expression);
        macro.setTextualExpression(expression.getRoot().toString());
    }

    /**
     * Verify that the macros referred in the given expression exists in the given rank profile,
     * and return tensors of the types specified in requiredMacros.
     */
    private void verifyRequiredMacros(RankingExpression expression, ImportedModel model,
                                      RankProfile profile, QueryProfileRegistry queryProfiles) {
        Set<String> macroNames = new HashSet<>();
        addMacroNamesIn(expression.getRoot(), macroNames, model);
        for (String macroName : macroNames) {
            TensorType requiredType = model.requiredMacros().get(macroName);
            if (requiredType == null) continue; // Not a required macro

            RankProfile.Macro macro = profile.getMacros().get(macroName);
            if (macro == null)
                throw new IllegalArgumentException("Model refers input '" + macroName +
                                                   "' of type " + requiredType + " but this macro is not present in " +
                                                   profile);
            // TODO: We should verify this in the (function reference(s) this is invoked (starting from first/second
            // phase and summary features), as it may only resolve correctly given those bindings
            // Or, probably better, annotate the macros with type constraints here and verify during general
            // type verification
            TensorType actualType = macro.getRankingExpression().getRoot().type(profile.typeContext(queryProfiles));
            if ( actualType == null)
                throw new IllegalArgumentException("Model refers input '" + macroName +
                                                   "' of type " + requiredType +
                                                   " which must be produced by a macro in the rank profile, but " +
                                                   "this macro references a feature which is not declared");
            if ( ! actualType.isAssignableTo(requiredType))
                throw new IllegalArgumentException("Model refers input '" + macroName + "'. " +
                                                   typeMismatchExplanation(requiredType, actualType));
        }
    }

    private String typeMismatchExplanation(TensorType requiredType, TensorType actualType) {
        return "The required type of this is " + requiredType + ", but this macro returns " + actualType +
               (actualType.rank() == 0 ? ". This is often due to missing declaration of query tensor features " +
                                         "in query profile types - see the documentation."
                                       : "");
    }

    /**
     * Add the generated macros to the rank profile
     */
    private void addGeneratedMacros(ImportedModel model, RankProfile profile) {
        model.macros().forEach((k, v) -> addGeneratedMacroToProfile(profile, k, v.copy()));
    }

    /**
     * Check if batch dimensions of inputs can be reduced out. If the input
     * macro specifies that a single exemplar should be evaluated, we can
     * reduce the batch dimension out.
     */
    private void reduceBatchDimensions(RankingExpression expression, ImportedModel model,
                                       RankProfile profile, QueryProfileRegistry queryProfiles) {
        TypeContext<Reference> typeContext = profile.typeContext(queryProfiles);
        TensorType typeBeforeReducing = expression.getRoot().type(typeContext);

        // Check generated macros for inputs to reduce
        Set<String> macroNames = new HashSet<>();
        addMacroNamesIn(expression.getRoot(), macroNames, model);
        for (String macroName : macroNames) {
            if ( ! model.macros().containsKey(macroName)) continue;

            RankProfile.Macro macro = profile.getMacros().get(macroName);
            if (macro == null) {
                throw new IllegalArgumentException("Model refers to generated macro '" + macroName +
                                                   "but this macro is not present in " + profile);
            }
            RankingExpression macroExpression = macro.getRankingExpression();
            macroExpression.setRoot(reduceBatchDimensionsAtInput(macroExpression.getRoot(), model, typeContext));
        }

        // Check expression for inputs to reduce
        ExpressionNode root = expression.getRoot();
        root = reduceBatchDimensionsAtInput(root, model, typeContext);
        TensorType typeAfterReducing = root.type(typeContext);
        root = expandBatchDimensionsAtOutput(root, typeBeforeReducing, typeAfterReducing);
        expression.setRoot(root);
    }

    private ExpressionNode reduceBatchDimensionsAtInput(ExpressionNode node, ImportedModel model,
                                                        TypeContext<Reference> typeContext) {
        if (node instanceof TensorFunctionNode) {
            TensorFunction tensorFunction = ((TensorFunctionNode) node).function();
            if (tensorFunction instanceof Rename) {
                List<ExpressionNode> children = ((TensorFunctionNode)node).children();
                if (children.size() == 1 && children.get(0) instanceof ReferenceNode) {
                    ReferenceNode referenceNode = (ReferenceNode) children.get(0);
                    if (model.requiredMacros().containsKey(referenceNode.getName())) {
                        return reduceBatchDimensionExpression(tensorFunction, typeContext);
                    }
                }
            }
        }
        if (node instanceof ReferenceNode) {
            ReferenceNode referenceNode = (ReferenceNode) node;
            if (model.requiredMacros().containsKey(referenceNode.getName())) {
                return reduceBatchDimensionExpression(TensorFunctionNode.wrapArgument(node), typeContext);
            }
        }
        if (node instanceof CompositeNode) {
            List<ExpressionNode> children = ((CompositeNode)node).children();
            List<ExpressionNode> transformedChildren = new ArrayList<>(children.size());
            for (ExpressionNode child : children) {
                transformedChildren.add(reduceBatchDimensionsAtInput(child, model, typeContext));
            }
            return ((CompositeNode)node).setChildren(transformedChildren);
        }
        return node;
    }

    private ExpressionNode reduceBatchDimensionExpression(TensorFunction function, TypeContext<Reference> context) {
        TensorFunction result = function;
        TensorType type = function.type(context);
        if (type.dimensions().size() > 1) {
            List<String> reduceDimensions = new ArrayList<>();
            for (TensorType.Dimension dimension : type.dimensions()) {
                if (dimension.size().orElse(-1L) == 1) {
                    reduceDimensions.add(dimension.name());
                }
            }
            if (reduceDimensions.size() > 0) {
                result = new Reduce(function, Reduce.Aggregator.sum, reduceDimensions);
            }
        }
        return new TensorFunctionNode(result);
    }

    /**
     * If batch dimensions have been reduced away above, bring them back here
     * for any following computation of the tensor.
     */
    // TODO: determine when this is not necessary!
    private ExpressionNode expandBatchDimensionsAtOutput(ExpressionNode node, TensorType before, TensorType after) {
        if (after.equals(before)) {
            return node;
        }
        TensorType.Builder typeBuilder = new TensorType.Builder();
        for (TensorType.Dimension dimension : before.dimensions()) {
            if (dimension.size().orElse(-1L) == 1 && !after.dimensionNames().contains(dimension.name())) {
                typeBuilder.indexed(dimension.name(), 1);
            }
        }
        TensorType expandDimensionsType = typeBuilder.build();
        if (expandDimensionsType.dimensions().size() > 0) {
            ExpressionNode generatedExpression = new ConstantNode(new DoubleValue(1.0));
            Generate generatedFunction = new Generate(expandDimensionsType,
                                                      new GeneratorLambdaFunctionNode(expandDimensionsType,
                                                                                      generatedExpression)
                                                              .asLongListToDoubleOperator());
            Join expand = new Join(TensorFunctionNode.wrapArgument(node), generatedFunction, ScalarFunctions.multiply());
            return new TensorFunctionNode(expand);
        }
        return node;
    }

    /**
     * If a constant c is overridden by a macro, we need to replace instances of "constant(c)" by "c" in expressions.
     * This method does that for the given expression and returns the result.
     */
    private RankingExpression replaceConstantsByMacros(RankingExpression expression,
                                                       Set<String> constantsReplacedByMacros) {
        if (constantsReplacedByMacros.isEmpty()) return expression;
        return new RankingExpression(expression.getName(),
                                     replaceConstantsByMacros(expression.getRoot(), constantsReplacedByMacros));
    }

    private ExpressionNode replaceConstantsByMacros(ExpressionNode node, Set<String> constantsReplacedByMacros) {
        if (node instanceof ReferenceNode) {
            Reference reference = ((ReferenceNode)node).reference();
            if (FeatureNames.isSimpleFeature(reference) && reference.name().equals("constant")) {
                String argument = reference.simpleArgument().get();
                if (constantsReplacedByMacros.contains(argument))
                    return new ReferenceNode(argument);
            }
        }
        if (node instanceof CompositeNode) { // not else: this matches some of the same nodes as the outer if above
            CompositeNode composite = (CompositeNode)node;
            return composite.setChildren(composite.children().stream()
                                                  .map(child -> replaceConstantsByMacros(child, constantsReplacedByMacros))
                                                  .collect(Collectors.toList()));
        }
        return node;
    }

    private void addMacroNamesIn(ExpressionNode node, Set<String> names, ImportedModel model) {
        if (node instanceof ReferenceNode) {
            ReferenceNode referenceNode = (ReferenceNode)node;
            if (referenceNode.getOutput() == null) { // macro references cannot specify outputs
                names.add(referenceNode.getName());
                if (model.macros().containsKey(referenceNode.getName())) {
                    addMacroNamesIn(model.macros().get(referenceNode.getName()).getRoot(), names, model);
                }
            }
        }
        else if (node instanceof CompositeNode) {
            for (ExpressionNode child : ((CompositeNode)node).children())
                addMacroNamesIn(child, names, model);
        }
    }

    private Value asValue(Tensor tensor) {
        if (tensor.type().rank() == 0)
            return new DoubleValue(tensor.asDouble()); // the backend gets offended by dimensionless tensors
        else
            return new TensorValue(tensor);
    }

    private static String toModelName(Path modelPath) {
        return modelPath.toString().replace("/", "_");
    }

    @Override
    public String toString() { return "model '" + modelName + "'"; }

    /**
     * Provides read/write access to the correct directories of the application package given by the feature arguments
     */
    static class ModelStore {

        private final ApplicationPackage application;
        private final ModelFiles modelFiles;

        ModelStore(ApplicationPackage application, Path modelPath) {
            this.application = application;
            this.modelFiles = new ModelFiles(modelPath);
        }

        public boolean hasSourceModel() {
            return sourceModelDir().exists();
        }

        /**
         * Returns the directory which contains the source model to use for these arguments
         */
        public File sourceModelDir() {
            return application.getFileReference(ApplicationPackage.MODELS_DIR.append(modelFiles.modelPath()));
        }

        /**
         * Adds this expression to the application package, such that it can be read later.
         *
         * @param name the name of this ranking expression - may have 1-3 parts separated by dot where the first part
         *             is always the model name
         */
        void writeExpression(String name, RankingExpression expression) {
            application.getFile(modelFiles.expressionPath(name))
                       .writeFile(new StringReader(expression.getRoot().toString()));
        }

        Map<String, RankingExpression> readExpressions() {
            Map<String, RankingExpression> expressions = new HashMap<>();
            ApplicationFile expressionPath = application.getFile(modelFiles.expressionsPath());
            if ( ! expressionPath.exists() || ! expressionPath.isDirectory()) return Collections.emptyMap();
            for (ApplicationFile expressionFile : expressionPath.listFiles()) {
                try {
                    String name = expressionFile.getPath().getName();
                    expressions.put(name, new RankingExpression(name, expressionFile.createReader()));
                }
                catch (FileNotFoundException e) {
                    throw new IllegalStateException("Expression file removed while reading: " + expressionFile, e);
                }
                catch (ParseException e) {
                    throw new IllegalStateException("Invalid stored expression in " + expressionFile, e);
                }
            }
            return expressions;
        }

        /** Adds this macro expression to the application package to it can be read later. */
        void writeMacro(String name, RankingExpression expression) {
            application.getFile(modelFiles.macrosPath()).appendFile(name + "\t" +
                                                                   expression.getRoot().toString() + "\n");
        }

        /** Reads the previously stored macro expressions for these arguments */
        List<Pair<String, RankingExpression>> readMacros() {
            try {
                ApplicationFile file = application.getFile(modelFiles.macrosPath());
                if (!file.exists()) return Collections.emptyList();

                List<Pair<String, RankingExpression>> macros = new ArrayList<>();
                BufferedReader reader = new BufferedReader(file.createReader());
                String line;
                while (null != (line = reader.readLine())) {
                    String[] parts = line.split("\t");
                    String name = parts[0];
                    try {
                        RankingExpression expression = new RankingExpression(parts[1]);
                        macros.add(new Pair<>(name, expression));
                    }
                    catch (ParseException e) {
                        throw new IllegalStateException("Could not parse " + name, e);
                    }
                }
                return macros;
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
                for (ApplicationFile constantFile : application.getFile(modelFiles.largeConstantsPath()).listFiles()) {
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
            Path constantsPath = ApplicationPackage.MODELS_GENERATED_DIR.append(modelFiles.modelPath()).append("constants");

            // "tbf" ending for "typed binary format" - recognized by the nodes receiving the file:
            Path constantPath = constantsPath.append(name + ".tbf");

            // Remember the constant in a file we replicate in ZooKeeper
            application.getFile(modelFiles.largeConstantsPath().append(name + ".constant"))
                       .writeFile(new StringReader(name + ":" + constant.type() + ":" + correct(constantPath)));

            // Write content explicitly as a file on the file system as this is distributed using file distribution
            createIfNeeded(constantsPath);
            IOUtils.writeFile(application.getFileReference(constantPath), TypedBinaryFormat.encode(constant));
            return correct(constantPath);
        }

        private List<Pair<String, Tensor>> readSmallConstants() {
            try {
                ApplicationFile file = application.getFile(modelFiles.smallConstantsPath());
                if (!file.exists()) return Collections.emptyList();

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

        private void close(Reader reader) {
            try {
                if (reader != null)
                    reader.close();
            }
            catch (IOException e) {
                // ignore
            }
        }

    }

    static class ModelFiles {

        Path modelPath;

        public ModelFiles(Path modelPath) {
            this.modelPath = modelPath;
        }

        /** Returns modelPath with slashes replaced by underscores */
        public String modelName() { return modelPath.toString().replace('/', '_').replace('.', '_'); }

        /** Returns relative path to this model below the "models/" dir in the application package */
        public Path modelPath() { return modelPath; }

        /** Files stored below this path will be replicated in zookeeper */
        public Path storedModelReplicatedPath() {
            return ApplicationPackage.MODELS_GENERATED_REPLICATED_DIR.append(modelPath());
        }

        /** Files stored below this path will not be replicated */
        public Path storedModelPath() {
            return ApplicationPackage.MODELS_GENERATED_DIR.append(modelPath());
        }

        public Path expressionPath(String name) {
            return expressionsPath().append(name);
        }

        public Path expressionsPath() {
            return storedModelReplicatedPath().append("expressions");
        }

        public Path smallConstantsPath() {
            return storedModelPath().append("constants.txt");
        }

        /** Path to the large (ranking) constants directory */
        public Path largeConstantsPath() {
            return storedModelReplicatedPath().append("constants");
        }

        /** Path to the macros file */
        public Path macrosPath() {
            return storedModelReplicatedPath().append("macros.txt");
        }

    }

    /** Encapsulates the arguments of a specific model output */
    static class FeatureArguments {

        private final String modelName;
        private final Path modelPath;

        /** Optional arguments */
        private final Optional<String> signature, output;

        public FeatureArguments(Arguments arguments) {
            this(Path.fromString(asString(arguments.expressions().get(0))),
                 optionalArgument(1, arguments),
                 optionalArgument(2, arguments));
        }

        public FeatureArguments(Path modelPath, Optional<String> signature, Optional<String> output) {
            this.modelPath = modelPath;
            this.modelName = toModelName(modelPath);
            this.signature = signature;
            this.output = output;
        }

        public Path modelPath() { return modelPath; }

        public Optional<String> signature() { return signature; }
        public Optional<String> output() { return output; }

        public String toName() {
            return modelName +
                   (signature.isPresent() ? "." + signature.get() : "") +
                   (output.isPresent() ? "." + output.get() : "");
        }

        private static Optional<String> optionalArgument(int argumentIndex, Arguments arguments) {
            if (argumentIndex >= arguments.expressions().size())
                return Optional.empty();
            return Optional.of(asString(arguments.expressions().get(argumentIndex)));
        }

        public static String asString(ExpressionNode node) {
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

    }

}
