// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;

import java.util.Collections;
import java.util.List;

public class Argument extends IntermediateOperation {

    private OrderedTensorType standardNamingType;  // using standard naming convention: d0, d1, ...

    public Argument(String name, OrderedTensorType argumentType) {
        super(name, Collections.emptyList());
        type = argumentType;
        standardNamingType = OrderedTensorType.standardType(argumentType);
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return type;
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        TensorFunction output = new VariableTensor(vespaName(), standardNamingType.type());
        if (!standardNamingType.equals(type)) {
            List<String> renameFrom = standardNamingType.dimensionNames();
            List<String> renameTo = type.dimensionNames();
            output = new Rename(output, renameFrom, renameTo);
        }
        return output;
    }

    @Override
    public void addDimensionNameConstraints(DimensionRenamer renamer) {
        for (TensorType.Dimension dimension : type.type().dimensions()) {
            renamer.addDimension(dimension.name());
        }
    }

    @Override
    public boolean isInput() {
        return true;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

}
