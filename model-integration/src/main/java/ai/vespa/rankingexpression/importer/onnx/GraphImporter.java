// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.operations.Gemm;
import ai.vespa.rankingexpression.importer.operations.ConcatReduce;
import ai.vespa.rankingexpression.importer.operations.OnnxConcat;
import ai.vespa.rankingexpression.importer.operations.Reduce;
import ai.vespa.rankingexpression.importer.operations.Select;
import ai.vespa.rankingexpression.importer.operations.Softmax;
import ai.vespa.rankingexpression.importer.operations.Squeeze;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.operations.Argument;
import ai.vespa.rankingexpression.importer.operations.Constant;
import ai.vespa.rankingexpression.importer.operations.Identity;
import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import ai.vespa.rankingexpression.importer.operations.Join;
import ai.vespa.rankingexpression.importer.operations.Map;
import ai.vespa.rankingexpression.importer.operations.MatMul;
import ai.vespa.rankingexpression.importer.operations.NoOp;
import ai.vespa.rankingexpression.importer.operations.Reshape;
import ai.vespa.rankingexpression.importer.operations.Shape;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.tensor.functions.ScalarFunctions;
import onnx.Onnx;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts an ONNX graph to a Vespa IntermediateGraph which is the basis
 * for generating Vespa ranking expressions.
 *
 * @author lesters
 */
class GraphImporter {

    private static final Value eluAlpha = DoubleValue.frozen(1.0);
    private static final Value seluAlpha = DoubleValue.frozen(1.6732632423543772848170429916717);
    private static final Value seluGamma = DoubleValue.frozen(1.0507009873554804934193349852946);
    private static final Value leakyReluAlpha = DoubleValue.frozen(0.01);

    private static IntermediateOperation mapOperation(Onnx.NodeProto node,
                                                     List<IntermediateOperation> inputs,
                                                     IntermediateGraph graph) {
        String type = node.getOpType();
        String modelName = graph.name();
        String nodeName = getNodeName(node);
        AttributeConverter attributes = AttributeConverter.convert(node);
        return mapOperation(type, inputs, modelName, nodeName, attributes);
    }

    static IntermediateOperation mapOperation(String opType,
                                              List<IntermediateOperation> inputs,
                                              String modelName,
                                              String nodeName,
                                              AttributeConverter attributes) {
        switch (opType.toLowerCase()) {
            case "abs":         return new Map(modelName, nodeName, inputs, ScalarFunctions.abs());
            case "acos":        return new Map(modelName, nodeName, inputs, ScalarFunctions.acos());
            case "add":         return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "asin":        return new Map(modelName, nodeName, inputs, ScalarFunctions.asin());
            case "atan":        return new Map(modelName, nodeName, inputs, ScalarFunctions.atan());
            case "ceil":        return new Map(modelName, nodeName, inputs, ScalarFunctions.ceil());
            case "concat":      return new OnnxConcat(modelName, nodeName, inputs, attributes);
            case "cos":         return new Map(modelName, nodeName, inputs, ScalarFunctions.cos());
            case "div":         return new Join(modelName, nodeName, inputs, ScalarFunctions.divide());
            case "elu":         return new Map(modelName, nodeName, inputs, ScalarFunctions.elu(attributes.get("alpha").orElse(eluAlpha).asDouble()));
            case "equal":       return new Join(modelName, nodeName, inputs, ScalarFunctions.equal());
            case "exp":         return new Map(modelName, nodeName, inputs, ScalarFunctions.exp());
            case "floor":       return new Map(modelName, nodeName, inputs, ScalarFunctions.floor());
            case "gemm":        return new Gemm(modelName, nodeName, inputs, attributes);
            case "greater":     return new Join(modelName, nodeName, inputs, ScalarFunctions.greater());
            case "identity":    return new Identity(modelName, nodeName, inputs);
            case "less":        return new Join(modelName, nodeName, inputs, ScalarFunctions.less());
            case "log":         return new Map(modelName, nodeName, inputs, ScalarFunctions.log());
            case "matmul":      return new MatMul(modelName, nodeName, inputs);
            case "max":         return new ConcatReduce(modelName, nodeName, inputs, com.yahoo.tensor.functions.Reduce.Aggregator.max);
            case "min":         return new ConcatReduce(modelName, nodeName, inputs, com.yahoo.tensor.functions.Reduce.Aggregator.min);
            case "mean":        return new ConcatReduce(modelName, nodeName, inputs, com.yahoo.tensor.functions.Reduce.Aggregator.avg);
            case "mul":         return new Join(modelName, nodeName, inputs, ScalarFunctions.multiply());
            case "neg":         return new Map(modelName, nodeName, inputs, ScalarFunctions.neg());
            case "pow":         return new Join(modelName, nodeName, inputs, ScalarFunctions.pow());
            case "reshape":     return new Reshape(modelName, nodeName, inputs, attributes);
            case "reducel1":    return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum, ScalarFunctions.abs(), null);
            case "reducel2":    return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum, ScalarFunctions.square(), ScalarFunctions.sqrt());
            case "reducelogsum":return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum, null, ScalarFunctions.log());
            case "reducelogsumexp": return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum, ScalarFunctions.exp(), ScalarFunctions.log());
            case "reducemax":   return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.max);
            case "reducemean":  return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.avg);
            case "reducemin":   return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.min);
            case "reduceprod":  return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.prod);
            case "reducesum":   return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum);
            case "reducesumsquare": return new Reduce(modelName, nodeName, inputs, attributes, com.yahoo.tensor.functions.Reduce.Aggregator.sum, ScalarFunctions.square(), null);
            case "reciprocal":  return new Map(modelName, nodeName, inputs, ScalarFunctions.reciprocal());
            case "relu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.relu());
            case "selu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.selu(attributes.get("gamma").orElse(seluGamma).asDouble(), attributes.get("alpha").orElse(seluAlpha).asDouble()));
            case "leakyrelu":   return new Map(modelName, nodeName, inputs, ScalarFunctions.leakyrelu(attributes.get("alpha").orElse(leakyReluAlpha).asDouble()));
            case "shape":       return new Shape(modelName, nodeName, inputs);
            case "sigmoid":     return new Map(modelName, nodeName, inputs, ScalarFunctions.sigmoid());
            case "sin":         return new Map(modelName, nodeName, inputs, ScalarFunctions.sin());
            case "softmax":     return new Softmax(modelName, nodeName, inputs, attributes);
            case "sub":         return new Join(modelName, nodeName, inputs, ScalarFunctions.subtract());
            case "squeeze":     return new Squeeze(modelName, nodeName, inputs, attributes);
            case "sqrt":        return new Map(modelName, nodeName, inputs, ScalarFunctions.sqrt());
            case "square":      return new Map(modelName, nodeName, inputs, ScalarFunctions.square());
            case "where":       return new Select(modelName, nodeName, inputs);
            case "tan":         return new Map(modelName, nodeName, inputs, ScalarFunctions.tan());
            case "tanh":        return new Map(modelName, nodeName, inputs, ScalarFunctions.tanh());
        }

        IntermediateOperation op = new NoOp(modelName, nodeName, inputs);
        op.warning("Operation '" + opType + "' is currently not implemented");
        return op;
    }

    static IntermediateGraph importGraph(String modelName, Onnx.ModelProto model) {
        Onnx.GraphProto onnxGraph = model.getGraph();

        IntermediateGraph intermediateGraph = new IntermediateGraph(modelName);
        importOperations(onnxGraph, intermediateGraph);
        verifyNoWarnings(intermediateGraph);
        verifyOutputTypes(onnxGraph, intermediateGraph);

        return intermediateGraph;
    }

    private static void importOperations(Onnx.GraphProto onnxGraph, IntermediateGraph intermediateGraph) {
        for (Onnx.ValueInfoProto valueInfo : onnxGraph.getOutputList()) {
            importOperation(valueInfo.getName(), onnxGraph, intermediateGraph);
        }
    }

    private static IntermediateOperation importOperation(String name,
                                                         Onnx.GraphProto onnxGraph,
                                                         IntermediateGraph intermediateGraph) {
        if (intermediateGraph.alreadyImported(name)) {
            return intermediateGraph.get(name);
        }
        IntermediateOperation operation;
        if (isArgumentTensor(name, onnxGraph)) {
            Onnx.ValueInfoProto valueInfoProto = getArgumentTensor(name, onnxGraph);
            if (valueInfoProto == null)
                throw new IllegalArgumentException("Could not find argument tensor '" + name + "'");
            OrderedTensorType type = TypeConverter.typeFrom(valueInfoProto.getType());
            operation = new Argument(intermediateGraph.name(), valueInfoProto.getName(), type);

            intermediateGraph.inputs(IntermediateGraph.defaultSignature())
                    .put(IntermediateOperation.namePartOf(name), operation.vespaName());

        } else if (isConstantTensor(name, onnxGraph)) {
            Onnx.TensorProto tensorProto = getConstantTensor(name, onnxGraph);
            OrderedTensorType defaultType = TypeConverter.typeFrom(tensorProto);
            operation = new Constant(intermediateGraph.name(), name, defaultType);
            operation.setConstantValueFunction(type -> new TensorValue(TensorConverter.toVespaTensor(tensorProto, type)));

        } else {
            Onnx.NodeProto node = getNodeFromGraph(name, onnxGraph);
            List<IntermediateOperation> inputs = importOperationInputs(node, onnxGraph, intermediateGraph);
            operation = mapOperation(node, inputs, intermediateGraph);

            // propagate constant values if all inputs are constant
            if (operation.isConstant()) {
                operation.setConstantValueFunction(operation::evaluateAsConstant);
            }

            if (isOutputNode(name, onnxGraph)) {
                intermediateGraph.outputs(IntermediateGraph.defaultSignature())
                        .put(IntermediateOperation.namePartOf(name), operation.name());
            }
        }
        intermediateGraph.put(operation.name(), operation);

        return operation;
    }

    // Rules for initializers in ONNX:
    // When an initializer has the same name as a graph input, it specifies a default value for that input.
    // When an initializer has a name different from all graph inputs, it specifies a constant value.

    private static boolean isArgumentTensor(String name, Onnx.GraphProto graph) {
        Onnx.ValueInfoProto value = getArgumentTensor(name, graph);
        Onnx.TensorProto tensor = getConstantTensor(name, graph);
        return value != null && tensor == null;
    }

    private static boolean isConstantTensor(String name, Onnx.GraphProto graph) {
        return getConstantTensor(name, graph) != null;
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
            String nodeName = IntermediateOperation.namePartOf(valueInfo.getName());
            if (nodeName.equals(name)) {
                return valueInfo;
            }
        }
        return null;
    }

    private static List<IntermediateOperation> importOperationInputs(Onnx.NodeProto node,
                                                                     Onnx.GraphProto onnxGraph,
                                                                     IntermediateGraph intermediateGraph) {
        return node.getInputList().stream()
                .map(nodeName -> importOperation(nodeName, onnxGraph, intermediateGraph))
                .collect(Collectors.toList());
    }

    private static void verifyNoWarnings(IntermediateGraph intermediateGraph) {
        for (java.util.Map.Entry<String, String> output : intermediateGraph.outputs(IntermediateGraph.defaultSignature()).entrySet()) {
            IntermediateOperation operation = intermediateGraph.get(output.getValue());
            Set<String> warnings = getWarnings(operation);
            if (warnings.size() > 0) {
                throw new IllegalArgumentException("Could not import " + intermediateGraph.name() + ": " + String.join("\n", warnings));
            }
        }
    }

    private static void verifyOutputTypes(Onnx.GraphProto onnxGraph, IntermediateGraph intermediateGraph) {
        for (java.util.Map.Entry<String, String> output : intermediateGraph.outputs(IntermediateGraph.defaultSignature()).entrySet()) {
            IntermediateOperation operation = intermediateGraph.get(output.getValue());
            Onnx.ValueInfoProto onnxNode = getOutputNode(output.getKey(), onnxGraph);
            OrderedTensorType type = operation.type().orElseThrow(
                        () -> new IllegalArgumentException("Output of '" + output.getValue() + "' has no type."));
            TypeConverter.verifyType(onnxNode.getType(), type);
        }
    }

    private static Onnx.NodeProto getNodeFromGraph(String nodeName, Onnx.GraphProto graph) {
        Optional<Onnx.NodeProto> node = getNodeFromGraphNames(nodeName, graph);
        if (node.isPresent())
            return node.get();

        node = getNodeFromGraphOutputs(nodeName, graph);
        if (node.isPresent())
            return node.get();

        node = getNodeFromGraphInputs(nodeName, graph);
        if (node.isPresent())
            return node.get();

        throw new IllegalArgumentException("Node '" + nodeName + "' not found in ONNX graph");
    }

    private static Optional<Onnx.NodeProto> getNodeFromGraphOutputs(String nodeName, Onnx.GraphProto graph) {
        return graph.getNodeList().stream().filter(node ->
                node.getOutputList().stream().anyMatch(name -> name.equals(nodeName))).findFirst();
    }

    private static Optional<Onnx.NodeProto> getNodeFromGraphInputs(String nodeName, Onnx.GraphProto graph) {
        return graph.getNodeList().stream().filter(node ->
                node.getInputList().stream().anyMatch(name -> name.equals(nodeName))).findFirst();
    }

    private static Optional<Onnx.NodeProto> getNodeFromGraphNames(String nodeName, Onnx.GraphProto graph) {
        return graph.getNodeList().stream().filter(node -> node.getName().equals(nodeName)).findFirst();
    }

    private static String getNodeName(Onnx.NodeProto node) {
        String nodeName = node.getName();
        if (nodeName.length() > 0)
            return nodeName;
        if (node.getOutputCount() == 1)
            return node.getOutput(0);
        throw new IllegalArgumentException("Unable to find a suitable name for node '" + node.toString() + "'. " +
                "Either no explicit name given or no single output name.");
    }

    private static Set<String> getWarnings(IntermediateOperation op) {
        Set<String> warnings = new HashSet<>(op.warnings());
        for (IntermediateOperation input : op.inputs()) {
            warnings.addAll(getWarnings(input));
        }
        return warnings;
    }

}
