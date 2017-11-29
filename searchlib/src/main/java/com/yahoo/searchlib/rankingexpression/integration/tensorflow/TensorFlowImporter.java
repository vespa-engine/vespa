package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ProtocolStringList;
import com.google.protobuf.TextFormat;
import com.yahoo.io.IOUtils;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.Softmax;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.yolean.Exceptions;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.GraphDef;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.OpDef;
import org.tensorflow.framework.SavedModel;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.framework.TensorShapeProto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

/**
 * Converts a saved TensorFlow model into a ranking expression and set of constants.
 * 
 * @author bratseth
 */
public class TensorFlowImporter {

    /*
       A note on conversion from implicitly numbered to explicitly named dimensions:
       Vespa tensor dimensions are explicitly named and thus have an explicit notion of being
       'the same' or not of some dimension in another tensor. Since TF lacks this, each operation
       comes with a built-in definition of sameness. We mirror this by wrapping the Vespa tensor operation
       around dimension renaming operations which mirrors those built into the TF operation definitions.
       
       To do this we need a naming convention: We maintain a naming of each tensor where the 'outermost'
       dimension is named 'd0', the second outer most 'd1' and so on. Arguments are renamed to match the operation
       and the result is then renamed again (if necessary) to recover this convention across a full nested
       computation.
       
       This requires us to track tensor types throughout the conversion.
     */
    
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
    
    private TensorType importTensorType(TensorShapeProto tensorShape) {
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
            case "add" : case "add_n" : return join(importArguments(tfNode, inputs, graph, indent), ScalarFunctions.add());
            case "acos" : return map(importArguments(tfNode, inputs, graph, indent), ScalarFunctions.acos());
            case "identity" : return identity(tfNode, inputs);
            case "matmul" : return matmul(importArguments(tfNode, inputs, graph, indent));
            case "softmax" : return softmax(importArguments(tfNode, inputs, graph, indent));
            default : throw new IllegalArgumentException("Conversion of TensorFlow operation '" + tfNode.getOp() + "' is not supported");
        }
    }
    
    private List<TypedTensorFunction> importArguments(NodeDef tfNode, Map<String, TensorType> inputs, GraphDef graph, String indent) {
        return tfNode.getInputList().stream()
                                    .map(argNode -> importNode(getNode(nameOf(argNode), graph), inputs, graph, indent + "  "))
                                    .collect(Collectors.toList());
    }
    
    private TypedTensorFunction join(List<TypedTensorFunction> arguments, DoubleBinaryOperator doubleFunction) {
        // Note that this generalizes the corresponding TF function as it does not verify that the tensor
        // types are the same, with the assumption that this already happened on the TF side
        // (and if not, this should do the right thing anyway)
        ensureArguments(2, arguments, "join");
        TypedTensorFunction a = arguments.get(0);
        TypedTensorFunction b = arguments.get(0);

        TensorType resultType = Join.outputType(a.type(), b.type());
        Join function = new Join(a.function(), b.function(), doubleFunction);
        return new TypedTensorFunction(resultType, function);
    }

    private TypedTensorFunction map(List<TypedTensorFunction> arguments, DoubleUnaryOperator doubleFunction) {
        ensureArguments(1, arguments, "apply");
        TypedTensorFunction a = arguments.get(0);

        TensorType resultType = com.yahoo.tensor.functions.Map.outputType(a.type());
        com.yahoo.tensor.functions.Map function = new com.yahoo.tensor.functions.Map(a.function(), doubleFunction);
        return new TypedTensorFunction(resultType, function);
    }

    private TypedTensorFunction identity(NodeDef tfNode, Map<String, TensorType> inputs) {
        // TODO: Verify with TF documentation
        String name;
        TensorType inputType;
        if (tfNode.getName().endsWith("/read")) { // A node reading a variable supplied with this model TODO: We need to turn those into constants
            if (tfNode.getInputList().size() != 1)
                throw new IllegalArgumentException("A Variable/read node must have one input but has " +
                                                   tfNode.getInputList().size());
            name = tfNode.getInput(0);
            AttrValue shapes = tfNode.getAttrMap().get("_output_shapes");
            if (shapes == null)
                throw new IllegalArgumentException("Referenced variable '" + name + " is missing a tensor output shape");
            inputType = importTensorType(shapes.getList().getShape(0));
        }
        else { // a referenced input (query or document tensor) TODO: How to map to attribute/query name
            name = tfNode.getName();
            inputType = inputs.get(name);
            if (inputType == null)
                throw new IllegalArgumentException("An identity operation node is referencing input '" + name +
                                                   "', but there is no such input");
        }
        return new TypedTensorFunction(inputType, new VariableTensor(name));
    }

    private TypedTensorFunction matmul(List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "matmul");
        TypedTensorFunction a = arguments.get(0);
        TypedTensorFunction b = arguments.get(0);
        if (a.type().rank() < 2 || b.type.rank() < 2) 
            throw new IllegalArgumentException("Tensors in matmul must have rank of at least 2");
        if (a.type().rank() != b.type.rank())
            throw new IllegalArgumentException("Tensors in matmul must have the same rank");

        // Let the second-to-last dimension of the second tensor be the same as the last dimension of the first
        // and the last dimension of the second argument be not present in the first argument, while leaving the 
        // rest of the dimensions the same. Such is the way of implicit dimension name tensor multiplication.

        // TODO: Check if transpose_a or transpose_b is set and rename differently accordingly

        String beforeLastDim = "d" + (a.type().rank() - 1);
        String lastDim = "d" + a.type().rank();
        String afterLastDim = "d" + (a.type().rank() + 1);
        
        Rename renamedB = new Rename(b.function(), ImmutableList.of(beforeLastDim, lastDim), 
                                                   ImmutableList.of(lastDim, afterLastDim));
        Matmul matmul = new Matmul(a.function(), renamedB, lastDim);
        return new TypedTensorFunction(Matmul.outputType(a.type(), b.type(), lastDim), 
                                       new Rename(matmul, afterLastDim, lastDim));
    }

    private TypedTensorFunction softmax(List<TypedTensorFunction> arguments) {
        ensureArguments(1, arguments, "softmax");
        TypedTensorFunction a = arguments.get(0);
        // TODO: Read the "dim" parameter and use it to decide dimension if set and != -1
        String dimension = "d" + (a.type().rank() - 1);
        Softmax softmax = new Softmax(a.function(), dimension);
        return new TypedTensorFunction(Softmax.outputType(a.type(), dimension), softmax);
    }

    private void ensureArguments(int count, List<TypedTensorFunction> arguments, String operationName) {
        if ( arguments.size() != count)
            throw new IllegalArgumentException("Expected " + count + " arguments to " + operationName + 
                                               ", but got " + arguments.size());
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
     * A method signature input and output has the form name:index.
     * This returns the name part without the index.
     */
    private String nameOf(String name) {
        return name.split(":")[0];
    }
    
    private boolean contains(String string, ProtocolStringList strings) {
        return strings.asByteStringList().stream().anyMatch(s -> s.toStringUtf8().equals(string));
    }
    
    /** A tensor function returning a specific tensor type */
    private static final class TypedTensorFunction {
        
        private final TensorType type;
        private final TensorFunction function;
        
        public TypedTensorFunction(TensorType type, TensorFunction function) {
            this.type = type;
            this.function = function;
        }
        
        public TensorType type() { return type; }
        public TensorFunction function() { return function; }
        
    }

}
