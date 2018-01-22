// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.framework.TensorShapeProto;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 *
 * @author bratseth
 */
public class TensorFlowImporter {

    private final OperationMapper operationMapper = new OperationMapper();

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a .pbtxt or .pb file.
     * The name of the model is taken as the db/pbtxt file name (not including the file ending).
     *
     * @param modelDir the directory containing the TensorFlow model files to import
     */
    public TensorFlowModel importModel(String modelDir) {
        try (SavedModelBundle model = SavedModelBundle.load(modelDir, "serve")) {
            return importModel(model);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model from directory '" + modelDir + "'", e);
        }
    }

    public TensorFlowModel importModel(File modelDir) {
        return importModel(modelDir.toString());
    }

    /** Imports a TensorFlow model */
    public TensorFlowModel importModel(SavedModelBundle model) {
        try {
            return importGraph(MetaGraphDef.parseFrom(model.metaGraphDef()), model);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not import TensorFlow model '" + model + "'", e);
        }
    }

    private TensorFlowModel importGraph(MetaGraphDef graph, SavedModelBundle model) {
        TensorFlowModel result = new TensorFlowModel();
        for (Map.Entry<String, SignatureDef> signatureEntry : graph.getSignatureDefMap().entrySet()) {
            TensorFlowModel.Signature signature = result.signature(signatureEntry.getKey()); // Prefer key over "methodName"

            importInputs(signatureEntry.getValue().getInputsMap(), signature);
            for (Map.Entry<String, TensorInfo> output : signatureEntry.getValue().getOutputsMap().entrySet()) {
                String outputName = output.getKey();
                try {
                    NodeDef node = getNode(nameOf(output.getValue().getName()), graph.getGraphDef());
                    importNode(node, graph.getGraphDef(), model, result);
                    signature.output(outputName, nameOf(output.getValue().getName()));
                }
                catch (IllegalArgumentException e) {
                    signature.skippedOutput(outputName, Exceptions.toMessageString(e));
                }
            }
        }
        return result;
    }

    private void importInputs(Map<String, TensorInfo> inputInfoMap, TensorFlowModel.Signature signature) {
        inputInfoMap.forEach((key, value) -> {
            String argumentName = nameOf(value.getName());
            TensorType argumentType = importTensorType(value.getTensorShape());
            // Arguments are (Placeholder) nodes, so not local to the signature:
            signature.owner().argument(argumentName, argumentType);
            signature.input(key, argumentName);
        });
    }

    private TensorType importTensorType(TensorShapeProto tensorShape) {
        TensorType.Builder b = new TensorType.Builder();
        for (TensorShapeProto.Dim dimension : tensorShape.getDimList()) {
            int dimensionSize = (int)dimension.getSize();
            if (dimensionSize >= 0)
                b.indexed("d" + b.rank(), dimensionSize);
            else
                b.indexed("d" + b.rank()); // unbound size
        }
        return b.build();
    }

    /** Recursively convert a graph of TensorFlow nodes into a Vespa tensor function expression tree */
    private TypedTensorFunction importNode(NodeDef tfNode, GraphDef graph, SavedModelBundle model, TensorFlowModel result) {
        TypedTensorFunction function = tensorFunctionOf(tfNode, graph, model, result);
        try {
            // We add all intermediate nodes imported as separate expressions. Only those referenced in a signature output
            // will be used. We parse the TensorFunction here to convert it to a RankingExpression tree
            result.expression(tfNode.getName(), new RankingExpression(tfNode.getName(), function.function().toString()));
            return function;
        }
        catch (ParseException e) {
            throw new RuntimeException("Tensorflow function " + function.function() +
                                       " cannot be parsed as a ranking expression", e);
        }
    }

    private TypedTensorFunction tensorFunctionOf(NodeDef tfNode, GraphDef graph, SavedModelBundle model, TensorFlowModel result) {
        // Import arguments lazily below, as some nodes have arguments unused arguments leading to unsupported ops
        // TODO: Implement mapping of more functions from https://www.tensorflow.org/api_docs/python/
        switch (tfNode.getOp().toLowerCase()) {
            case "add" : case "add_n" : return operationMapper.join(importArguments(tfNode, graph, model, result), ScalarFunctions.add());
            case "acos" : return operationMapper.map(importArguments(tfNode, graph, model, result), ScalarFunctions.acos());
            case "elu": return operationMapper.map(importArguments(tfNode, graph, model, result), ScalarFunctions.elu());
            case "identity" : return operationMapper.identity(tfNode, model, result);
            case "placeholder" : return operationMapper.placeholder(tfNode, result);
            case "relu": return operationMapper.map(importArguments(tfNode, graph, model, result), ScalarFunctions.relu());
            case "matmul" : return operationMapper.matmul(importArguments(tfNode, graph, model, result));
            case "sigmoid": return operationMapper.map(importArguments(tfNode, graph, model, result), ScalarFunctions.sigmoid());
            case "softmax" : return operationMapper.softmax(importArguments(tfNode, graph, model, result));
            default : throw new IllegalArgumentException("Conversion of TensorFlow operation '" + tfNode.getOp() + "' is not supported");
        }
    }

    private List<TypedTensorFunction> importArguments(NodeDef tfNode, GraphDef graph, SavedModelBundle model,
                                                      TensorFlowModel result) {
        return tfNode.getInputList().stream()
                                    .map(argNode -> importNode(getNode(nameOf(argNode), graph), graph, model, result))
                                    .collect(Collectors.toList());
    }

    private NodeDef getNode(String name, GraphDef graph) {
        return graph.getNodeList().stream()
                                  .filter(node -> node.getName().equals(name))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalArgumentException("Could not find node '" + name + "'"));
    }

    /**
     * A method signature input and output has the form name:index.
     * This returns the name part without the index.
     */
    private String nameOf(String name) {
        return name.split(":")[0];
    }

}
