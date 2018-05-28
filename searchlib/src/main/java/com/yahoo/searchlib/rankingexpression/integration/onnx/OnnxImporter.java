package com.yahoo.searchlib.rankingexpression.integration.onnx;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.Constant;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.Argument;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations.OnnxOperation;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OperationMapper;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.yolean.Exceptions;
import onnx.Onnx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Converts a ONNX model into a ranking expression and set of constants.
 *
 * @author lesters
 */
public class OnnxImporter {

    private static final Logger log = Logger.getLogger(OnnxImporter.class.getName());

    public OnnxModel importModel(String modelName, File modelDir) {
        return importModel(modelName, modelDir.toString());
    }

    public OnnxModel importModel(String modelName, String modelPath) {
        try (FileInputStream inputStream = new FileInputStream(modelPath)) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            return importModel(modelName, model);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

    public OnnxModel importModel(String modelName, Onnx.ModelProto model) {
        return importGraph(modelName, model.getGraph());
    }

    private static OnnxModel importGraph(String modelName, Onnx.GraphProto graph) {
        OnnxModel model = new OnnxModel(modelName);
        OperationIndex index = new OperationIndex();

        importNodes(graph, model, index);
        verifyOutputTypes(graph, model, index);
        findDimensionNames(model, index);
        importExpressions(model, index);

        reportWarnings(model, index);

        return model;
    }

    private static void importNodes(Onnx.GraphProto graph, OnnxModel model, OperationIndex index) {
        for (Onnx.ValueInfoProto valueInfo : graph.getOutputList()) {
            importNode(valueInfo.getName(), graph, model, index);
        }
    }

    private static OnnxOperation importNode(String name, Onnx.GraphProto graph, OnnxModel model, OperationIndex index) {
        if (index.alreadyImported(name)) {
            return index.get(name);
        }
        OnnxOperation operation;
        if (isArgumentTensor(name, graph)) {
            operation = new Argument(getArgumentTensor(name, graph));
            model.input(OnnxOperation.namePartOf(name), operation.vespaName());
        } else if (isConstantTensor(name, graph)) {
            operation = new Constant(model.name(), getConstantTensor(name, graph));
        } else {
            Onnx.NodeProto node = getNodeFromGraph(name, graph);
            List<OnnxOperation> inputs = importNodeInputs(node, graph, model, index);
            operation = OperationMapper.get(node, inputs);
            if (isOutputNode(name, graph)) {
                model.output(OnnxOperation.namePartOf(name), operation.vespaName());
            }
        }
        index.put(operation.vespaName(), operation);

        return operation;
    }

    private static boolean isArgumentTensor(String name, Onnx.GraphProto graph) {
        Onnx.ValueInfoProto value = getArgumentTensor(name, graph);
        Onnx.TensorProto tensor = getConstantTensor(name, graph);
        return value != null && tensor == null;
    }

    private static boolean isConstantTensor(String name, Onnx.GraphProto graph) {
        Onnx.ValueInfoProto value = getArgumentTensor(name, graph);
        Onnx.TensorProto tensor = getConstantTensor(name, graph);
        return value != null && tensor != null;
    }

    private static Onnx.ValueInfoProto getArgumentTensor(String name, Onnx.GraphProto graph) {
        for (Onnx.ValueInfoProto valueInfo : graph.getInputList()) {
            if (valueInfo.getName().equals(name)) {
                return valueInfo;
            }
        }
        return null;
    }

    private static Onnx.TensorProto getConstantTensor(String name, Onnx.GraphProto graph) {
        for (Onnx.TensorProto tensorProto : graph.getInitializerList()) {
            if (tensorProto.getName().equals(name)) {
                return tensorProto;
            }
        }
        return null;
    }

    private static boolean isOutputNode(String name, Onnx.GraphProto graph) {
        return getOutputNode(name, graph) != null;
    }

    private static Onnx.ValueInfoProto getOutputNode(String name, Onnx.GraphProto graph) {
        for (Onnx.ValueInfoProto valueInfo : graph.getOutputList()) {
            if (valueInfo.getName().equals(name)) {
                return valueInfo;
            }
            String nodeName = OnnxOperation.namePartOf(valueInfo.getName());
            if (nodeName.equals(name)) {
                return valueInfo;
            }
        }
        return null;
    }

    private static List<OnnxOperation> importNodeInputs(Onnx.NodeProto node,
                                                        Onnx.GraphProto graph,
                                                        OnnxModel model,
                                                        OperationIndex index) {
        return node.getInputList().stream()
                .map(nodeName -> importNode(nodeName, graph, model, index))
                .collect(Collectors.toList());
    }

    private static void verifyOutputTypes(Onnx.GraphProto graph, OnnxModel model, OperationIndex index) {
        for (String outputName : model.outputs().values()) {
            OnnxOperation operation = index.get(outputName);
            Onnx.ValueInfoProto onnxNode = getOutputNode(outputName, graph);
            operation.type().orElseThrow(
                        () -> new IllegalArgumentException("Output of '" + outputName + "' has no type."))
                    .verifyType(onnxNode.getType());
        }
    }


    /** Find dimension names to avoid excessive renaming while evaluating the model. */
    private static void findDimensionNames(OnnxModel model, OperationIndex index) {
        DimensionRenamer renamer = new DimensionRenamer();
        for (String output : model.outputs().values()) {
            addDimensionNameConstraints(index.get(output), renamer);
        }
        renamer.solve();
        for (String output : model.outputs().values()) {
            renameDimensions(index.get(output), renamer);
        }
    }

    private static void addDimensionNameConstraints(OnnxOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> addDimensionNameConstraints(input, renamer));
            operation.addDimensionNameConstraints(renamer);
        }
    }

    private static void renameDimensions(OnnxOperation operation, DimensionRenamer renamer) {
        if (operation.type().isPresent()) {
            operation.inputs().forEach(input -> renameDimensions(input, renamer));
            operation.renameDimensions(renamer);
        }
    }

    private static void importExpressions(OnnxModel model, OperationIndex index) {
        for (String outputName : model.outputs().values()) {
            try {
                Optional<TensorFunction> function = importExpression(index.get(outputName), model);
                if (!function.isPresent()) {
                    model.skippedOutput(outputName, "No valid output function could be found.");
                }
            }
            catch (IllegalArgumentException e) {
                model.skippedOutput(outputName, Exceptions.toMessageString(e));
            }
        }
    }

    private static Optional<TensorFunction> importExpression(OnnxOperation operation, OnnxModel model) {
        if (!operation.type().isPresent()) {
            return Optional.empty();
        }
        if (operation.isConstant()) {
            return importConstant(operation, model);
        }
        importInputExpressions(operation, model);
        importRankingExpression(operation, model);
        importArgumentExpression(operation, model);

        return operation.function();
    }

    private static void importInputExpressions(OnnxOperation operation, OnnxModel model) {
        operation.inputs().forEach(input -> importExpression(input, model));
    }

    private static Optional<TensorFunction> importConstant(OnnxOperation operation, OnnxModel model) {
        String name = operation.vespaName();
        if (model.largeConstants().containsKey(name) || model.smallConstants().containsKey(name)) {
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

    private static void importRankingExpression(OnnxOperation operation, OnnxModel model) {
        if (operation.function().isPresent()) {
            String name = operation.vespaName();
            if (!model.expressions().containsKey(name)) {
                TensorFunction function = operation.function().get();

                if (model.outputs().containsKey(name)) {
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
                    throw new RuntimeException("Tensorflow function " + function +
                            " cannot be parsed as a ranking expression", e);
                }
            }
        }
    }

    private static void importArgumentExpression(OnnxOperation operation, OnnxModel model) {
        if (operation.isInput()) {
            // All inputs must have dimensions with standard naming convention: d0, d1, ...
            OrderedTensorType standardNamingConvention = OrderedTensorType.standardType(operation.type().get());
            model.argument(operation.vespaName(), standardNamingConvention.type());
            model.requiredMacro(operation.vespaName(), standardNamingConvention.type());
        }
    }

    private static void reportWarnings(OnnxModel model, OperationIndex index) {
        for (String output : model.outputs().values()) {
            reportWarnings(model, index.get(output));
        }
    }

    private static void reportWarnings(OnnxModel model, OnnxOperation operation) {
        for (String warning : operation.warnings()) {
            model.importWarning(warning);
        }
        for (OnnxOperation input : operation.inputs()) {
            reportWarnings(model, input);
        }
    }

    private static Onnx.NodeProto getNodeFromGraph(String nodeName, Onnx.GraphProto graph) {
        boolean hasPortNumber = nodeName.contains(":");
        for (Onnx.NodeProto node : graph.getNodeList()) {
            if (hasPortNumber) {
                for (String outputName : node.getOutputList()) {
                    if (outputName.equals(nodeName)) {
                        return node;
                    }
                }
            } else if (node.getName().equals(nodeName)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Node '" + nodeName + "' not found in ONNX graph");
    }

    private static class OperationIndex {
        private final Map<String, OnnxOperation> index = new HashMap<>();
        public OnnxOperation put(String key, OnnxOperation operation) { return index.put(key, operation); }
        public OnnxOperation get(String key) { return index.get(key); }
        public boolean alreadyImported(String key) { return index.containsKey(key); }
        public Collection<OnnxOperation> operations() { return index.values(); }
    }

}
