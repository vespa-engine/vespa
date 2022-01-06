// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModel;
import ai.vespa.rankingexpression.importer.configmodelview.MlModelImporter;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import ai.vespa.rankingexpression.importer.operations.Constant;
import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.text.ExpressionFormatter;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for importing ML models (ONNX/TensorFlow etc.) as native Vespa
 * ranking expressions. The general mechanism for import is for the
 * specific ML platform import implementations to create an
 * IntermediateGraph. This class offers common code to convert the
 * IntermediateGraph to Vespa ranking expressions and functions.
 *
 * @author lesters
 */
public abstract class ModelImporter implements MlModelImporter {

    private static final Logger log = Logger.getLogger(ModelImporter.class.getName());

    /** Returns whether the file or directory at the given path is of the type which can be imported by this */
    @Override
    public abstract boolean canImport(String modelPath);

    @Override
    public final ImportedModel importModel(String modelName, File modelPath) {
        return importModel(modelName, modelPath.toString());
    }

    /** Imports the given model */
    public abstract ImportedModel importModel(String modelName, String modelPath);

    /**
     * Takes an IntermediateGraph and converts it to a ImportedModel containing
     * the actual Vespa ranking expressions.
     */
    protected static ImportedModel convertIntermediateGraphToModel(IntermediateGraph graph,
                                                                   String modelSource,
                                                                   ImportedMlModel.ModelType modelType) {
        ImportedModel model = new ImportedModel(graph.name(), modelSource, modelType);
        log.log(Level.FINER, () -> "Intermediate graph created from '" + modelSource + "':\n" +
                                   ExpressionFormatter.inTwoColumnMode(70, 50).format(graph.toFullString()));

        graph.optimize();

        importSignatures(graph, model);
        importExpressions(graph, model);
        reportWarnings(graph, model);
        logVariableTypes(graph);

        return model;
    }

    private static void importSignatures(IntermediateGraph graph, ImportedModel model) {
        for (String signatureName : graph.signatures()) {
            ImportedModel.Signature signature = model.signature(signatureName);
            for (Map.Entry<String, String> input : graph.inputs(signatureName).entrySet()) {
                signature.input(input.getKey(), input.getValue());
            }
            for (Map.Entry<String, String> output : graph.outputs(signatureName).entrySet()) {
                signature.output(IntermediateOperation.vespaName(output.getKey()), output.getValue());
            }
        }
    }

    private static boolean isSignatureOutput(ImportedModel model, IntermediateOperation operation) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                if (outputName.equals(operation.name())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Convert intermediate representation to Vespa ranking expressions.
     */
    private static void importExpressions(IntermediateGraph graph, ImportedModel model) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                try {
                    Optional<TensorFunction<Reference>> function = importExpression(graph.get(outputName), model);
                    if (function.isEmpty()) {
                        signature.skippedOutput(outputName, "No valid output function could be found.");
                    }
                }
                catch (IllegalArgumentException e) {
                    signature.skippedOutput(outputName, Exceptions.toMessageString(e));
                }
            }
        }
    }

    private static Optional<TensorFunction<Reference>> importExpression(IntermediateOperation operation, ImportedModel model) {
        if (model.expressions().containsKey(operation.name())) {
            return operation.function();
        }
        if (operation.type().isEmpty()) {
            return Optional.empty();
        }
        if (operation.isConstant()) {
            return importConstant(operation, model);
        }
        importExpressionInputs(operation, model);
        importRankingExpression(operation, model);
        importArgumentExpression(operation, model);
        importFunctionExpression(operation, model);

        return operation.function();
    }

    private static void importExpressionInputs(IntermediateOperation operation, ImportedModel model) {
        operation.inputs().forEach(input -> importExpression(input, model));
    }

    private static Optional<TensorFunction<Reference>> importConstant(IntermediateOperation operation, ImportedModel model) {
        String name = operation.vespaName();
        if (model.hasLargeConstant(name) || model.hasSmallConstant(name)) {
            return operation.function();
        }

        Value value = operation.getConstantValue().orElseThrow(() ->
                new IllegalArgumentException("Operation '" + operation.vespaName() + "' " +
                                             "is constant but does not have a value."));
        if ( ! (value instanceof TensorValue)) {
            return operation.function(); // scalar values are inserted directly into the expression
        }

        Tensor tensor = value.asTensor();
        if (tensor.type().rank() == 0) {
            model.smallConstant(name, tensor);
        } else {
            model.largeConstant(name, tensor);
        }
        return operation.function();
    }

    private static void importRankingExpression(IntermediateOperation operation, ImportedModel model) {
        if (operation.function().isPresent()) {
            String name = operation.name();
            if ( ! model.expressions().containsKey(name)) {
                TensorFunction<Reference> function = operation.function().get();

                if (isSignatureOutput(model, operation)) {
                    OrderedTensorType operationType = operation.type().get();
                    OrderedTensorType standardNamingType = OrderedTensorType.standardType(operationType);
                    if ( ! operationType.equals(standardNamingType)) {
                        List<String> renameFrom = operationType.dimensionNames();
                        List<String> renameTo = standardNamingType.dimensionNames();
                        function = new Rename<Reference>(function, renameFrom, renameTo);
                    }
                }

                try {
                    // We add all intermediate nodes imported as separate expressions. Only
                    // those referenced from the output will be used. We parse the
                    // TensorFunction here to convert it to a RankingExpression tree.
                    model.expression(name, new RankingExpression(name, function.toString()));
                }
                catch (ParseException e) {
                    throw new RuntimeException("Imported function " + function +
                                               " cannot be parsed as a ranking expression", e);
                }
            }
        }
    }

    private static void importArgumentExpression(IntermediateOperation operation, ImportedModel model) {
        if (operation.isInput()) {
            // All inputs must have dimensions with standard naming convention: d0, d1, ...
            OrderedTensorType standardNamingConvention = OrderedTensorType.standardType(operation.type().get());
            model.input(operation.vespaName(), standardNamingConvention.type());
        }
    }

    private static void importFunctionExpression(IntermediateOperation operation, ImportedModel model) {
        if (operation.rankingExpressionFunction().isPresent()) {
            TensorFunction<Reference> function = operation.rankingExpressionFunction().get();
            try {
                model.function(operation.rankingExpressionFunctionName(),
                               new RankingExpression(operation.rankingExpressionFunctionName(), function.toString()));
            }
            catch (ParseException e) {
                throw new RuntimeException("Model function " + function +
                                           " cannot be parsed as a ranking expression", e);
            }
        }
    }

    /**
     * Add any import warnings to the signature in the ImportedModel.
     */
    private static void reportWarnings(IntermediateGraph graph, ImportedModel model) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                reportWarnings(graph.get(outputName), model, new HashSet<>());
            }
        }
    }

    private static void reportWarnings(IntermediateOperation operation, ImportedModel model, Set<String> processed) {
        if (processed.contains(operation.name())) {
            return;
        }
        for (String warning : operation.warnings()) {
            // If we want to report warnings, that code goes here
        }
        for (IntermediateOperation input : operation.inputs()) {
            reportWarnings(input, model, processed);
        }
        processed.add(operation.name());
    }

    /**
     * Log all model Variables (i.e file constants) imported as part of this with their ordered type.
     * This allows users to learn the exact types (including dimension order after renaming) of the Variables
     * such that these can be converted and fed to a parent document independently of the rest of the model
     * for fast model weight updates.
     */
    private static void logVariableTypes(IntermediateGraph graph) {
        for (IntermediateOperation operation : graph.operations().values()) {
            if ( ! (operation instanceof Constant)) continue;
            if ( ! operation.type().isPresent()) continue; // will not happen
            log.info("Importing model variable " + operation.name() + " as " + operation.vespaName() +
                    " of type " + operation.type().get());
        }
    }

}
