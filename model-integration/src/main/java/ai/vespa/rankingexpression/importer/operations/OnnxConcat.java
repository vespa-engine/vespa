// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;
import java.util.Optional;

public class OnnxConcat extends IntermediateOperation {

    private final AttributeMap attributeMap;
    private String concatDimensionName;
    private int concatDimensionIndex;

    public OnnxConcat(String modelName, String nodeName, List<IntermediateOperation> inputs, AttributeMap attributeMap) {
        super(modelName, nodeName, inputs);
        this.attributeMap = attributeMap;
        if (attributeMap.get("axis").isEmpty())
            throw new IllegalArgumentException("OnnxConcat in " + name + ": Required attribute 'axis' is missing.");
        this.concatDimensionIndex = (int) attributeMap.get("axis").get().asDouble();
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! inputs.stream().map(IntermediateOperation::type).allMatch(Optional::isPresent)) return null;

        OrderedTensorType aType = inputs.get(0).type().get();
        if (concatDimensionIndex < 0) {
            concatDimensionIndex = aType.dimensions().size() + concatDimensionIndex;
        }
        long concatDimSize = aType.dimensions().get(concatDimensionIndex).size().orElse(-1L);

        for (int i = 1; i < inputs.size(); ++i) {
            OrderedTensorType bType = inputs.get(i).type().get();
            if (bType.rank() != aType.rank())
                throw new IllegalArgumentException("OnnxConcat in " + name + ": Inputs must have the same rank.");

            for (int j = 0; j < aType.rank(); ++j) {
                long dimSizeA = aType.dimensions().get(j).size().orElse(-1L);
                long dimSizeB = bType.dimensions().get(j).size().orElse(-1L);
                if (j == concatDimensionIndex) {
                    concatDimSize += dimSizeB;
                } else if (dimSizeA != dimSizeB) {
                    throw new IllegalArgumentException("OnnxConcat in " + name + ": " +
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
        for (int i = 1; i < inputs.size(); ++i) {
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
        for (int i = 1; i < inputs.size(); ++i) {
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
    public OnnxConcat withInputs(List<IntermediateOperation> inputs) {
        return new OnnxConcat(modelName(), name(), inputs, attributeMap);
    }

    @Override
    public String operationName() { return "ConcatV2"; }

}
