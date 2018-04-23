// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OperationMapper;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.TensorConverter;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Const;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.TensorFlowOperation;
import com.yahoo.searchlib.rankingexpression.integration.tensorflow.importer.operations.Variable;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 * @author lesters
 */
public class TensorFlowImporter {

    private static final Logger log = Logger.getLogger(TensorFlowImporter.class.getName());

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a .pbtxt or .pb file.
     * The name of the model is taken as the db/pbtxt file name (not including the file ending).
     *
     * @param modelName the name of the model to import, consisting of characters in [A-Za-z0-9_]
     * @param modelDir the directory containing the TensorFlow model files to import
     */
    public TensorFlowModel importModel(String modelName, String modelDir) {
        try (SavedModelBundle model = SavedModelBundle.load(modelDir, "serve")) {

            return importModel(modelName, model);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    public TensorFlowModel importModel(String modelName, File modelDir) {
        return importModel(modelName, modelDir.toString());
    }

    /** Imports a TensorFlow model */
    public TensorFlowModel importModel(String modelName, SavedModelBundle model) {
        try {
            return importGraph(modelName, MetaGraphDef.parseFrom(model.metaGraphDef()), model);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model '" + model + "'", e);
        }
    }

    /**
     * Imports the TensorFlow graph by first importing the tensor types, then
     * finding a suitable set of dimensions names for each
     * placeholder/constant/variable, then importing the expressions.
     */
    private static TensorFlowModel importGraph(String modelName, MetaGraphDef graph, SavedModelBundle bundle) {
        TensorFlowModel model = new TensorFlowModel(modelName);
        OperationIndex index = new OperationIndex();

        importSignatures(graph, model);
        importNodes(graph, model, index);
        findDimensionNames(model, index);
        importExpressions(model, index, bundle);

        reportWarnings(model, index);
        logVariableTypes(index);

        return model;
    }

    private static void importSignatures(MetaGraphDef graph, TensorFlowModel model) {
        for (Map.Entry<String, SignatureDef> signatureEntry : graph.getSignatureDefMap().entrySet()) {
            String signatureName = signatureEntry.getKey();
            TensorFlowModel.Signature signature = model.signature(signatureName);

            Map<String, TensorInfo> inputInfoMap = signatureEntry.getValue().getInputsMap();
            for (Map.Entry<String, TensorInfo> input : inputInfoMap.entrySet()) {
                String inputName = input.getKey();
                signature.input(inputName, namePartOf(input.getValue().getName()));
            }

            Map<String, TensorInfo> outputInfoMap = signatureEntry.getValue().getOutputsMap();
            for (Map.Entry<String, TensorInfo> output : outputInfoMap.entrySet()) {
                String outputName = output.getKey();
                signature.output(outputName, namePartOf(output.getValue().getName()));
            }
        }
    }

    private static boolean isSignatureInput(TensorFlowModel model, TensorFlowOperation operation) {
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String inputName : signature.inputs().values()) {
                if (inputName.equals(operation.node().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSignatureOutput(TensorFlowModel model, TensorFlowOperation operation) {
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                if (outputName.equals(operation.node().getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void importNodes(MetaGraphDef graph, TensorFlowModel model, OperationIndex index) {
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                importNode(model.name(), outputName, graph.getGraphDef(), index);
            }
        }
    }

    private static TensorFlowOperation importNode(String modelName, String nodeName, GraphDef graph, OperationIndex index) {
        if (index.alreadyImported(nodeName)) {
            return index.get(nodeName);
        }
        NodeDef node = getTensorFlowNodeFromGraph(namePartOf(nodeName), graph);
        List<TensorFlowOperation> inputs = importNodeInputs(modelName, node, graph, index);
        TensorFlowOperation operation = OperationMapper.get(modelName, node, inputs, portPartOf(nodeName));
        index.put(nodeName, operation);

        List<TensorFlowOperation> controlInputs = importControlInputs(modelName, node, graph, index);
        if (controlInputs.size() > 0) {
            operation.setControlInputs(controlInputs);
        }

        return operation;
    }

    private static List<TensorFlowOperation> importNodeInputs(String modelName, NodeDef node, GraphDef graph, OperationIndex index) {
        return node.getInputList().stream()
                .filter(name -> ! isControlDependency(name))
                .map(nodeName -> importNode(modelName, nodeName, graph, index))
                .collect(Collectors.toList());
    }

    private static List<TensorFlowOperation> importControlInputs(String modelName, NodeDef node, GraphDef graph, OperationIndex index) {
        return node.getInputList().stream()
                .filter(nodeName -> isControlDependency(nodeName))
                .map(nodeName -> importNode(modelName, nodeName, graph, index))
                .collect(Collectors.toList());
    }

    private static boolean isControlDependency(String name) {
        return name.startsWith("^");
    }

    /** Find dimension names to avoid excessive renaming while evaluating the model. */
    private static void findDimensionNames(TensorFlowModel model, OperationIndex index) {
        DimensionRenamer renamer = new DimensionRenamer();
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String output : signature.outputs().values()) {
                addDimensionNameConstraints(index.get(output), renamer);
            }
        }
        renamer.solve();
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String output : signature.outputs().values()) {
                renameDimensions(index.get(output), renamer);
            }
        }
    }

    private static void addDimensionNameConstraints(TensorFlowOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> addDimensionNameConstraints(input, renamer));
            operation.addDimensionNameConstraints(renamer);
        }
    }

    private static void renameDimensions(TensorFlowOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> renameDimensions(input, renamer));
            operation.renameDimensions(renamer);
        }
    }

    private static void importExpressions(TensorFlowModel model, OperationIndex index, SavedModelBundle bundle) {
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String outputName : signature.outputs().values()) {
                try {
                    Optional<TensorFunction> function = importExpression(index.get(outputName), model, bundle);
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

    private static Optional<TensorFunction> importExpression(TensorFlowOperation operation, TensorFlowModel model, SavedModelBundle bundle) {
        if (!operation.type().isPresent()) {
            return Optional.empty();
        }
        if (operation.isConstant()) {
            return importConstant(model, operation, bundle);
        }

        importInputExpressions(operation, model, bundle);
        importRankingExpression(model, operation);
        importInputExpression(model, operation);
        importMacroExpression(model, operation);

        return operation.function();
    }

    private static void importInputExpressions(TensorFlowOperation operation, TensorFlowModel model,
                                               SavedModelBundle bundle) {
        operation.inputs().forEach(input -> importExpression(input, model, bundle));
    }

    private static void importMacroExpression(TensorFlowModel model, TensorFlowOperation operation) {
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

    private static Optional<TensorFunction> importConstant(TensorFlowModel model, TensorFlowOperation operation,
                                                           SavedModelBundle bundle) {
        String name = operation.vespaName();
        if (model.largeConstants().containsKey(name) || model.smallConstants().containsKey(name)) {
            return operation.function();
        }

        Tensor tensor;
        if (operation.getConstantValue().isPresent()) {
            Value value = operation.getConstantValue().get();
            if ( ! (value instanceof TensorValue)) {
                return operation.function(); // scalar values are inserted directly into the expression
            }
            tensor = value.asTensor();
        } else {
            // Here we use the type from the operation, which will have correct dimension names after name resolving
            tensor = TensorConverter.toVespaTensor(readVariable(operation.node().getName(), bundle),
                                                   operation.type().get());
            operation.setConstantValue(new TensorValue(tensor));
        }

        if (tensor.type().rank() == 0) {
            model.smallConstant(name, tensor);
        } else {
            model.largeConstant(name, tensor);
        }
        return operation.function();
    }

    static org.tensorflow.Tensor<?> readVariable(String name, SavedModelBundle bundle) {
        Session.Runner fetched = bundle.session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if (importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from fetching " + name +
                                            ", but got " + importedTensors.size());
        return importedTensors.get(0);
    }

    private static void importRankingExpression(TensorFlowModel model, TensorFlowOperation operation) {
        if (operation.function().isPresent()) {
            String name = operation.node().getName();
            if (!model.expressions().containsKey(operation.node().getName())) {
                TensorFunction function = operation.function().get();

                // Make sure output adheres to standard naming convention
                if (isSignatureOutput(model, operation)) {
                    OrderedTensorType operationType = operation.type().get();
                    OrderedTensorType standardNamingType = OrderedTensorType.fromTensorFlowType(operation.node());
                    if ( ! operationType.equals(standardNamingType)) {
                        List<String> renameFrom = operationType.dimensionNames();
                        List<String> renameTo = standardNamingType.dimensionNames();
                        function = new Rename(function, renameFrom, renameTo);
                    }
                }

                try {
                    // We add all intermediate nodes imported as separate expressions. Only
                    // those referenced  in a signature output will be used. We parse the
                    // TensorFunction here to convert it to a RankingExpression tree.
                    model.expression(name, new RankingExpression(name, function.toString()));
                }
                catch (ParseException e) {
                    throw new RuntimeException("Tensorflow function " + function +
                                               " cannot be parsed as a ranking expression", e);
                }
            }
        }
    }

    private static void importInputExpression(TensorFlowModel model, TensorFlowOperation operation) {
        if (operation.isInput() && isSignatureInput(model, operation)) {
            // All inputs must have dimensions with standard naming convention: d0, d1, ...
            OrderedTensorType standardNamingConvention = OrderedTensorType.fromTensorFlowType(operation.node());
            model.argument(operation.node().getName(), standardNamingConvention.type());
            model.requiredMacro(operation.vespaName(), standardNamingConvention.type());
        }
    }

    private static void reportWarnings(TensorFlowModel model, OperationIndex index) {
        for (TensorFlowModel.Signature signature : model.signatures().values()) {
            for (String output : signature.outputs().values()) {
                reportWarnings(index.get(output), signature);
            }
        }
    }

    /**
     * Log all TensorFlow Variables (i.e file constants) imported as part of this with their ordered type.
     * This allows users to learn the exact types (including dimension order after renaming) of the Variables
     * such that these can be converted and fed to a parent document independently of the rest of the model
     * for fast model weight updates.
     */
    private static void logVariableTypes(OperationIndex index) {
        for (TensorFlowOperation operation : index.operations()) {
            if ( ! (operation instanceof Variable)) continue;
            if ( ! operation.type().isPresent()) continue; // will not happen

            log.info("Importing TensorFlow variable " + operation.node().getName() + " as " + operation.vespaName() +
                     " of type " + operation.type().get());
        }
    }

    private static void reportWarnings(TensorFlowOperation operation, TensorFlowModel.Signature signature) {
        for (String warning : operation.warnings()) {
            signature.importWarning(warning);
        }
        for (TensorFlowOperation input : operation.inputs()) {
            reportWarnings(input, signature);
        }
    }

    private static NodeDef getTensorFlowNodeFromGraph(String name, GraphDef graph) {
        for (NodeDef node : graph.getNodeList()) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Could not find node '" + name + "'");
    }

    /**
     * A method signature input and output has the form name:index.
     * This returns the name part without the index.
     */
    private static String namePartOf(String name) {
        name = name.startsWith("^") ? name.substring(1) : name;
        return name.split(":")[0];
    }

    /**
     * This return the output port part. Indexes are used for nodes with
     * multiple outputs.
     */
    private static int portPartOf(String name) {
        int i = name.indexOf(":");
        return i < 0 ? 0 : Integer.parseInt(name.substring(i + 1));
    }

    private static class OperationIndex {

        private final Map<String, TensorFlowOperation> index = new HashMap<>();
        public TensorFlowOperation put(String key, TensorFlowOperation operation) { return index.put(key, operation); }
        public TensorFlowOperation get(String key) { return index.get(key); }
        public boolean alreadyImported(String key) { return index.containsKey(key); }
        public Collection<TensorFlowOperation> operations() { return index.values(); }

    }

}
