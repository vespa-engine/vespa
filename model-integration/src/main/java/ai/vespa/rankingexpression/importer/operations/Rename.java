// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.DimensionRenamer;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;

/**
 * Renames a tensor dimension to relax dimension constraints
 *
 * @author bratseth
 */
public class Rename extends IntermediateOperation {

    private String from, to;

    public Rename(String modelName, String from, String to, IntermediateOperation input) {
        super(modelName, "rename", input != null ? List.of(input) : Collections.emptyList());
        this.from = from;
        this.to = to;
    }

    @Override
    boolean allInputFunctionsPresent(int expected) {
        return super.allInputFunctionsPresent(expected);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        if ( ! allInputTypesPresent(1)) return null;

        OrderedTensorType inputType = inputs.get(0).type().orElse(null);
        if (inputType == null) return null;

        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(inputType.type().valueType());
        for (TensorType.Dimension dimension : inputType.dimensions())
            builder.add(dimension.withName(dimension.name().equals(from) ? to : dimension.name()));
        return builder.build();
    }

    @Override
    protected TensorFunction<Reference> lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;
        return new com.yahoo.tensor.functions.Rename<>(inputs.get(0).function().orElse(null), from, to);
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        renamer.addDimension(to);
    }

    public void renameDimensions(DimensionRenamer renamer) {
        super.renameDimensions(renamer);
        from = renamer.dimensionNameOf(from).orElse(from);
        to = renamer.dimensionNameOf(to).orElse(to);
    }

    @Override
    public Rename withInputs(List<IntermediateOperation> inputs) {
        if (inputs.size() != 1)
            throw new IllegalArgumentException("Rename require 1 input, not " + inputs.size());
        return new Rename(modelName(), from, to, inputs.get(0));
    }

    @Override
    public String operationName() { return "Rename"; }

}


