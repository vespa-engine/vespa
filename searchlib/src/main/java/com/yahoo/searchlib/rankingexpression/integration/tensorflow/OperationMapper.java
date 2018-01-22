// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.google.common.collect.ImmutableList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticNode;
import com.yahoo.searchlib.rankingexpression.rule.ArithmeticOperator;
import com.yahoo.searchlib.rankingexpression.rule.ComparisonNode;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.GeneratorLambdaFunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.searchlib.rankingexpression.rule.TruthOperator;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Generate;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Matmul;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.Softmax;
import com.yahoo.tensor.functions.TensorFunction;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

        if (a.type().rank() == 0 && b.type().rank() > 0) {
            return new TypedTensorFunction(b.type(), new Join(a.function(), b.function(), doubleFunction));
        }
        if (b.type().rank() == 0 && a.type().rank() > 0) {
            return new TypedTensorFunction(a.type(), new Join(a.function(), b.function(), doubleFunction));
        }
        if (a.type().rank() == b.type().rank()) {
            return new TypedTensorFunction(a.type(), new Join(a.function(), b.function(), doubleFunction));
        }

        // Well now we have entered the wonderful world of "broadcasting"
        // https://docs.scipy.org/doc/numpy/user/basics.broadcasting.html
        // I'm not able to extract from that any unambiguous specification of which dimensions
        // should be "stretched" when the tensor do not have the same number of dimensions.
        // From trying this with TensorFlow it appears that the second tensor is matched to the
        // "end" (highest numbered) dimensions of the first, but I'm not sure whether this is generally true.
        // Anyway, we move the dimensions of b to the last dimensions of a (instead of by default, the first).

        if (a.type().rank() > b.type().rank()) {
            TensorFunction renameFunction = renameForBroadcast(a, b);
            return new TypedTensorFunction(a.type(), new Join(a.function(), renameFunction, doubleFunction));
        }
        TensorFunction renameFunction = renameForBroadcast(b, a);
        return new TypedTensorFunction(b.type(), new Join(renameFunction, b.function(), doubleFunction));
    }

    private TensorFunction renameForBroadcast(TypedTensorFunction a, TypedTensorFunction b) {
        List<String> renameFrom = new ArrayList<>();
        List<String> renameTo = new ArrayList<>();
        int sizeDifference = a.type().rank() - b.type().rank();
        for (int i = 0; i < b.type().rank(); i++) {
            renameFrom.add(b.type().dimensions().get(i).name());
            renameTo.add("d" + (sizeDifference + i));
        }
        return new Rename(b.function(), renameFrom, renameTo);
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

    TypedTensorFunction placeholderWithDefault(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result) {
        String name = tfNode.getInput(0);
        Tensor defaultValue = getConstantTensor(model, name);
        result.constant(name, defaultValue);
        result.macro(name, new RankingExpression(name, new ReferenceNode("constant('" + name + "')")));
        // The default value will be provided by the macro. Users can override macro to change value.
        return new TypedTensorFunction(defaultValue.type(), new VariableTensor(name));
    }

    TypedTensorFunction constant(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result) {
        String name = tfNode.getName();
        if (tfNode.getInputList().size() != 0) {
            throw new IllegalArgumentException("A constant node must have zero inputs but '" + name + "' has " +
                    tfNode.getInputList().size());
        }
        return importConstantTensor(tfNode, model, result, name);
    }

    TypedTensorFunction identity(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result) {
        if ( ! tfNode.getName().endsWith("/read"))
            throw new IllegalArgumentException("Encountered identity node " + tfNode.getName() + ", but identify " +
                                               "nodes are only supported when reading variables");
        if (tfNode.getInputList().size() != 1)
            throw new IllegalArgumentException("A Variable/read node must have one input but '" +
                                                tfNode.getName() + "' has " + tfNode.getInputList().size());

        String name = tfNode.getInput(0);
        return importConstantTensor(tfNode, model, result, name);
    }

    private TypedTensorFunction importConstantTensor(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result, String name) {
        AttrValue shapes = tfNode.getAttrMap().get("_output_shapes");
        if (shapes == null)
            throw new IllegalArgumentException("'" + name + "' is missing a tensor shape");
        Tensor constant = getConstantTensor(model, name);
        result.constant(name, constant);
        return new TypedTensorFunction(constant.type(),
                new TensorFunctionNode.TensorFunctionExpressionNode(new ReferenceNode("constant('" + name + "')")));
    }

    private Tensor getConstantTensor(SavedModelBundle model, String name) {
        Session.Runner fetched = model.session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if (importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from fetching " + name + ", but got " +
                    importedTensors.size());
        return tensorConverter.toVespaTensor(importedTensors.get(0));
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

    TypedTensorFunction mean(NodeDef tfNode, SavedModelBundle model, List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "mean");
        Tensor reductionIndices = getConstantTensor(model, tfNode.getInput(1));

        TensorFunction inputFunction = arguments.get(0).function();
        TensorType inputType = arguments.get(0).type();

        List<String> reduceDimensions = new ArrayList<>();
        for (Iterator<Tensor.Cell> cellIterator = reductionIndices.cellIterator(); cellIterator.hasNext();) {
            Tensor.Cell cell = cellIterator.next();
            int dimensionIndex = cell.getValue().intValue();
            if (dimensionIndex < 0) {
                dimensionIndex = inputType.dimensions().size() - dimensionIndex;
            }
            reduceDimensions.add(inputType.dimensions().get(dimensionIndex).name());
        }

        TensorType outputType = Reduce.outputType(inputType, reduceDimensions);
        TensorFunction outputFunction = new Reduce(inputFunction, Reduce.Aggregator.avg, reduceDimensions);

        if (shouldKeepDimensions(tfNode)) {
            return reshape(outputFunction, outputType, keepDimensionType(inputType, reduceDimensions));
        }

        TypedTensorFunction output = checkNamingConvention(outputType, outputFunction);
        return output;
    }

    private boolean shouldKeepDimensions(NodeDef tfNode) {
        AttrValue keepDimsAttr = tfNode.getAttrMap().get("keep_dims");
        return keepDimsAttr != null && keepDimsAttr.getB();
    }

    private TensorType keepDimensionType(TensorType inputType, List<String> reduceDimensions) {
        TensorType.Builder builder = new TensorType.Builder();
        for (TensorType.Dimension dimension: inputType.dimensions()) {
            String name = dimension.name();
            Long size = dimensionSize(dimension);
            if (reduceDimensions.contains(name)) {
                size = 1L;
            }
            builder.indexed(name, size);
        }
        return builder.build();
    }

    private TypedTensorFunction checkNamingConvention(TensorType type, TensorFunction function) {
        for (int i = 0; i < type.dimensions().size(); ++i) {
            String correct = String.format("d%d", i);
            String current = type.dimensions().get(i).name();
            if (!current.equals(correct)) {
                return fixNamingConvention(type, function);
            }
        }
        return new TypedTensorFunction(type, function);
    }

    private TypedTensorFunction fixNamingConvention(TensorType type, TensorFunction function) {
        TensorType.Builder correctType = new TensorType.Builder();
        List<String> from = new ArrayList<>();
        List<String> to = new ArrayList<>();
        for (int i = 0; i < type.dimensions().size(); ++i) {
            String correct = String.format("d%d", i);
            String current = type.dimensions().get(i).name();
            if (!current.equals(correct)) {
                from.add(current);
                to.add(correct);
            }
            correctType.indexed(correct, dimensionSize(type.dimensions().get(i)));
        }
        if (from.size() > 0) {
            function = new Rename(function, from, to);
            type = correctType.build();
        }
        return new TypedTensorFunction(type, function);
    }

    TypedTensorFunction noOp(List<TypedTensorFunction> arguments) {
        ensureArguments(1, arguments, "noOp");
        return arguments.get(0);
    }

    TypedTensorFunction expandDims(NodeDef tfNode, SavedModelBundle model, List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "expandDims");
        Tensor axis = getConstantTensor(model, tfNode.getInput(1));
        if (axis.type().rank() != 0) {
            throw new IllegalArgumentException("Axis argument to ExpandDims must be a scalar");
        }

        TensorFunction inputFunction = arguments.get(0).function();
        TensorType inputType = arguments.get(0).type();

        int dimensionToInsert = (int)axis.asDouble();
        if (dimensionToInsert < 0) {
            dimensionToInsert = inputType.dimensions().size() - dimensionToInsert;
        }

        TensorType.Builder outputTypeBuilder = new TensorType.Builder();
        int dimensionIndex = 0;
        for (int i = 0; i < inputType.dimensions().size() + 1; ++i) {
            String name = String.format("temp_%d", i);
            Long size;
            if (i == dimensionToInsert) {
                size = 1L;
            } else {
                size = dimensionSize(inputType.dimensions().get(dimensionIndex));
                dimensionIndex++;
            }
            outputTypeBuilder.indexed(name, size);
        }

        return reshape(inputFunction, inputType, outputTypeBuilder.build());
    }

    TypedTensorFunction reshape(NodeDef tfNode, SavedModelBundle model, List<TypedTensorFunction> arguments) {
        ensureArguments(2, arguments, "reshape");
        Tensor shape = getConstantTensor(model, tfNode.getInput(1));

        TensorFunction inputFunction = arguments.get(0).function();
        TensorType inputType = arguments.get(0).type();

        TensorType.Builder outputTypeBuilder = new TensorType.Builder();
        int dimensionIndex = 0;
        for (Iterator<Tensor.Cell> cellIterator = shape.cellIterator(); cellIterator.hasNext();) {
            Tensor.Cell cell = cellIterator.next();
            int size = cell.getValue().intValue();
            if (size < 0) {
                size = -1 * (int)shape.reduce(Reduce.Aggregator.prod).asDouble() / tensorSize(inputType).intValue();
            }
            outputTypeBuilder.indexed(String.format("temp_%d", dimensionIndex), size);
            dimensionIndex++;
        }
        return reshape(inputFunction, inputType, outputTypeBuilder.build());
    }

    private TypedTensorFunction reshape(TensorFunction inputFunction, TensorType inputType, TensorType outputType) {
        if (!tensorSize(inputType).equals(tensorSize(outputType))) {
            throw new IllegalArgumentException("New and old shape of tensor must have the same size when reshaping");
        }

        // Conceptually, reshaping consists on unrolling a tensor to an array using the dimension order,
        // then use the dimension order of the new shape to roll back into a tensor.
        // Here we create a transformation tensor that is multiplied with the from tensor to map into
        // the new shape. We have to introduce temporary dimension names and rename back if dimension names
        // in the new and old tensor type overlap.

        ExpressionNode unrollFrom = unrollTensorExpression(inputType);
        ExpressionNode unrollTo = unrollTensorExpression(outputType);
        ExpressionNode transformExpression = new ComparisonNode(unrollFrom, TruthOperator.EQUAL, unrollTo);

        TensorType transformationType = new TensorType.Builder(inputType, outputType).build();
        Generate transformTensor = new Generate(transformationType,
                new GeneratorLambdaFunctionNode(transformationType, transformExpression).asLongListToDoubleOperator());

        TensorFunction outputFunction = new Reduce(
                new Join(inputFunction, transformTensor, ScalarFunctions.multiply()),
                Reduce.Aggregator.sum,
                inputType.dimensions().stream().map(TensorType.Dimension::name).collect(Collectors.toList()));
        TypedTensorFunction output = checkNamingConvention(outputType, outputFunction);
        return output;
    }

    private ExpressionNode unrollTensorExpression(TensorType type) {
        if (type.rank() == 0) {
            return new ConstantNode(DoubleValue.zero);
        }
        List<ExpressionNode> children = new ArrayList<>();
        List<ArithmeticOperator> operators = new ArrayList<>();
        int size = 1;
        for (int i = type.dimensions().size() - 1; i >= 0; --i) {
            TensorType.Dimension dimension = type.dimensions().get(i);
            children.add(0, new ReferenceNode(dimension.name()));
            if (size > 1) {
                operators.add(0, ArithmeticOperator.MULTIPLY);
                children.add(0, new ConstantNode(new DoubleValue(size)));
            }
            size *= dimensionSize(dimension);
            if (i > 0) {
                operators.add(0, ArithmeticOperator.PLUS);
            }
        }
        return new ArithmeticNode(children, operators);
    }

    TypedTensorFunction select(NodeDef tfNode, SavedModelBundle model, TensorFlowModel result, List<TypedTensorFunction> arguments) {
        ensureArguments(3, arguments, "select");
        Tensor condition = getConstantTensor(model, tfNode.getInput(0));

        TypedTensorFunction x = arguments.get(1);
        TypedTensorFunction y = arguments.get(2);
        if ((x.type().rank() != y.type().rank()) || !(tensorSize(x.type()).equals(tensorSize(y.type())))) {
            throw new IllegalArgumentException("'Select': input tensors must have the same shape");
        }

        if (condition.type().rank() == 0) {
            return (int)condition.asDouble() == 0 ? y : x;
        }
        if (condition.type().rank() == 1 && dimensionSize(condition.type().dimensions().get(0)) == 1) {
            return condition.cellIterator().next().getValue().intValue() == 0 ? y : x;
        }

        // The task is to select cells from 'x' or 'y' based on 'condition'.
        // If 'condition' is 0 (false), select from 'y', if 1 (true) select
        // from 'x'. We do this by individually joining 'x' and 'y' with
        // 'condition', and then joining the resulting two tensors.

        TypedTensorFunction conditionFunction = importConstantTensor(tfNode, model, result, tfNode.getInput(0));
        TensorFunction xCond = new Join(x.function(), conditionFunction.function(), ScalarFunctions.multiply());
        TensorFunction yCond = new Join(y.function(), conditionFunction.function(), new DoubleBinaryOperator() {
            @Override public double applyAsDouble(double a, double b) { return a * (1.0 - b); }
            @Override public String toString() { return "f(a,b)(a * (1-b))"; }
        });
        TensorFunction outputFunction = new Join(xCond, yCond, ScalarFunctions.add());
        return new TypedTensorFunction(x.type(), outputFunction);
    }

    TypedTensorFunction softmax(List<TypedTensorFunction> arguments) {
        ensureArguments(1, arguments, "softmax");
        TypedTensorFunction a = arguments.get(0);
        // TODO: Read the "dim" parameter and use it to decide dimension if set and != -1
        String dimension = "d" + (a.type().rank() - 1);
        Softmax softmax = new Softmax(a.function(), dimension);
        return new TypedTensorFunction(Softmax.outputType(a.type(), dimension), softmax);
    }

    TypedTensorFunction squeeze(NodeDef tfNode, List<TypedTensorFunction> arguments) {
        ensureArguments(1, arguments, "squeeze");

        TensorFunction inputFunction = arguments.get(0).function();
        TensorType inputType = arguments.get(0).type();
        List<String> squeezeDimensions;

        AttrValue squeezeDimsAttr = tfNode.getAttrMap().get("squeeze_dims");
        if (squeezeDimsAttr == null) {
            squeezeDimensions = inputType.dimensions().stream().
                    filter(dim -> dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    collect(Collectors.toList());
        } else {
            squeezeDimensions = squeezeDimsAttr.getList().getIList().stream().
                    map(i -> i < 0 ? inputType.dimensions().size() - i : i).
                    map(i -> inputType.dimensions().get(i.intValue())).
                    filter(dim -> dimensionSize(dim) == 1).
                    map(TensorType.Dimension::name).
                    collect(Collectors.toList());
        }

        if (squeezeDimensions.isEmpty()) {
            return arguments.get(0);
        }

        TensorFunction outputFunction = new Reduce(inputFunction, Reduce.Aggregator.sum, squeezeDimensions);
        TensorType outputType = Reduce.outputType(inputType, squeezeDimensions);
        TypedTensorFunction output = checkNamingConvention(outputType, outputFunction);
        return output;
    }

    private Long tensorSize(TensorType type) {
        Long size = 1L;
        for (TensorType.Dimension dimension : type.dimensions()) {
            size *= dimensionSize(dimension);
        }
        return size;
    }

    private Long dimensionSize(TensorType.Dimension dim) {
        return dim.size().orElseThrow(() -> new IllegalArgumentException("Dimension has no size"));
    }

    private void ensureArguments(int count, List<TypedTensorFunction> arguments, String operationName) {
        if ( arguments.size() != count)
            throw new IllegalArgumentException("Expected " + count + " arguments to " + operationName +
                                               ", but got " + arguments.size());
    }

}
