package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.TextFormat;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.OpDef;
import org.tensorflow.framework.SavedModel;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 * 
 * @author bratseth
 */
public class TensorFlowImporter {

    /**
     * Imports a saved TensorFlow model from a directory.
     * The model should be saved as a pbtxt file.
     * The name of the model is taken at the pbtxt file name (not including the .pbtxt ending).
     */
    public void importModel(String modelDir) {
        try {
            SavedModel.Builder builder = SavedModel.newBuilder();
            TextFormat.getParser().merge(IOUtils.createReader(modelDir + "/saved_model.pbtxt"), builder);
            //System.out.println("Read " + builder);
            importModel(builder.build());
            
            // TODO: Support binary reading:
            //SavedModel.parseFrom(new FileInputStream(modelDir + "/saved_model.pbtxt"));
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Could not open TensorFlow model directory '" + modelDir + "'", e);
        }
        
    }

    private void importModel(SavedModel model) {
        model.getMetaGraphsList().forEach(this::importGraph);
    }
    
    private void importGraph(MetaGraphDef graph) {
        System.out.println("Importing graph");
        for (Map.Entry<String, SignatureDef> signatureEntry : graph.getSignatureDefMap().entrySet()) {
            System.out.println("  Importing signature def " + signatureEntry.getKey() + 
                               " with method name " + signatureEntry.getValue().getMethodName());
            signatureEntry.getValue().getOutputsMap().values()
                    .forEach(output -> importOutput(output, signatureEntry.getValue().getMethodName(), graph.getGraphDef()));
        }
    }
    
    private void importOutput(TensorInfo output, String signatureName, GraphDef graph) {
        try {
            System.out.println("    Importing output " + output.getName());
            NodeDef node = getNode(nameOf(output.getName()), graph);
            // System.out.println("Ops:-------------");
            // graph.getStrippedOpList().getOpList().stream().forEach(s -> System.out.println(s.getName()));
            // System.out.println("-----------------");
            importNode(node, graph, "");
        }
        catch (IllegalArgumentException e) {
            System.err.println("Skipping output '" + output.getName() + "' of signature '" + signatureName + "': " + Exceptions.toMessageString(e));
        }
    }

    private ExpressionNode importNode(NodeDef tfNode, GraphDef graph, String indent) {
        System.out.println("      " + indent + "Importing node " + tfNode.getName());
        List<ExpressionNode> arguments = new ArrayList<>();
        for (String input : tfNode.getInputList())
            arguments.add(importNode(getNode(nameOf(input), graph), graph, indent + "  "));
        ExpressionNode node = expressionNodeOf(tfNode.getName(), arguments);
    }
    
    private ExpressionNode expressionNodeOf(String node, List<ExpressionNode> arguments) {
        return new TensorFunctionNode(tensorFunctionOf(node, arguments.stream()
                                                                      .map(TensorFunctionNode.TensorFunctionExpressionNode::new)
                                                                      .collect(Collectors.toList())));
    }
    
    private TensorFunction tensorFunctionOf(String node, List<TensorFunction> arguments) {
        switch (node) {
            case "add" : return new Join(arguments.get(0), arguments.get(1), ScalarFunctions.add());
            case "MatMul" : return new Matmul(arguments.get(0), arguments.get(1), ScalarFunctions.add());
        }
    }

    private NodeDef getNode(String name, GraphDef graph) {
        return graph.getNodeList().stream()
                                  .filter(node -> node.getName().equals(name))
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalArgumentException("Could not find node '" + name + "'"));
    }

    private void importOp(OpDef op, MetaGraphDef.MetaInfoDef graph) {
        System.out.println("      Importing op " + op.getName());
    }

    private OpDef getOp(String name, MetaGraphDef.MetaInfoDef graph) {
        return graph.getStrippedOpList().getOpList().stream()
                .filter(op -> op.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Could not find operation '" + name + "'"));
    }

    /**
     * An output has the form name:index.
     * This returns the name part without the index.
     */
    private String nameOf(String outputName) {
        return outputName.split(":")[0];
    }
    
    private boolean contains(String string, ProtocolStringList strings) {
        return strings.asByteStringList().stream().anyMatch(s -> s.toStringUtf8().equals(string));
    }

}
