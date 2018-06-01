// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.IntermediateGraph;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Argument;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.ConcatV2;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Const;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Constant;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.ExpandDims;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Identity;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.IntermediateOperation;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Join;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Map;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.MatMul;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Mean;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Merge;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.NoOp;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.PlaceholderWithDefault;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Reshape;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Select;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Shape;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Squeeze;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations.Switch;
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

public class GraphImporter {

    public static IntermediateOperation mapOperation(NodeDef node,
                                                     List<IntermediateOperation> inputs,
                                                     IntermediateGraph graph) {
        String nodeName = node.getName();
        String modelName = graph.name();
        int nodePort = IntermediateOperation.indexPartOf(nodeName);
        OrderedTensorType nodeType = TypeConverter.fromTensorFlowType(node);
        AttributeConverter attributes = AttributeConverter.convert(node);

        switch (node.getOp().toLowerCase()) {
            // array ops
            case "concatv2":    return new ConcatV2(modelName, nodeName, inputs);
            case "const":       return new Const(modelName, nodeName, inputs, attributes, nodeType);
            case "expanddims":  return new ExpandDims(modelName, nodeName, inputs);
            case "identity":    return new Identity(modelName, nodeName, inputs);
            case "placeholder": return new Argument(modelName, nodeName, nodeType);
            case "placeholderwithdefault": return new PlaceholderWithDefault(modelName, nodeName, inputs);
            case "reshape":     return new Reshape(modelName, nodeName, inputs);
            case "shape":       return new Shape(modelName, nodeName, inputs);
            case "squeeze":     return new Squeeze(modelName, nodeName, inputs, attributes);

            // control flow
            case "merge":       return new Merge(modelName, nodeName, inputs);
            case "switch":      return new Switch(modelName, nodeName, inputs, nodePort);

            // math ops
            case "add":         return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "add_n":       return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "acos":        return new Map(modelName, nodeName, inputs, ScalarFunctions.acos());
            case "div":         return new Join(modelName, nodeName, inputs, ScalarFunctions.divide());
            case "realdiv":     return new Join(modelName, nodeName, inputs, ScalarFunctions.divide());
            case "floor":       return new Map(modelName, nodeName, inputs, ScalarFunctions.floor());
            case "matmul":      return new MatMul(modelName, nodeName, inputs);
            case "maximum":     return new Join(modelName, nodeName, inputs, ScalarFunctions.max());
            case "mean":        return new Mean(modelName, nodeName, inputs, attributes);
            case "reducemean":  return new Mean(modelName, nodeName, inputs, attributes);
            case "mul":         return new Join(modelName, nodeName, inputs, ScalarFunctions.multiply());
            case "multiply":    return new Join(modelName, nodeName, inputs, ScalarFunctions.multiply());
            case "rsqrt":       return new Map(modelName, nodeName, inputs, ScalarFunctions.rsqrt());
            case "select":      return new Select(modelName, nodeName, inputs);
            case "where3":      return new Select(modelName, nodeName, inputs);
            case "sigmoid":     return new Map(modelName, nodeName, inputs, ScalarFunctions.sigmoid());
            case "squareddifference": return new Join(modelName, nodeName, inputs, ScalarFunctions.squareddifference());
            case "sub":         return new Join(modelName, nodeName, inputs, ScalarFunctions.subtract());
            case "subtract":    return new Join(modelName, nodeName, inputs, ScalarFunctions.subtract());

            // nn ops
            case "biasadd":     return new Join(modelName, nodeName, inputs, ScalarFunctions.add());
            case "elu":         return new Map(modelName, nodeName, inputs, ScalarFunctions.elu());
            case "relu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.relu());
            case "selu":        return new Map(modelName, nodeName, inputs, ScalarFunctions.selu());

            // state ops
            case "variable":    return new Constant(modelName, nodeName, nodeType);
            case "variablev2":  return new Constant(modelName, nodeName, nodeType);

            // evaluation no-ops
            case "stopgradient":return new Identity(modelName, nodeName, inputs);
            case "noop":        return new NoOp(modelName, nodeName, inputs);

        }

        IntermediateOperation op = new NoOp(modelName, node.getName(), inputs);
        op.warning("Operation '" + node.getOp() + "' is currently not implemented");
        return op;
    }

    public static IntermediateGraph importGraph(String modelName, SavedModelBundle bundle) throws IOException {
        MetaGraphDef tfGraph = MetaGraphDef.parseFrom(bundle.metaGraphDef());

        IntermediateGraph intermediateGraph = new IntermediateGraph(modelName);
        importSignatures(tfGraph, intermediateGraph);
        importOperations(tfGraph, intermediateGraph, bundle);
//        verifyOutputTypes(tfGraph, intermediateGraph);

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

    public static org.tensorflow.Tensor<?> readVariable(String name, SavedModelBundle bundle) {
        Session.Runner fetched = bundle.session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if (importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from fetching " + name +
                                            ", but got " + importedTensors.size());
        return importedTensors.get(0);
    }

}
