// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.onnx.importer.operations;

import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.DimensionRenamer;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.OrderedTensorType;
import com.yahoo.searchlib.rankingexpression.integration.onnx.importer.TensorConverter;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.functions.TensorFunction;
import onnx.Onnx;

import java.util.Collections;
import java.util.Optional;

public class Constant extends OnnxOperation {

    final String modelName;
    final Onnx.TensorProto tensorProto;

    public Constant(String modelName, Onnx.TensorProto tensorProto) {
        super(null, Collections.emptyList());
        this.modelName = modelName;
        this.tensorProto = tensorProto;
    }

    /** todo: Constant names are prefixed by "modelName_" to avoid name conflicts between models */
    @Override
    public String vespaName() {
        return modelName + "_" + vespaName(tensorProto.getName());
    }

    @Override
    protected OrderedTensorType lazyGetType() {
        return OrderedTensorType.fromOnnxType(tensorProto.getDimsList(), vespaName() + "_");
    }

    @Override
    protected TensorFunction lazyGetFunction() {
        return null;  // will be added by function() since this is constant.
    }

    @Override
    public Optional<Value> getConstantValue() {
        return Optional.of(new TensorValue(TensorConverter.toVespaTensor(tensorProto, type)));
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
