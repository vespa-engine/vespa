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
import org.tensorflow.Session;
import org.tensorflow.framework.AttrValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Contains mappings of TensorFlow operations to the corresponding Vespa tensor functions.
 *
 * @author bratseth
 * @author lesters
 */
class OperationMapper {

    // A note on conversion from implicitly numbered to explicitly named dimensions:
    //
    // Vespa tensor dimensions are explicitly named and thus have an explicit notion of being
    // 'the same' or not of some dimension in another tensor. Since TF lacks this, each operation
    // comes with a built-in definition of sameness. We mirror this by wrapping the Vespa tensor operation
    // around dimension renaming operations which mirrors those built into the TF operation definitions.
    //
    // To do this we need a naming convention: We maintain a naming of each tensor where the 'outermost'
    // dimension is named 'd0', the second outer most 'd1' and so on. Arguments are renamed to match the operation
    // and the result is then renamed again (if necessary) to recover this convention across a full nested
    // computation.
    //
    // This requires us to track tensor types throughout the conversion.


    // Supported TensorFlow operations
    enum Operation {

        // TODO: move the implementations to specific files as we support more operations

        /*
         * array ops
         */
        CONST  (OperationMapper::constant),
        EXPANDDIMS  (OperationMapper::expandDims),
        IDENTITY  (OperationMapper::identity),
        PLACEHOLDER  (OperationMapper::placeholder),
        PLACEHOLDERWITHDEFAULT  (OperationMapper::placeholderWithDefault),
        RESHAPE  (OperationMapper::reshape),
        SQUEEZE  (OperationMapper::squeeze),

        /*
         * control flow
         */
        MERGE  (OperationMapper::merge),
        SWITCH  (OperationMapper::switchOp),

        /*
         * math ops
         */
        ADD  (OperationMapper::add),
        ADD_N  (OperationMapper::add),
        ACOS  (OperationMapper::acos),
        DIV  (OperationMapper::div),
        REALDIV  (OperationMapper::div),
        FLOOR  (OperationMapper::floor),
        MATMUL  (OperationMapper::matmul),
        MAXIMUM  (OperationMapper::maximum),
        MEAN  (OperationMapper::mean),
        REDUCEMEAN  (OperationMapper::mean),
        MUL  (OperationMapper::mul),
        MULTIPLY  (OperationMapper::mul),
        RSQRT  (OperationMapper::rsqrt),
        SELECT  (OperationMapper::select),
        WHERE3  (OperationMapper::select),
        SIGMOID  (OperationMapper::sigmoid),
        SQUAREDDIFFERENCE  (OperationMapper::squaredDifference),
        SUB  (OperationMapper::sub),
        SUBTRACT  (OperationMapper::sub),

        /*
         * nn ops
         */
        BIASADD  (OperationMapper::add),
        ELU  (OperationMapper::elu),
        RELU  (OperationMapper::relu),
        SELU  (OperationMapper::selu),
        SOFTMAX  (OperationMapper::softMax),

        /*
         * state ops
         */
        VARIABLE  (OperationMapper::variable),
        VARIABLEV2  (OperationMapper::variable),

        /*
         * evaluation no-ops
         */
        STOPGRADIENT  (OperationMapper::identity),
        NOOP  (OperationMapper::noOp);


        private final Function<TensorFlowImporter.Parameters, Optional<TypedTensorFunction>> func;

        Operation(Function<TensorFlowImporter.Parameters, Optional<TypedTensorFunction>> func) {
            this.func = func;
        }

        Optional<TypedTensorFunction> map(TensorFlowImporter.Parameters params) {
            return func.apply(params);
        }

    }

    static Optional<TypedTensorFunction> map(TensorFlowImporter.Parameters params) {
        Optional<Operation> operation = Stream.of(Operation.values())
                                            .filter(op -> op.name().equalsIgnoreCase(params.node().getOp()))
                                            .findFirst();
        if (operation.isPresent()) {
            return operation.get().map(params);
        }
        params.signature().importWarning("TensorFlow operation '" + params.node().getOp() +
                                         "' in node '" + params.node().getName() + "' is not supported.");
        return Optional.empty();
    }


    // Operations ---------------------------------

    private static Optional<TypedTensorFunction> constant(TensorFlowImporter.Parameters params) {
        Tensor value = AttrValueConverter.toVespaTensor(params.node(), "value");
        return createConstant(params, value);
    }

    private static Optional<TypedTensorFunction> expandDims(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 2)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();

        Tensor axis = getConstantTensor(params, params.node().getInput(1));
        if (axis.type().rank() != 0) {
            throw new IllegalArgumentException("Axis argument to ExpandDims must be a scalar");
        }

        TensorFunction inputFunction = inputs.get(0).get().function();
        TensorType inputType = inputs.get(0).get().type();

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

    private static Optional<TypedTensorFunction> identity(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 1)) {
            return Optional.empty();
        }
        return params.inputs().get(0);
    }

    private static Optional<TypedTensorFunction> placeholder(TensorFlowImporter.Parameters params) {
        String name = params.node().getName();
        String vespaName = toVespaName(params.node().getName());
        TensorType type = params.result().arguments().get(name);
        if (type == null) {
            throw new IllegalArgumentException("A 'placeholder' node is referencing placeholder '" + name +
                                               "', but there is no such placeholder");
        }
        params.result().requiredMacro(vespaName, type);
        // Included literally in the expression and so must be produced by a separate macro in the rank profile
        TypedTensorFunction output = new TypedTensorFunction(type, new VariableTensor(vespaName, type));
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> placeholderWithDefault(TensorFlowImporter.Parameters params) {
        String name = toVespaName(params.node().getInput(0));
        Tensor defaultValue = getConstantTensor(params, params.node().getInput(0));
        params.result().constant(name, defaultValue);
        params.result().macro(name, new RankingExpression(name, new ReferenceNode("constant(\"" + name + "\")")));
        // The default value will be provided by the macro. Users can override macro to change value.
        TypedTensorFunction output = new TypedTensorFunction(defaultValue.type(), new VariableTensor(name));
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> reshape(TensorFlowImporter.Parameters params) {
        if ( ! checkInputs(params, 2)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        Tensor shape = getConstantTensor(params, params.node().getInput(1));

        TensorFunction inputFunction = inputs.get(0).get().function();
        TensorType inputType = inputs.get(0).get().type();

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

    private static Optional<TypedTensorFunction> squeeze(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 1)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();

        TensorFunction inputFunction = inputs.get(0).get().function();
        TensorType inputType = inputs.get(0).get().type();
        List<String> squeezeDimensions;

        AttrValue squeezeDimsAttr = params.node().getAttrMap().get("squeeze_dims");
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
            return inputs.get(0);
        }

        TensorFunction outputFunction = new Reduce(inputFunction, Reduce.Aggregator.sum, squeezeDimensions);
        TensorType outputType = Reduce.outputType(inputType, squeezeDimensions);
        TypedTensorFunction output = checkNamingConvention(outputType, outputFunction);
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> merge(TensorFlowImporter.Parameters params) {
        return params.inputs().stream()
                .filter(Optional::isPresent)
                .findFirst()
                .orElse(Optional.empty());
    }

    private static Optional<TypedTensorFunction> switchOp(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 2)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        Tensor predicate = getConstantTensor(params, params.node().getInput(1));
        if (predicate.type().rank() != 0) {
            throw new IllegalArgumentException("'switch': predicate must be a scalar");
        }
        double pred = predicate.asDouble();
        int output = params.port().length() > 0 ? Integer.parseInt(params.port()) : 0;
        if (output < 0 || output > 1) {
            throw new IllegalArgumentException("'switch': predicate is not boolean");
        }
        if (pred == output) {
            return inputs.get(0);
        }
        return Optional.empty();
    }

    private static Optional<TypedTensorFunction> add(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.add());
    }

    private static Optional<TypedTensorFunction> acos(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.acos());
    }

    private static Optional<TypedTensorFunction> div(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.divide());
    }

    private static Optional<TypedTensorFunction> floor(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.floor());
    }

    private static Optional<TypedTensorFunction> matmul(TensorFlowImporter.Parameters params) {
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        if (!checkInputs(params, 2)) {
            return Optional.empty();
        }

        TypedTensorFunction a = inputs.get(0).get();
        TypedTensorFunction b = inputs.get(1).get();
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
        TypedTensorFunction output = new TypedTensorFunction(Matmul.outputType(a.type(), b.type(), "d1"),
                                       new Rename(matmul, afterLastDim, "d1"));
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> maximum(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.max());
    }

    private static Optional<TypedTensorFunction> mean(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 2)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        TensorFunction inputFunction = inputs.get(0).get().function();
        TensorType inputType = inputs.get(0).get().type();

        Tensor reductionIndices = getConstantTensor(params, params.node().getInput(1));
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

        if (shouldKeepDimensions(params)) {
            return reshape(outputFunction, outputType, keepDimensionType(inputType, reduceDimensions));
        }
        TypedTensorFunction output = checkNamingConvention(outputType, outputFunction);
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> mul(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.multiply());
    }

    private static Optional<TypedTensorFunction> rsqrt(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.rsqrt());
    }

    private static Optional<TypedTensorFunction> select(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 3)) {
            return Optional.empty();
        }
        Tensor condition = getConstantTensor(params, params.node().getInput(0));

        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        TypedTensorFunction x = inputs.get(1).get();
        TypedTensorFunction y = inputs.get(2).get();
        if ((x.type().rank() != y.type().rank()) || !(tensorSize(x.type()).equals(tensorSize(y.type())))) {
            throw new IllegalArgumentException("'Select': input tensors must have the same shape");
        }

        if (condition.type().rank() == 0) {
            return Optional.of((int)condition.asDouble() == 0 ? y : x);
        }
        if (condition.type().rank() == 1 && dimensionSize(condition.type().dimensions().get(0)) == 1) {
            return Optional.of(condition.cellIterator().next().getValue().intValue() == 0 ? y : x);
        }

        // The task is to select cells from 'x' or 'y' based on 'condition'.
        // If 'condition' is 0 (false), select from 'y', if 1 (true) select
        // from 'x'. We do this by individually joining 'x' and 'y' with
        // 'condition', and then joining the resulting two tensors.

        Optional<TypedTensorFunction> conditionFunction = importConstantTensor(params, params.node().getInput(0));
        if (!conditionFunction.isPresent()) {
            return Optional.empty();
        }
        TensorFunction xCond = new Join(x.function(), conditionFunction.get().function(), ScalarFunctions.multiply());
        TensorFunction yCond = new Join(y.function(), conditionFunction.get().function(), new DoubleBinaryOperator() {
            @Override public double applyAsDouble(double a, double b) { return a * (1.0 - b); }
            @Override public String toString() { return "f(a,b)(a * (1-b))"; }
        });
        TensorFunction outputFunction = new Join(xCond, yCond, ScalarFunctions.add());
        TypedTensorFunction output = new TypedTensorFunction(x.type(), outputFunction);
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> sigmoid(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.sigmoid());
    }

    private static Optional<TypedTensorFunction> squaredDifference(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.squareddifference());
    }

    private static Optional<TypedTensorFunction> sub(TensorFlowImporter.Parameters params) {
        return join(params, ScalarFunctions.subtract());
    }

    private static Optional<TypedTensorFunction> elu(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.elu());
    }

    private static Optional<TypedTensorFunction> relu(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.relu());
    }

    private static Optional<TypedTensorFunction> selu(TensorFlowImporter.Parameters params) {
        return map(params, ScalarFunctions.selu());
    }

    private static Optional<TypedTensorFunction> softMax(TensorFlowImporter.Parameters params) {
        if (!checkInputs(params, 1)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        TypedTensorFunction a = inputs.get(0).get();
        // TODO: Read the "dim" parameter and use it to decide dimension if set and != -1
        String dimension = "d" + (a.type().rank() - 1);
        Softmax softmax = new Softmax(a.function(), dimension);
        TypedTensorFunction output = new TypedTensorFunction(Softmax.outputType(a.type(), dimension), softmax);
        return Optional.of(output);
    }

    private static Optional<TypedTensorFunction> variable(TensorFlowImporter.Parameters params) {
        return importConstantTensor(params, params.node().getName());
    }

    private static Optional<TypedTensorFunction> noOp(TensorFlowImporter.Parameters params) {
        return Optional.empty();
    }

    /*
     * Utility
     */

    private static Optional<TypedTensorFunction> join(TensorFlowImporter.Parameters params, DoubleBinaryOperator doubleFunction) {
        if (!checkInputs(params, 2)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();

        TypedTensorFunction a = inputs.get(0).get();
        TypedTensorFunction b = inputs.get(1).get();

        if (a.type().rank() == 0 && b.type().rank() > 0) {
            return Optional.of(new TypedTensorFunction(b.type(), new Join(a.function(), b.function(), doubleFunction)));
        }
        if (b.type().rank() == 0 && a.type().rank() > 0) {
            return Optional.of(new TypedTensorFunction(a.type(), new Join(a.function(), b.function(), doubleFunction)));
        }
        if (a.type().rank() == b.type().rank()) {
            return Optional.of(new TypedTensorFunction(a.type(), new Join(a.function(), b.function(), doubleFunction)));
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
            return Optional.of(new TypedTensorFunction(a.type(), new Join(a.function(), renameFunction, doubleFunction)));
        }
        TensorFunction renameFunction = renameForBroadcast(b, a);
        return Optional.of(new TypedTensorFunction(b.type(), new Join(renameFunction, b.function(), doubleFunction)));
    }

    private static TensorFunction renameForBroadcast(TypedTensorFunction a, TypedTensorFunction b) {
        List<String> renameFrom = new ArrayList<>();
        List<String> renameTo = new ArrayList<>();
        int sizeDifference = a.type().rank() - b.type().rank();
        for (int i = 0; i < b.type().rank(); i++) {
            renameFrom.add(b.type().dimensions().get(i).name());
            renameTo.add("d" + (sizeDifference + i));
        }
        return new Rename(b.function(), renameFrom, renameTo);
    }

    private static Optional<TypedTensorFunction> map(TensorFlowImporter.Parameters params, DoubleUnaryOperator doubleFunction) {
        if (!checkInputs(params, 1)) {
            return Optional.empty();
        }
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        TypedTensorFunction a = inputs.get(0).get();
        TensorType resultType = com.yahoo.tensor.functions.Map.outputType(a.type());
        com.yahoo.tensor.functions.Map function = new com.yahoo.tensor.functions.Map(a.function(), doubleFunction);
        return Optional.of(new TypedTensorFunction(resultType, function));
    }

    private static Optional<TypedTensorFunction> createConstant(TensorFlowImporter.Parameters params, Tensor constant) {
        String name = toVespaName(params.node().getName());
        params.result().constant(name, constant);
        TypedTensorFunction output = new TypedTensorFunction(constant.type(),
                new TensorFunctionNode.TensorFunctionExpressionNode(
                        new ReferenceNode("constant(\"" + name + "\")")));
        return Optional.of(output);
    }

    private static Tensor getConstantTensor(TensorFlowImporter.Parameters params, String name) {
        String vespaName = toVespaName(name);
        if (params.result().constants().containsKey(vespaName)) {
            return params.result().constants().get(vespaName);
        }
        Session.Runner fetched = params.model().session().runner().fetch(name);
        List<org.tensorflow.Tensor<?>> importedTensors = fetched.run();
        if (importedTensors.size() != 1)
            throw new IllegalStateException("Expected 1 tensor from fetching " + name + ", but got " +
                    importedTensors.size());
        return TensorConverter.toVespaTensor(importedTensors.get(0));
    }

    private static Optional<TypedTensorFunction> importConstantTensor(TensorFlowImporter.Parameters params, String name) {
        AttrValue shapes = params.node().getAttrMap().get("_output_shapes");
        if (shapes == null)
            throw new IllegalArgumentException("'" + name + "' is missing a tensor shape");
        Tensor constant = getConstantTensor(params, name);
        return createConstant(params, constant);
    }

    private static Optional<TypedTensorFunction> reshape(TensorFunction inputFunction, TensorType inputType, TensorType outputType) {
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
        return Optional.of(output);
    }

    private static ExpressionNode unrollTensorExpression(TensorType type) {
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

    private static boolean shouldKeepDimensions(TensorFlowImporter.Parameters params) {
        AttrValue keepDimsAttr = params.node().getAttrMap().get("keep_dims");
        return keepDimsAttr != null && keepDimsAttr.getB();
    }

    private static TensorType keepDimensionType(TensorType inputType, List<String> reduceDimensions) {
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

    private static TypedTensorFunction checkNamingConvention(TensorType type, TensorFunction function) {
        for (int i = 0; i < type.dimensions().size(); ++i) {
            String correct = String.format("d%d", i);
            String current = type.dimensions().get(i).name();
            if (!current.equals(correct)) {
                return fixNamingConvention(type, function);
            }
        }
        return new TypedTensorFunction(type, function);
    }

    private static TypedTensorFunction fixNamingConvention(TensorType type, TensorFunction function) {
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

    private static Long tensorSize(TensorType type) {
        Long size = 1L;
        for (TensorType.Dimension dimension : type.dimensions()) {
            size *= dimensionSize(dimension);
        }
        return size;
    }

    private static Long dimensionSize(TensorType.Dimension dim) {
        return dim.size().orElseThrow(() -> new IllegalArgumentException("Dimension has no size"));
    }

    private static boolean checkInputs(TensorFlowImporter.Parameters params, int expected) {
        List<Optional<TypedTensorFunction>> inputs = params.inputs();
        if (!inputs.stream().allMatch(Optional::isPresent)) {
            return false;
        }
        if (inputs.size() != expected) {
            params.signature().importWarning("Expected " + expected +
                    " arguments to " + params.node().getOp() + ", but got " + inputs.size());
            return false;
        }
        return true;
    }

    public static String toVespaName(String name) {
        return name != null ? name.replace('/', '_') : null;
    }

}
