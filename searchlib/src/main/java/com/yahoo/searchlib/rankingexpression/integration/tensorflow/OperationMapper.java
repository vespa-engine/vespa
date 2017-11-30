package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.common.collect.ImmutableList;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.Softmax;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

/**
 * Contains mappings of TensorFlow operations to the corresponding Vespa tensor functions.
 * 
 * @author bratseth
 */
class OperationMapper {

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

    TypedTensorFunction join(List<TypedTensorFunction> arguments, DoubleBinaryOperator doubleFunction) {
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

    TypedTensorFunction map(List<TypedTensorFunction> arguments, DoubleUnaryOperator doubleFunction) {
        ensureArguments(1, arguments, "apply");
        TypedTensorFunction a = arguments.get(0);

        TensorType resultType = com.yahoo.tensor.functions.Map.outputType(a.type());
        com.yahoo.tensor.functions.Map function = new com.yahoo.tensor.functions.Map(a.function(), doubleFunction);
        return new TypedTensorFunction(resultType, function);
    }

    TypedTensorFunction identity(NodeDef tfNode, Map<String, TensorType> inputs) {
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
            inputType = TensorFlowImporter.importTensorType(shapes.getList().getShape(0));
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

    TypedTensorFunction matmul(List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "matmul");
        TypedTensorFunction a = arguments.get(0);
        TypedTensorFunction b = arguments.get(0);
        if (a.type().rank() < 2 || b.type().rank() < 2)
            throw new IllegalArgumentException("Tensors in matmul must have rank of at least 2");
        if (a.type().rank() != b.type().rank())
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

    TypedTensorFunction softmax(List<TypedTensorFunction> arguments) {
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

}
