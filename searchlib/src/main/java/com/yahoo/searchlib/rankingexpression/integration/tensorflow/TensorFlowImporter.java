package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
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
     * The model should be saved as a pbtxt file.
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
        // We add all intermediate nodes imported as separate expressions. Only those referenced in a signature output
        // will be used
        result.expression(tfNode.getName(), new RankingExpression(tfNode.getName(), new TensorFunctionNode(function.function())));
        return function;
    }

    private TypedTensorFunction tensorFunctionOf(NodeDef tfNode, GraphDef graph, SavedModelBundle model, TensorFlowModel result) {
        // Import arguments lazily below, as some nodes have arguments unused arguments leading to unsupported ops
        // TODO: Implement mapping of more functions from https://www.tensorflow.org/api_docs/python/
        switch (tfNode.getOp().toLowerCase()) {
            case "add" : case "add_n" : return operationMapper.join(importArguments(tfNode, graph, model, result), ScalarFunctions.add());
            case "acos" : return operationMapper.map(importArguments(tfNode, graph, model, result), ScalarFunctions.acos());
            case "placeholder" : return operationMapper.placeholder(tfNode, result);
            case "identity" : return operationMapper.identity(tfNode, model, result);
            case "matmul" : return operationMapper.matmul(importArguments(tfNode, graph, model, result));
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
