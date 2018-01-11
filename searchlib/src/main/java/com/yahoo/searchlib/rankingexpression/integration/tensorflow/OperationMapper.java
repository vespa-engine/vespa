// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.Softmax;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.List;
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

    private TensorConverter tensorConverter = new TensorConverter();

    TypedTensorFunction join(List<TypedTensorFunction> arguments, DoubleBinaryOperator doubleFunction) {
        ensureArguments(2, arguments, "join");
        TypedTensorFunction a = arguments.get(0);
        TypedTensorFunction b = arguments.get(1);
        if (a.type().rank() < b.type().rank())
            throw new IllegalArgumentException("Attempt to join " + a.type() + " and " + b.type() + ", " +
                                               "but this is not supported when the second argument has a higher rank");

        TensorFunction bFunction = b.function();

        if (a.type().rank() > b.type().rank()) {
            // Well now we have entered the wonderful world of "broadcasting"
            // https://docs.scipy.org/doc/numpy/user/basics.broadcasting.html
            // I'm not able to extract from that any unambiguous specification of which dimensions
            // should be "stretched" when the tensor do not have the same number of dimensions.
            // From trying this with TensorFlow it appears that the second tensor is matched to the
            // "end" (highest numbered) dimensions of the first, but I'm not sure whether this is generally true.
            // Anyway, we move the dimensions of b to the last dimensions of a (instead of by default, the first).
            List<String> renameFrom = new ArrayList<>();
            List<String> renameTo = new ArrayList<>();
            int sizeDifference = a.type().rank() - b.type().rank();
            for (int i = 0; i < b.type().rank(); i++) {
                renameFrom.add(b.type().dimensions().get(i).name());
                renameTo.add("d" + (sizeDifference + i));
            }
            bFunction = new Rename(bFunction, renameFrom, renameTo);
        }

        Join function = new Join(a.function(), bFunction, doubleFunction);
        return new TypedTensorFunction(a.type(), function); // output type is a type by TF definition and a.rank>=b.rank
    }

    TypedTensorFunction map(List<TypedTensorFunction> arguments, DoubleUnaryOperator doubleFunction) {
        ensureArguments(1, arguments, "apply");
        TypedTensorFunction a = arguments.get(0);

        TensorType resultType = com.yahoo.tensor.functions.Map.outputType(a.type());
        com.yahoo.tensor.functions.Map function = new com.yahoo.tensor.functions.Map(a.function(), doubleFunction);
        return new TypedTensorFunction(resultType, function);
    }

    TypedTensorFunction placeholder(NodeDef tfNode, TensorFlowModel result) {
        String name = tfNode.getName();
        TensorType type = result.arguments().get(name);
        if (type == null)
            throw new IllegalArgumentException("A 'placeholder' node is referencing placeholder '" + name +
                                               "', but there is no such placeholder");
        // Included literally in the expression and so must be produced by a separate macro in the rank profile
        return new TypedTensorFunction(type, new VariableTensor(name));
    }

    TypedTensorFunction identity(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result) {
        if ( ! tfNode.getName().endsWith("/read"))
            throw new IllegalArgumentException("Encountered identity node " + tfNode.getName() + ", but identify " +
                                               "nodes are only supported when reading variables");
        if (tfNode.getInputList().size() != 1)
            throw new IllegalArgumentException("A Variable/read node must have one input but has " +
                                               tfNode.getInputList().size());

        String name = tfNode.getInput(0);
        AttrValue shapes = tfNode.getAttrMap().get("_output_shapes");
        if (shapes == null)
            throw new IllegalArgumentException("Referenced variable '" + name + "' is missing a tensor output shape");
        Session.Runner fetched = model.session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if ( importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from reading Variable " + name + ", but got " +
                                            importedTensors.size());
        Tensor constant = tensorConverter.toVespaTensor(importedTensors.get(0));
        result.constant(name, constant);
        return new TypedTensorFunction(constant.type(),
                                       new TensorFunctionNode.TensorFunctionExpressionNode(new ReferenceNode("constant(" + name + ")")));
    }

    TypedTensorFunction matmul(List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "matmul");
        TypedTensorFunction a = arguments.get(0);
        TypedTensorFunction b = arguments.get(1);
        if (a.type().rank() < 2 || b.type().rank() < 2)
            throw new IllegalArgumentException("Tensors in matmul must have rank of at least 2");
        if (a.type().rank() != b.type().rank())
            throw new IllegalArgumentException("Tensors in matmul must have the same rank");

        String afterLastDim = "d" + (a.type().rank() + 1);
        // Let the first dimension of the second tensor be the same as the second dimension of the first
        // and the second dimension of the second argument be not present in the first argument, while leaving the
        // rest of the dimensions the same. Such is the way of implicit dimension name tensor multiplication.

        // TODO: Check if transpose_a or transpose_b is set true and rename differently accordingly

        Rename renamedB = new Rename(b.function(), ImmutableList.of("d0", "d1"),
                                     ImmutableList.of("d1", afterLastDim));
        Matmul matmul = new Matmul(a.function(), renamedB, "d1");
        return new TypedTensorFunction(Matmul.outputType(a.type(), b.type(), "d1"),
                                       new Rename(matmul, afterLastDim, "d1"));
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
