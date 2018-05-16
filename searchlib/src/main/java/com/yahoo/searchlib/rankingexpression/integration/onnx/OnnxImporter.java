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
import onnx.Onnx;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Converts a ONNX model into a ranking expression and set of constants.
 *
 * @author lesters
 */
public class OnnxImporter {

    public OnnxModel importModel(String modelPath, String outputNode) {
        try (FileInputStream inputStream = new FileInputStream(modelPath)) {
            Onnx.ModelProto model = Onnx.ModelProto.parseFrom(inputStream);
            return importModel(model, outputNode);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not import ONNX model from '" + modelPath + "'", e);
        }
    }

    public OnnxModel importModel(Onnx.ModelProto model, String outputNode) {
        return importGraph(model.getGraph(), outputNode);
    }

    private static OnnxModel importGraph(Onnx.GraphProto graph, String outputNode) {
        OnnxModel model = new OnnxModel(outputNode);
        OperationIndex index = new OperationIndex();

        OnnxOperation output = importNode(outputNode, graph, index);
        output.type().orElseThrow(() -> new IllegalArgumentException("Output of '" + outputNode + "' has no type."))
                .verifyType(getOutputNode(outputNode, graph).getType());

        findDimensionNames(output);
        importExpressions(output, model);

        return model;
    }

    private static OnnxOperation importNode(String nodeName, Onnx.GraphProto graph, OperationIndex index) {
        if (index.alreadyImported(nodeName)) {
            return index.get(nodeName);
        }
        OnnxOperation operation;
        if (isArgumentTensor(nodeName, graph)) {
            operation = new Argument(getArgumentTensor(nodeName, graph));
        } else if (isConstantTensor(nodeName, graph)) {
            operation = new Constant(getConstantTensor(nodeName, graph));
        } else {
            Onnx.NodeProto node = getNodeFromGraph(nodeName, graph);
            List<OnnxOperation> inputs = importNodeInputs(node, graph, index);
            operation = OperationMapper.get(node, inputs);
        }
        index.put(nodeName, operation);

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
            Onnx.NodeProto node = getNodeFromGraph(valueInfo.getName(), graph);
            if (node.getName().equals(name)) {
                return valueInfo;
            }
        }
        return null;
    }

    private static List<OnnxOperation> importNodeInputs(Onnx.NodeProto node,
                                                        Onnx.GraphProto graph,
                                                        OperationIndex index) {
        return node.getInputList().stream()
                .map(nodeName -> importNode(nodeName, graph, index))
                .collect(Collectors.toList());
    }

    /** Find dimension names to avoid excessive renaming while evaluating the model. */
    private static void findDimensionNames(OnnxOperation output) {
        DimensionRenamer renamer = new DimensionRenamer();
        addDimensionNameConstraints(output, renamer);
        renamer.solve();
        renameDimensions(output, renamer);
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

    private static void importExpressions(OnnxOperation output, OnnxModel model) {
        Optional<TensorFunction> function = importExpression(output, model);
        if (!function.isPresent()) {
            throw new IllegalArgumentException("No valid output function could be found.");
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
        importInputExpression(operation, model);

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

                if (name.equals(model.output())) {
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

    private static void importInputExpression(OnnxOperation operation, OnnxModel model) {
        if (operation.isInput()) {
            // All inputs must have dimensions with standard naming convention: d0, d1, ...
            OrderedTensorType standardNamingConvention = OrderedTensorType.standardType(operation.type().get());
            model.argument(operation.vespaName(), standardNamingConvention.type());
            model.requiredMacro(operation.vespaName(), standardNamingConvention.type());
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
