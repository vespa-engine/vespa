// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.EmbracedNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.Join;
import com.yahoo.tensor.functions.Reduce;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.Slice;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode.wrapScalar;

public class MatMul extends IntermediateOperation {

    public MatMul(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(2)) return null;

        OrderedTensorType typeA = inputs.get(0).type().get();
        OrderedTensorType typeB = inputs.get(1).type().get();

        if (typeA.type().rank() < 1 || typeB.type().rank() < 1)
            throw new IllegalArgumentException("Tensors in matmul must have rank of at least 1");

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        OrderedTensorType largestRankType = typeA.rank() >= typeB.rank() ? typeA : typeB;
        OrderedTensorType smallestRankType = typeA.rank() >= typeB.rank() ? typeB : typeA;
        for (int i = 0; i < largestRankType.rank() - 2; ++i) {
            TensorType.Dimension dim = largestRankType.dimensions().get(i);
            // broadcasting
            int j = smallestRankType.rank() - largestRankType.rank() + i;
            if (j >= 0 && smallestRankType.dimensions().get(j).size().get() > dim.size().get()) {
                dim = smallestRankType.dimensions().get(j);
            }
            typeBuilder.add(dim);
        }
        if (typeA.rank() >= 2) {
            typeBuilder.add(typeA.dimensions().get(typeA.rank() - 2));
        }
        if (typeB.rank() >= 2) {
            typeBuilder.add(typeB.dimensions().get(typeB.rank() - 1));
        }
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputTypesPresent(2)) return null;
        if ( ! allInputFunctionsPresent(2)) return null;

        OrderedTensorType typeA = inputs.get(0).type().get();
        OrderedTensorType typeB = inputs.get(1).type().get();

        TensorFunction<Reference> functionA = handleBroadcasting(inputs.get(0).function().get(), typeA, typeB);
        TensorFunction<Reference> functionB = handleBroadcasting(inputs.get(1).function().get(), typeB, typeA);

        return new com.yahoo.tensor.functions.Reduce<Reference>(
                    new Join<Reference>(functionA, functionB, ScalarFunctions.multiply()),
                    Reduce.Aggregator.sum,
                    typeA.dimensions().get(typeA.rank() - 1).name());
    }

    private TensorFunction<Reference> handleBroadcasting(TensorFunction<Reference> tensorFunction, OrderedTensorType typeA, OrderedTensorType typeB) {
        List<Slice.DimensionValue<Reference>> slices = new ArrayList<>();
        for (int i = 0; i < typeA.rank() - 2; ++i) {
            long dimSizeA = typeA.dimensions().get(i).size().get();
            String dimNameA = typeA.dimensionNames().get(i);
            int j = typeB.rank() - typeA.rank() + i;
            if (j >= 0) {
                long dimSizeB = typeB.dimensions().get(j).size().get();
                if (dimSizeB > dimSizeA && dimSizeA == 1) {
                    ExpressionNode dimensionExpression = new EmbracedNode(new ConstantNode(DoubleValue.zero));
                    slices.add(new Slice.DimensionValue<>(Optional.of(dimNameA), wrapScalar(dimensionExpression)));
                }
            }
        }
        return slices.size() == 0 ? tensorFunction : new Slice<>(tensorFunction, slices);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if ( ! allInputTypesPresent(2)) return;

        OrderedTensorType typeA = inputs.get(0).type().get();
        OrderedTensorType typeB = inputs.get(1).type().get();

        String lastDimA = typeA.dimensions().get(typeA.rank()-1).name();
        String lastDimB = typeB.dimensions().get(typeB.rank()-1).name();
        String secondLastDimA = typeA.dimensions().get(Math.max(0,typeA.rank()-2)).name();
        String secondLastDimB = typeB.dimensions().get(Math.max(0,typeB.rank()-2)).name();

        // The last dimension of A should have the same name as the second-to-last dimension of B
        renamer.addConstraint(lastDimA, secondLastDimB, DimensionRenamer.Constraint.equal(false), this);

        // The second-to-last dimension of a should have a different name than the last dimension of b
        if (typeA.rank() >= 2 && typeB.rank() >= 2) {
            renamer.addConstraint(secondLastDimA, lastDimB, DimensionRenamer.Constraint.lessThan(false), this);
        }

        // For efficiency, the dimensions to join over should be innermost - soft constraint
        if (typeA.rank() >= 2) {
            renamer.addConstraint(secondLastDimA, lastDimA, DimensionRenamer.Constraint.lessThan(true), this);
        }
        if (typeB.rank() >= 2) {
            renamer.addConstraint(secondLastDimB, lastDimB, DimensionRenamer.Constraint.greaterThan(true), this);
        }

        // Handle different cases when at least one of the tensors have rank > 2
        for (int i = 0; i < typeA.rank() - 2; ++i) {
            String iDim = typeA.dimensionNames().get(i);

            // a1 < a2 < a3 < a4
            for (int j = i+1; j < typeA.rank(); ++j) {
                String jDim = typeA.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.lessThan(false), this);
            }
            // not equal to last 2 dimensions in B
            for (int j = typeB.rank()-2; j < typeB.rank(); ++j) {
                if (j < 0) continue;
                String jDim = typeB.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.notEqual(false), this);
            }
            // equal to matching dimension in tensor B
            int j = typeB.rank() - typeA.rank() + i;
            if (j >= 0) {
                String jDim = typeB.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.equal(false), this);
            }
        }

        for (int i = 0; i < typeB.rank() - 2; ++i) {
            String iDim = typeB.dimensionNames().get(i);

            // b1 < b2 < b3 < b4
            for (int j = i+1; j < typeB.rank(); ++j) {
                String jDim = typeB.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.lessThan(false), this);
            }
            // not equal to last 2 dimensions in A
            for (int j = typeA.rank()-2; j < typeA.rank(); ++j) {
                if (j < 0) continue;
                String jDim = typeA.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.notEqual(false), this);
            }
            // equal to matching dimension in tensor A
            int j = typeA.rank() - typeB.rank() + i;
            if (j >= 0) {
                String jDim = typeA.dimensionNames().get(j);
                renamer.addConstraint(iDim, jDim, DimensionRenamer.Constraint.equal(false), this);
            }
        }
    }

    @Override
    public MatMul withInputs(List<IntermediateOperation> inputs) {
        return new MatMul(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "MatMul"; }

}
