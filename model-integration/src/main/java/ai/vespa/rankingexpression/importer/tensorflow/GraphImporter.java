// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.operations.Softmax;
import ai.vespa.rankingexpression.importer.operations.Sum;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import ai.vespa.rankingexpression.importer.IntermediateGraph;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.operations.Argument;
import ai.vespa.rankingexpression.importer.operations.ConcatV2;
import ai.vespa.rankingexpression.importer.operations.Const;
import ai.vespa.rankingexpression.importer.operations.Constant;
import ai.vespa.rankingexpression.importer.operations.ExpandDims;
import ai.vespa.rankingexpression.importer.operations.Identity;
import ai.vespa.rankingexpression.importer.operations.IntermediateOperation;
import ai.vespa.rankingexpression.importer.operations.Join;
import ai.vespa.rankingexpression.importer.operations.Map;
import ai.vespa.rankingexpression.importer.operations.MatMul;
import ai.vespa.rankingexpression.importer.operations.Mean;
import ai.vespa.rankingexpression.importer.operations.Merge;
import ai.vespa.rankingexpression.importer.operations.NoOp;
import ai.vespa.rankingexpression.importer.operations.PlaceholderWithDefault;
import ai.vespa.rankingexpression.importer.operations.Reshape;
import ai.vespa.rankingexpression.importer.operations.Select;
import ai.vespa.rankingexpression.importer.operations.Shape;
import ai.vespa.rankingexpression.importer.operations.Squeeze;
import ai.vespa.rankingexpression.importer.operations.Switch;
import com.yahoo.tensor.functions.ScalarFunctions;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts a TensorFlow graph to a Vespa IntermediateGraph which is the basis
 * for generating Vespa ranking expressions.
 *
 * @author lesters
 */
class GraphImporter {

    private static IntermediateOperation mapOperation(NodeDef node,
                                                     List<IntermediateOperation> inputs,
                                                     IntermediateGraph graph) {
        String nodeName = node.getName();
        String modelName = graph.name();
        int nodePort = IntermediateOperation.indexPartOf(nodeName);
        OrderedTensorType nodeType = TypeConverter.typeFrom(node);
        AttributeConverter attributes = AttributeConverter.convert(node);

        switch (node.getOp().toLowerCase()) {
            // array ops
            case "concatv2":    return new ConcatV2(modelName, nodeName, inputs);
            case "const":       return new Const(modelName, nodeName, inputs, attributes, nodeType);
            case "expanddims":  return new ExpandDims(modelName, nodeName, inputs);
            case "identity":    return new Identity(modelName, nodeName, inputs);
            case "placeholder": return new Argument(modelName, nodeName, nodeType);
            case "placeholderwithdefault": return new PlaceholderWithDefault(modelName, nodeName, inputs);
            case "reshape":     return new Reshape(modelName, nodeName, inputs, attributes);
            case "shape":       return new Shape(modelName, nodeName, inputs);
            case "squeeze":     return new Squeeze(modelName, nodeName, inputs, attributes);

            // control flow
            case "merge":       return new Merge(modelName, nodeName, inputs);
            case "switch":      return new Switch(modelName, nodeName, inputs, nodePort);

            // math ops
            case "abs":         return new Map(modelName, nodeName, inputs, ScalarFunctions.abs());
            case "acos":        return new Map(modelName, nodeName, inputs, ScalarFunctions.acos());
            case "add":         return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "add_n":       return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "asin":        return new Map(modelName, nodeName, inputs, ScalarFunctions.asin());
            case "atan":        return new Map(modelName, nodeName, inputs, ScalarFunctions.atan());
            case "ceil":        return new Map(modelName, nodeName, inputs, ScalarFunctions.ceil());
            case "cos":         return new Map(modelName, nodeName, inputs, ScalarFunctions.cos());
            case "div":         return new Join(modelName, nodeName, inputs, ScalarFunctions.divide());
            case "exp":         return new Map(modelName, nodeName, inputs, ScalarFunctions.exp());
            case "realdiv":     return new Join(modelName, nodeName, inputs, ScalarFunctions.divide());
            case "floor":       return new Map(modelName, nodeName, inputs, ScalarFunctions.floor());
            case "log":         return new Map(modelName, nodeName, inputs, ScalarFunctions.log());
            case "matmul":      return new MatMul(modelName, nodeName, inputs);
            case "maximum":     return new Join(modelName, nodeName, inputs, ScalarFunctions.max());
            case "mean":        return new Mean(modelName, nodeName, inputs, attributes);
            case "reducemean":  return new Mean(modelName, nodeName, inputs, attributes);
            case "mul":         return new Join(modelName, nodeName, inputs, ScalarFunctions.multiply());
            case "multiply":    return new Join(modelName, nodeName, inputs, ScalarFunctions.multiply());
            case "negate":      return new Map(modelName, nodeName, inputs, ScalarFunctions.neg());
            case "reciprocal":  return new Map(modelName, nodeName, inputs, ScalarFunctions.reciprocal());
            case "rsqrt":       return new Map(modelName, nodeName, inputs, ScalarFunctions.rsqrt());
            case "select":      return new Select(modelName, nodeName, inputs);
            case "where3":      return new Select(modelName, nodeName, inputs);
            case "sigmoid":     return new Map(modelName, nodeName, inputs, ScalarFunctions.sigmoid());
            case "sin":         return new Map(modelName, nodeName, inputs, ScalarFunctions.sin());
            case "squareddifference": return new Join(modelName, nodeName, inputs, ScalarFunctions.squareddifference());
            case "sub":         return new Join(modelName, nodeName, inputs, ScalarFunctions.subtract());
            case "subtract":    return new Join(modelName, nodeName, inputs, ScalarFunctions.subtract());
            case "sum":         return new Sum(modelName, nodeName, inputs, attributes);
            case "square":      return new Map(modelName, nodeName, inputs, ScalarFunctions.square());
            case "sqrt":        return new Map(modelName, nodeName, inputs, ScalarFunctions.sqrt());
            case "tan":         return new Map(modelName, nodeName, inputs, ScalarFunctions.tan());
            case "tanh":        return new Map(modelName, nodeName, inputs, ScalarFunctions.tanh());

            // nn ops
            case "biasadd":     return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "elu":         return new Map(modelName, nodeName, inputs, ScalarFunctions.elu());
            case "relu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.relu());
            case "selu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.selu());
            case "softmax":     return new Softmax(modelName, nodeName, inputs, attributes);

            // state ops
            case "variable":    return new Constant(modelName, nodeName, nodeType);
            case "variablev2":  return new Constant(modelName, nodeName, nodeType);
            case "varhandleop": return new Constant(modelName, nodeName, nodeType);
            case "readvariableop":return new Identity(modelName, nodeName, inputs);

            // evaluation no-ops
            case "stopgradient":return new Identity(modelName, nodeName, inputs);
            case "noop":        return new NoOp(modelName, nodeName, inputs);

        }

        IntermediateOperation op = new NoOp(modelName, node.getName(), inputs);
        op.warning("Operation '" + node.getOp() + "' is currently not implemented");
        return op;
    }

    static IntermediateGraph importGraph(String modelName, SavedModelBundle bundle) throws IOException {
        MetaGraphDef tfGraph = MetaGraphDef.parseFrom(bundle.metaGraphDef());

        IntermediateGraph intermediateGraph = new IntermediateGraph(modelName);
        importSignatures(tfGraph, intermediateGraph);
        importOperations(tfGraph, intermediateGraph, bundle);
        verifyOutputTypes(tfGraph, intermediateGraph);

        return intermediateGraph;
    }

    private static void importSignatures(MetaGraphDef tfGraph, IntermediateGraph intermediateGraph) {
        for (java.util.Map.Entry<String, SignatureDef> signatureEntry : tfGraph.getSignatureDefMap().entrySet()) {
            String signatureName = signatureEntry.getKey();
            java.util.Map<String, TensorInfo> inputInfoMap = signatureEntry.getValue().getInputsMap();
            for (java.util.Map.Entry<String, TensorInfo> input : inputInfoMap.entrySet()) {
                String inputName = input.getKey();
                String nodeName = input.getValue().getName();
                intermediateGraph.inputs(signatureName).put(inputName, IntermediateOperation.namePartOf(nodeName));
            }
            java.util.Map<String, TensorInfo> outputInfoMap = signatureEntry.getValue().getOutputsMap();
            for (java.util.Map.Entry<String, TensorInfo> output : outputInfoMap.entrySet()) {
                String outputName = output.getKey();
                String nodeName = output.getValue().getName();
                intermediateGraph.outputs(signatureName).put(outputName, IntermediateOperation.namePartOf(nodeName));
            }
        }
    }

    private static void importOperations(MetaGraphDef tfGraph,
                                         IntermediateGraph intermediateGraph,
                                         SavedModelBundle bundle) {
        for (String signatureName : intermediateGraph.signatures()) {
            for (String outputName : intermediateGraph.outputs(signatureName).values()) {
                importOperation(outputName, tfGraph.getGraphDef(), intermediateGraph, bundle);
            }
        }
    }

    private static IntermediateOperation importOperation(String nodeName,
                                                         GraphDef tfGraph,
                                                         IntermediateGraph intermediateGraph,
                                                         SavedModelBundle bundle) {
        if (intermediateGraph.alreadyImported(nodeName)) {
            return intermediateGraph.get(nodeName);
        }
        NodeDef node = getTensorFlowNodeFromGraph(IntermediateOperation.namePartOf(nodeName), tfGraph);
        List<IntermediateOperation> inputs = importOperationInputs(node, tfGraph, intermediateGraph, bundle);
        IntermediateOperation operation = mapOperation(node, inputs, intermediateGraph);
        intermediateGraph.put(nodeName, operation);

        List<IntermediateOperation> controlInputs = importControlInputs(node, tfGraph, intermediateGraph, bundle);
        if (controlInputs.size() > 0) {
            operation.setControlInputs(controlInputs);
        }

        if (operation.isConstant()) {
            operation.setConstantValueFunction(
                    type -> new TensorValue(TensorConverter.toVespaTensor(readVariable(nodeName, bundle), type)));
        }

        return operation;
    }

    private static List<IntermediateOperation> importOperationInputs(NodeDef node,
                                                                     GraphDef tfGraph,
                                                                     IntermediateGraph intermediateGraph,
                                                                     SavedModelBundle bundle) {
        return node.getInputList().stream()
                .filter(name -> ! isControlDependency(name))
                .map(nodeName -> importOperation(nodeName, tfGraph, intermediateGraph, bundle))
                .collect(Collectors.toList());
    }

    private static List<IntermediateOperation> importControlInputs(NodeDef node,
                                                                   GraphDef tfGraph,
                                                                   IntermediateGraph intermediateGraph,
                                                                   SavedModelBundle bundle) {
        return node.getInputList().stream()
                .filter(nodeName -> isControlDependency(nodeName))
                .map(nodeName -> importOperation(nodeName, tfGraph, intermediateGraph, bundle))
                .collect(Collectors.toList());
    }

    private static boolean isControlDependency(String name) {
        return name.startsWith("^");
    }

    private static NodeDef getTensorFlowNodeFromGraph(String name, GraphDef tfGraph) {
        for (NodeDef node : tfGraph.getNodeList()) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        throw new IllegalArgumentException("Could not find node '" + name + "'");
    }

    static org.tensorflow.Tensor<?> readVariable(String name, SavedModelBundle bundle) {
        Session.Runner fetched = bundle.session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if (importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from fetching " + name +
                                            ", but got " + importedTensors.size());
        return importedTensors.get(0);
    }

    private static void verifyOutputTypes(MetaGraphDef tfGraph, IntermediateGraph intermediateGraph) {
        for (String signatureName : intermediateGraph.signatures()) {
            for (String outputName : intermediateGraph.outputs(signatureName).values()) {
                IntermediateOperation operation = intermediateGraph.get(outputName);
                NodeDef node = getTensorFlowNodeFromGraph(IntermediateOperation.namePartOf(operation.name()), tfGraph.getGraphDef());
                OrderedTensorType type = operation.type().orElseThrow(
                        () -> new IllegalArgumentException("Output of '" + outputName + "' has no type."));
                TypeConverter.verifyType(node, type);
            }
        }

    }

}
