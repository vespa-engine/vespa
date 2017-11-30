package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.protobuf.TextFormat;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.SavedModel;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.framework.TensorShapeProto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
     * The name of the model is taken at the pbtxt file name (not including the .pbtxt ending).
     */
    public List<RankingExpression> importModel(String modelDir) {
        try {
            SavedModel.Builder builder = SavedModel.newBuilder();
            TextFormat.getParser().merge(IOUtils.createReader(modelDir + "/saved_model.pbtxt"), builder);
            return importModel(builder.build());
            
            // TODO: Support binary reading:
            //SavedModel.parseFrom(new FileInputStream(modelDir + "/saved_model.pbtxt"));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not open TensorFlow model directory '" + modelDir + "'", e);
        }
        
    }

    /** Import all declared inputs in all the graphs in the given model */
    private List<RankingExpression> importModel(SavedModel model) {
        // TODO: Handle name conflicts between output keys in different graphs?
        return model.getMetaGraphsList().stream()
                                        .flatMap(graph -> importGraph(graph).stream())
                                        .collect(Collectors.toList());
    }

    private List<RankingExpression> importGraph(MetaGraphDef graph) {
        System.out.println("Importing graph");
        List<RankingExpression> expressions = new ArrayList<>();
        for (Map.Entry<String, SignatureDef> signatureEntry : graph.getSignatureDefMap().entrySet()) {
            System.out.println("  Importing signature def " + signatureEntry.getKey() + 
                               " with method name " + signatureEntry.getValue().getMethodName());
            Map<String, TensorType> inputs = importInputs(signatureEntry.getValue().getInputsMap());
            for (Map.Entry<String, TensorInfo> output : signatureEntry.getValue().getOutputsMap().entrySet()) {
                try {
                    ExpressionNode result = importOutput(output.getValue(),
                                                         inputs,
                                                         graph.getGraphDef());
                    expressions.add(new RankingExpression(output.getKey(), result));
                }
                catch (IllegalArgumentException e) {
                    System.err.println("Skipping output '" + output.getValue().getName() + "' of signature '" + // TODO: Log, or ...
                                       signatureEntry.getValue().getMethodName() +
                                       "': " + Exceptions.toMessageString(e));
                }
            }
        }
        return expressions;
    }

    private Map<String, TensorType> importInputs(Map<String, TensorInfo> inputInfoMap) {
        Map<String, TensorType> inputs = new HashMap<>();
        inputInfoMap.forEach((key, value) -> inputs.put(nameOf(value.getName()), 
                                                        importTensorType(value.getTensorShape())));
        return inputs;
    }
    
    static TensorType importTensorType(TensorShapeProto tensorShape) {
        TensorType.Builder b = new TensorType.Builder();
        for (int i = 0; i < tensorShape.getDimCount(); i++) {
            int dimensionSize = (int) tensorShape.getDim(i).getSize();
            if (dimensionSize >= 0)
                b.indexed("d" + i, dimensionSize);
            else
                b.indexed("d" + i); // unbound size
        }
        return b.build();
    }

    private ExpressionNode importOutput(TensorInfo output, Map<String, TensorType> inputs, GraphDef graph) {
        System.out.println("    Importing output " + output.getName());
        NodeDef node = getNode(nameOf(output.getName()), graph);
        return new TensorFunctionNode(importNode(node, inputs, graph, "").function());
    }

    /** Recursively convert a graph of TensorFlow nodes into a Vespa tensor function expression tree */
    private TypedTensorFunction importNode(NodeDef tfNode, Map<String, TensorType> inputs, GraphDef graph, String indent) {
        System.out.println("      " + indent + "Importing node " + tfNode.getName() + " with operation " + tfNode.getOp());
        return tensorFunctionOf(tfNode, inputs, graph, indent);
    }
    
    private TypedTensorFunction tensorFunctionOf(NodeDef tfNode,
                                                 Map<String, TensorType> inputs,
                                                 GraphDef graph,
                                                 String indent) {
        // Import arguments lazily below, as some nodes have arguments unused arguments leading to unsupported ops
        // TODO: Implement mapping of more functions from https://www.tensorflow.org/api_docs/python/
        switch (tfNode.getOp().toLowerCase()) {
            case "add" : case "add_n" : return operationMapper.join(importArguments(tfNode, inputs, graph, indent), ScalarFunctions.add());
            case "acos" : return operationMapper.map(importArguments(tfNode, inputs, graph, indent), ScalarFunctions.acos());
            case "identity" : return operationMapper.identity(tfNode, inputs);
            case "matmul" : return operationMapper.matmul(importArguments(tfNode, inputs, graph, indent));
            case "softmax" : return operationMapper.softmax(importArguments(tfNode, inputs, graph, indent));
            default : throw new IllegalArgumentException("Conversion of TensorFlow operation '" + tfNode.getOp() + "' is not supported");
        }
    }
    
    private List<TypedTensorFunction> importArguments(NodeDef tfNode, Map<String, TensorType> inputs, GraphDef graph, String indent) {
        return tfNode.getInputList().stream()
                                    .map(argNode -> importNode(getNode(nameOf(argNode), graph), inputs, graph, indent + "  "))
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
