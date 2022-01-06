// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import ai.vespa.rankingexpression.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConcatV2 extends IntermediateOperation {

    private String concatDimensionName;
    private int concatDimensionIndex;

    public ConcatV2(String modelName, String nodeName, List<IntermediateOperation> inputs) {
        super(modelName, nodeName, inputs);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent)) return null;

        IntermediateOperation concatDimOp = inputs.get(inputs.size() - 1);  // ConcatV2: concat dimension is the last input
        if ( ! concatDimOp.getConstantValue().isPresent())
            throw new IllegalArgumentException("ConcatV2 in " + name + ": Concat dimension must be a constant.");

        Tensor concatDimTensor = concatDimOp.getConstantValue().get().asTensor();
        if (concatDimTensor.type().rank() != 0)
            throw new IllegalArgumentException("ConcatV2 in " + name + ": Concat dimension must be a scalar.");

        OrderedTensorType aType = inputs.get(0).type().get();
        concatDimensionIndex = (int)concatDimTensor.asDouble();
        long concatDimSize = aType.dimensions().get(concatDimensionIndex).size().orElse(-1L);

        for (int i = 1; i < inputs.size() - 1; ++i) {
            OrderedTensorType bType = inputs.get(i).type().get();
            if (bType.rank() != aType.rank())
                throw new IllegalArgumentException("ConcatV2 in " + name + ": Inputs must have the same rank.");

            for (int j = 0; j < aType.rank(); ++j) {
                long dimSizeA = aType.dimensions().get(j).size().orElse(-1L);
                long dimSizeB = bType.dimensions().get(j).size().orElse(-1L);
                if (j == concatDimensionIndex) {
                    concatDimSize += dimSizeB;
                } else if (dimSizeA != dimSizeB) {
                    throw new IllegalArgumentException("ConcatV2 in " + name + ": " +
                                                       "input dimension " + j + " differs in input tensors.");
                }
            }
        }

        OrderedTensorType.Builder typeBuilder = new OrderedTensorType.Builder(resultValueType());
        int dimensionIndex = 0;
        for (TensorType.Dimension dimension : aType.dimensions()) {
            if (dimensionIndex == concatDimensionIndex) {
                concatDimensionName = dimension.name();
                typeBuilder.add(TensorType.Dimension.indexed(concatDimensionName, concatDimSize));
            } else {
                typeBuilder.add(dimension);
            }
            dimensionIndex++;
        }
        return typeBuilder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if (!inputs.stream().map(IntermediateOperation::function).allMatch(Optional::isPresent)) {
            return null;
        }
        TensorFunction<Reference> result = inputs.get(0).function().get();
        for (int i = 1; i < inputs.size() - 1; ++i) {
            TensorFunction<Reference> b = inputs.get(i).function().get();
            result = new com.yahoo.tensor.functions.Concat<>(result, b, concatDimensionName);
        }
        return result;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        if (!inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent)) {
            return;
        }
        OrderedTensorType a = inputs.get(0).type().get();
        for (int i = 1; i < inputs.size() - 1; ++i) {
            OrderedTensorType b = inputs.get(i).type().get();
            String bDim = b.dimensions().get(concatDimensionIndex).name();
            String aDim = a.dimensions().get(concatDimensionIndex).name();
            renamer.addConstraint(aDim, bDim, DimensionRenamer.Constraint.equal(false), this);
        }
    }

    @Override
    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        concatDimensionName = renamer.dimensionNameOf(concatDimensionName).orElse(concatDimensionName);
   }

    @Override
    public ConcatV2 withInputs(List<IntermediateOperation> inputs) {
        return new ConcatV2(modelName(), name(), inputs);
    }

    @Override
    public String operationName() { return "ConcatV2"; }

}
