// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.rule.OperationNode;
import com.yahoo.searchlib.rankingexpression.rule.Operator;
import com.yahoo.searchlib.rankingexpression.rule.ConstantNode;
import com.yahoo.searchlib.rankingexpression.rule.TensorFunctionNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.ScalarFunctions;
import com.yahoo.tensor.functions.TensorFunction;
import com.yahoo.text.ExpressionFormatter;

import java.util.List;
import java.util.Optional;

public class Gemm extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private final float alpha, beta;
    private final int transposeA, transposeB;

    private final static DoubleValue zero = DoubleValue.frozen(0.0);
    private final static DoubleValue one = DoubleValue.frozen(1.0);

    public Gemm(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        this.alpha = (float) attributeMap.get("alpha").orElse(one).asDouble();
        this.beta = (float) attributeMap.get("beta").orElse(one).asDouble();
        this.transposeA = (int) attributeMap.get("transA").orElse(zero).asDouble();
        this.transposeB = (int) attributeMap.get("transB").orElse(zero).asDouble();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! check2or3InputsPresent()) return null;

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());

        TensorType.Dimension dimA = inputs.get(0).type().get().dimensions().get(transposeA);
        TensorType.Dimension dimB = inputs.get(1).type().get().dimensions().get(1 - transposeB);

        typeBuilder.add(dimA);
        typeBuilder.add(dimB);
        OrderedTensorType result = typeBuilder.build();

        // Input tensor C. The shape of C should be unidirectional "broadcastable" to (dimA, dimB).
        if (inputs.size() == 3) {
            List<TensorType.Dimension> cDimensions = inputs.get(2).type().get().dimensions();
            if (cDimensions.size() == 2) {
                TensorType.Dimension dimC0 = cDimensions.get(0);
                TensorType.Dimension dimC1 = cDimensions.get(1);

                if ( ! (dimA.size().get().equals(dimC0.size().get()) || dimC0.size().get() == 1) ) {
                    throw new IllegalArgumentException("GEMM: type of optional input C " + inputs.get(2).type().get() +
                            " is not compatible or not broadcastable to " + result.type());
                }
                if ( ! (dimB.size().get().equals(dimC1.size().get()) || dimC1.size().get() == 1) ) {
                    throw new IllegalArgumentException("GEMM: type of optional input C " + inputs.get(2).type().get() +
                            " is not compatible or not broadcastable to " + result.type());
                }

            } else if (cDimensions.size() == 1) {
                TensorType.Dimension dimC0 = cDimensions.get(0);
                if ( ! (dimB.size().get().equals(dimC0.size().get()) || dimC0.size().get() == 1) ) {
                    throw new IllegalArgumentException("GEMM: type of optional input C " + inputs.get(2).type().get() +
                            " is not compatible or not broadcastable to " + result.type());
                }
            } else {
                throw new IllegalArgumentException("GEMM: optional input C has no dimensions.");
            }
        }

        return result;
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! check2or3InputsPresent()) return null;

        OrderedTensorType aType = inputs.get(0).type().get();
        OrderedTensorType bType = inputs.get(1).type().get();
        if (aType.type().rank() != 2 || bType.type().rank() != 2)
            throw new IllegalArgumentException("Tensors in Gemm must have rank of exactly 2");

        Optional<TensorFunction<Reference>> aFunction = inputs.get(0).function();
        Optional<TensorFunction<Reference>> bFunction = inputs.get(1).function();
        if (aFunction.isEmpty() || bFunction.isEmpty()) {
            return null;
        }

        String joinDimension = aType.dimensions().get(1 - transposeA).name();

        TensorFunction<Reference> AxB = new com.yahoo.tensor.functions.Matmul<>(aFunction.get(), bFunction.get(), joinDimension);
        TensorFunction<Reference> alphaxAxB = new TensorFunctionNode.ExpressionTensorFunction(
                new OperationNode(
                        new TensorFunctionNode(AxB),
                        Operator.multiply,
                        new ConstantNode(new DoubleValue(alpha))));

        if (inputs.size() == 3) {
            Optional<TensorFunction<Reference>> cFunction = inputs.get(2).function();
            TensorFunction<Reference> betaxC = new TensorFunctionNode.ExpressionTensorFunction(
                    new OperationNode(
                            new TensorFunctionNode(cFunction.get()),
                            Operator.multiply,
                            new ConstantNode(new DoubleValue(beta))));
            return new com.yahoo.tensor.functions.Join<>(alphaxAxB, betaxC, ScalarFunctions.add());
        }

        return alphaxAxB;
    }

    private boolean check2or3InputsPresent() {
        if (inputs.size() != 2 && inputs.size() != 3) {
            throw new IllegalArgumentException("Expected 2 or 3 inputs for '" + name + "', got " + inputs.size());
        }
        return inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if ( ! check2or3InputsPresent()) return;

        List<TensorType.Dimension> aDimensions = inputs.get(0).type().get().dimensions();
        List<TensorType.Dimension> bDimensions = inputs.get(1).type().get().dimensions();

        assertTwoDimensions(aDimensions, inputs.get(0), "first argument");
        assertTwoDimensions(bDimensions, inputs.get(1), "second argument");

        String aDim0 = aDimensions.get(transposeA).name();
        String aDim1 = aDimensions.get(1 - transposeA).name();
        String bDim0 = bDimensions.get(transposeB).name();
        String bDim1 = bDimensions.get(1 - transposeB).name();

        // The second dimension of a should have the same name as the first dimension of b
        renamer.addConstraint(aDim1, bDim0, DimensionRenamer.Constraint.equal(false), this);

        // The first dimension of a should have a different name than the second dimension of b
        renamer.addConstraint(aDim0, bDim1, DimensionRenamer.Constraint.lessThan(false), this);

        // If c is given, should be unidirectionally broadcastable to tensor a * b:
        // Tensor A and B both have exactly the same shape.
        // Tensor A and B all have the same number of dimensions and the length of each dimensions is either a common length or B's length is 1.
        // Tensor B has too few dimensions, and B can have its shapes prepended with a dimension of length 1 to satisfy property 2.
        if (inputs.size() == 3) {
            List<TensorType.Dimension> cDimensions = inputs.get(2).type().get().dimensions();

            if (cDimensions.size() == 2) {
                String cDim0 = cDimensions.get(0).name();
                String cDim1 = cDimensions.get(1).name();
                renamer.addConstraint(aDim0, cDim0, DimensionRenamer.Constraint.equal(false), this);
                renamer.addConstraint(bDim1, cDim1, DimensionRenamer.Constraint.equal(false), this);
            } else if (cDimensions.size() == 1) {
                String cDim0 = cDimensions.get(0).name();
                renamer.addConstraint(bDim1, cDim0, DimensionRenamer.Constraint.equal(false), this);
            }
        }

        // For efficiency, the dimensions to join over should be innermost - soft constraint
        renamer.addConstraint(aDim0, aDim1, DimensionRenamer.Constraint.lessThan(true), this);
        renamer.addConstraint(bDim0, bDim1, DimensionRenamer.Constraint.greaterThan(true), this);
    }

    private void assertTwoDimensions(List<TensorType.Dimension> dimensions, IntermediateOperation supplier, String inputDescription) {
        if (dimensions.size() >= 2) return;
        throw new IllegalArgumentException("Expected 2 dimensions in the " + inputDescription + " to " + this +
                                           " but got just " + dimensions + " from\n" +
                                           ExpressionFormatter.inTwoColumnMode(70, 50).format(supplier.toFullString()));
    }

    @Override
    public Gemm withInputs(List<IntermediateOperation> inputs) {
        return new Gemm(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "Gemm"; }

}
