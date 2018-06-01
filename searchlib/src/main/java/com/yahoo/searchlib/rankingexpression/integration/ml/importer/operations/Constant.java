// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.Optional;

public class Constant extends IntermediateOperation {

    private final String modelName;

    public Constant(String name, OrderedTensorType type, String modelName) {
        super(name, Collections.emptyList());
        this.modelName = modelName;
        this.type = type.rename(vespaName() + "_");
    }

    /** Constant names are prefixed by "modelName_" to avoid name conflicts between models */
    @Override
    public String vespaName() {
        return modelName + "_" + vespaName(name);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return type;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;  // will be added by function() since this is constant.
    }

    /**
     * Constant values are sent in via the constantValueFunction, as the
     * dimension names and thus the data layout depends on the dimension
     * renaming which happens after the conversion to intermediate graph.
     */
    @Override
    public Optional<Value> getConstantValue() {
        return Optional.ofNullable(constantValueFunction).map(func -> func.apply(type));
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    @Override
    public boolean isConstant() {
        return true;
    }

}
