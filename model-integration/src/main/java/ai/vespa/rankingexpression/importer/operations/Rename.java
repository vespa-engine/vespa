// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.operations;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.List;

/**
 * Renames a tensor dimension to relax dimension constraints
 *
 * @author bratseth
 */
public class Rename extends IntermediateOperation {

    private final String from, to;

    public Rename(String modelName, String from, String to, IntermediateOperation input) {
        super(modelName, "rename", List.of(input));
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
    protected TensorFunction lazyGetFunction() {
        if ( ! allInputFunctionsPresent(1)) return null;
        return new com.yahoo.tensor.functions.Rename(inputs.get(0).function().orElse(null), from, to);
    }

}
