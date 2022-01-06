// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

public class Join extends IntermediateOperation {

    private final DoubleBinaryOperator operator;

    public Join(String modelName, String nodeName, List<IntermediateOperation> inputs, DoubleBinaryOperator operator) {
        super(modelName, nodeName, inputs);
        this.operator = operator;
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(2)) return null;

        OrderedTensorType a = largestInput().type().get();
        OrderedTensorType b = smallestInput().type().get();

        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(resultValueType());
        int sizeDifference = a.rank() - b.rank();
        for (int i = 0; i < a.rank(); ++i) {
            TensorType.Dimension aDim = a.dimensions().get(i);
            long size = aDim.size().orElse(-1L);

            if (i - sizeDifference >= 0) {
                TensorType.Dimension bDim = b.dimensions().get(i - sizeDifference);
                size = Math.max(size, bDim.size().orElse(-1L));
            }

            if (aDim.type() == TensorType.Dimension.Type.indexedBound) {
                builder.add(TensorType.Dimension.indexed(aDim.name(), size));
            } else if (aDim.type() == TensorType.Dimension.Type.indexedUnbound) {
                builder.add(TensorType.Dimension.indexed(aDim.name()));
            } else if (aDim.type() == TensorType.Dimension.Type.mapped) {
                builder.add(TensorType.Dimension.mapped(aDim.name()));
            }
        }
        return builder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputTypesPresent(2)) return null;
        if ( ! allInputFunctionsPresent(2)) return null;

        // Optimization: if inputs are the same, replace with a map function.
        if (inputs.get(0).equals(inputs.get(1))) {
            Optional<DoubleUnaryOperator> mapOperator = operatorAsUnary(operator);
            if (mapOperator.isPresent()) {
                IntermediateOperation input = inputs.get(0);
                input.removeDuplicateOutputsTo(this);  // avoids unnecessary function export
                return new com.yahoo.tensor.functions.Map<Reference>(input.function().get(), mapOperator.get());
            }
        }

        IntermediateOperation a = largestInput();
        IntermediateOperation b = smallestInput();

        List<String> aDimensionsToReduce = new ArrayList<>();
        List<String> bDimensionsToReduce = new ArrayList<>();
        int sizeDifference = a.type().get().rank() - b.type().get().rank();
        for (int i = 0; i < b.type().get().rank(); ++i) {
            TensorType.Dimension bDim = b.type().get().dimensions().get(i);
            TensorType.Dimension aDim = a.type().get().dimensions().get(i + sizeDifference);
            long bSize = bDim.size().orElse(-1L);
            long aSize = aDim.size().orElse(-1L);
            if (bSize == 1L && aSize != 1L) {
                bDimensionsToReduce.add(bDim.name());
            }
            if (aSize == 1L && bSize != 1L) {
                aDimensionsToReduce.add(bDim.name());
            }
        }

        TensorFunction<Reference> aReducedFunction = a.function().get();
        if (aDimensionsToReduce.size() > 0) {
            aReducedFunction = new Reduce<Reference>(a.function().get(), Reduce.Aggregator.sum, aDimensionsToReduce);
        }
        TensorFunction<Reference> bReducedFunction = b.function().get();
        if (bDimensionsToReduce.size() > 0) {
            bReducedFunction = new Reduce<Reference>(b.function().get(), Reduce.Aggregator.sum, bDimensionsToReduce);
        }

        // retain order of inputs
        if (a == inputs.get(1)) {
            TensorFunction<Reference> temp = bReducedFunction;
            bReducedFunction = aReducedFunction;
            aReducedFunction = temp;
        }

        return new com.yahoo.tensor.functions.Join<Reference>(aReducedFunction, bReducedFunction, operator);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if ( ! allInputTypesPresent(2)) return;

        OrderedTensorType a = largestInput().type().get();
        OrderedTensorType b = smallestInput().type().get();
        int sizeDifference = a.rank() - b.rank();
        for (int i = 0; i < b.rank(); ++i) {
            String bDim = b.dimensions().get(i).name();
            String aDim = a.dimensions().get(i + sizeDifference).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer.Constraint.equal(false), this);
        }
    }

    private IntermediateOperation largestInput() {
        OrderedTensorType a = inputs.get(0).type().get();
        OrderedTensorType b = inputs.get(1).type().get();
        return a.rank() >= b.rank() ? inputs.get(0) : inputs.get(1);
    }

    private IntermediateOperation smallestInput() {
        OrderedTensorType a = inputs.get(0).type().get();
        OrderedTensorType b = inputs.get(1).type().get();
        return a.rank() < b.rank() ? inputs.get(0) : inputs.get(1);
    }

    @Override
    public Join withInputs(List<IntermediateOperation> inputs) {
        return new Join(modelName(), name(), inputs, operator);
    }

    @Override
    public String operationName() { return "Join"; }

    private Optional<DoubleUnaryOperator> operatorAsUnary(DoubleBinaryOperator op) {
        String unaryRep;
        if      (op instanceof ScalarFunctions.Add) unaryRep = "f(a)(a + a)";
        else if (op instanceof ScalarFunctions.Multiply) unaryRep = "f(a)(a * a)";
        else if (op instanceof ScalarFunctions.Subtract) unaryRep = "f(a)(0)";
        else if (op instanceof ScalarFunctions.Divide) unaryRep = "f(a)(1)";
        else if (op instanceof ScalarFunctions.Equal) unaryRep = "f(a)(1)";
        else if (op instanceof ScalarFunctions.Greater) unaryRep = "f(a)(0)";
        else if (op instanceof ScalarFunctions.Less) unaryRep = "f(a)(0)";
        else if (op instanceof ScalarFunctions.Max) unaryRep = "f(a)(a)";
        else if (op instanceof ScalarFunctions.Min) unaryRep = "f(a)(a)";
        else if (op instanceof ScalarFunctions.Mean) unaryRep = "f(a)(a)";
        else if (op instanceof ScalarFunctions.Pow) unaryRep = "f(a)(pow(a,a))";
        else if (op instanceof ScalarFunctions.SquaredDifference) unaryRep = "f(a)(0)";
        else return Optional.empty();
        return Optional.of(new DoubleUnaryOperator() {
            @Override
            public double applyAsDouble(double operand) { return op.applyAsDouble(operand, operand); }
            @Override
            public String toString() { return unaryRep; }
        });
    }

}
