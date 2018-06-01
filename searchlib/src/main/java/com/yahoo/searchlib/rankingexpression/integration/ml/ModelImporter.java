package com.yahoo.searchlib.rankingexpression.integration.ml;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.IntermediateGraph;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.IntermediateOperation;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.yolean.Exceptions;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class ModelImporter {

    /**
     * The main import function.
     */
    public abstract ImportedModel importModel(String modelName, String modelPath);

    public ImportedModel importModel(String modelName, File modelDir) {
        return importModel(modelName, modelDir.toString());
    }

    static ImportedModel convertIntermediateGraphToModel(IntermediateGraph graph) {
        ImportedModel model = new ImportedModel(graph.name());

        graph.optimize();

        importSignatures(graph, model);
        importExpressions(graph, model);
        reportWarnings(graph, model);
        logVariableTypes(graph, model);

        return model;
    }

    private static void importSignatures(IntermediateGraph graph, ImportedModel model) {
        for (String signatureName : graph.signatures()) {
            ImportedModel.Signature signature = model.signature(signatureName);
            for (Map.Entry<String, String> input : graph.inputs(signatureName).entrySet()) {
                signature.input(input.getKey(), input.getValue());
            }
            for (Map.Entry<String, String> output : graph.outputs(signatureName).entrySet()) {
                signature.output(output.getKey(), output.getValue());
            }
        }
    }

    private static boolean isSignatureInput(ImportedModel model, IntermediateOperation operation) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String inputName : signature.inputs().values()) {
                if (inputName.equals(operation.name())) {
                    return true;
                }
            }
        }
        return false;
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
    static void importExpressions(IntermediateGraph graph, ImportedModel model) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                try {
                    Optional<TensorFunction> function = importExpression(graph.get(outputName), model);
                    if (!function.isPresent()) {
                        signature.skippedOutput(outputName, "No valid output function could be found.");
                    }
                }
                catch (IllegalArgumentException e) {
                    signature.skippedOutput(outputName, Exceptions.toMessageString(e));
                }
            }
        }
    }

    private static Optional<TensorFunction> importExpression(IntermediateOperation operation, ImportedModel model) {
        if (!operation.type().isPresent()) {
            return Optional.empty();
        }
        if (operation.isConstant()) {
            return importConstant(operation, model);
        }
        importExpressionInputs(operation, model);
        importRankingExpression(operation, model);
        importArgumentExpression(operation, model);
        importMacroExpression(operation, model);

        return operation.function();
    }

    private static void importExpressionInputs(IntermediateOperation operation, ImportedModel model) {
        operation.inputs().forEach(input -> importExpression(input, model));
    }

    private static Optional<TensorFunction> importConstant(IntermediateOperation operation, ImportedModel model) {
        String name = operation.vespaName();
        if (model.largeConstants().containsKey(name) || model.smallConstants().containsKey(name)) {
            return operation.function();
        }

//        Tensor tensor;
//        if (operation.getConstantValue().isPresent()) {
//            Value value = operation.getConstantValue().get();
//            if ( ! (value instanceof TensorValue)) {
//                return operation.function(); // scalar values are inserted directly into the expression
//            }
//            tensor = value.asTensor();
//        } else {
//            // Here we use the type from the operation, which will have correct dimension names after name resolving
//            tensor = TensorConverter.toVespaTensor(readVariable(operation.node().getName(), bundle),
//                                                   operation.type().get());
//            operation.setConstantValue(new TensorValue(tensor));
//        }

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
            if (!model.expressions().containsKey(name)) {
                TensorFunction function = operation.function().get();

                if (isSignatureOutput(model, operation)) {
                    OrderedTensorType operationType = operation.type().get();
                    OrderedTensorType standardNamingType = OrderedTensorType.standardType(operationType);
                    if ( ! operationType.equals(standardNamingType)) {
                        List<String> renameFrom = operationType.dimensionNames();
                        List<String> renameTo = standardNamingType.dimensionNames();
                        function = new Rename(function, renameFrom, renameTo);
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
            model.argument(operation.vespaName(), standardNamingConvention.type());
            model.requiredMacro(operation.vespaName(), standardNamingConvention.type());
        }
    }

    private static void importMacroExpression(IntermediateOperation operation, ImportedModel model) {
        if (operation.macro().isPresent()) {
            TensorFunction function = operation.macro().get();
            try {
                model.macro(operation.macroName(), new RankingExpression(operation.macroName(), function.toString()));
            }
            catch (ParseException e) {
                throw new RuntimeException("Tensorflow function " + function +
                                           " cannot be parsed as a ranking expression", e);
            }
        }
    }

    /**
     * Convert intermediate representation to Vespa ranking expressions.
     */
    static void reportWarnings(IntermediateGraph graph, ImportedModel model) {
        for (ImportedModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                reportWarnings(graph.get(outputName), model);
            }
        }
    }

    private static void reportWarnings(IntermediateOperation operation, ImportedModel model) {
        for (String warning : operation.warnings()) {
            model.defaultSignature().importWarning(warning);
        }
        for (IntermediateOperation input : operation.inputs()) {
            reportWarnings(input, model);
        }
    }

    private static void logVariableTypes(IntermediateGraph graph, ImportedModel model) {
        // todo
    }

}
