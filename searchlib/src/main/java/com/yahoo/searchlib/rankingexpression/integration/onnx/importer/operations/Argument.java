// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations;

import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.VariableTensor;
import com.yahoo.tensor.functions.Rename;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;

import java.util.Collections;
import java.util.List;

public class Argument extends OnnxOperation {

    private Onnx.ValueInfoProto valueInfo;
    private OrderedTensorType standardNamingType;  // using standard naming convention: d0, d1, ...

    public Argument(Onnx.ValueInfoProto valueInfoProto) {
        super(null, Collections.emptyList());
        valueInfo = valueInfoProto;
        standardNamingType = OrderedTensorType.fromOnnxType(valueInfo.getType());
    }

    @Override
    public String vespaName() {
        return vespaName(valueInfo.getName());
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return OrderedTensorType.fromOnnxType(valueInfo.getType(), vespaName() + "_");
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
