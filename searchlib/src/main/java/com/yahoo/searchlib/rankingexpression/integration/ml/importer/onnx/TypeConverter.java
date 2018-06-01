// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.ml.importer.onnx;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import onnx.Onnx;

public class TypeConverter {

    public static void verifyType(Onnx.TypeProto typeProto, OrderedTensorType type) {
        Onnx.TensorShapeProto shape = typeProto.getTensorType().getShape();
        if (shape != null) {
            if (shape.getDimCount() != type.rank()) {
                throw new IllegalArgumentException("Onnx shape of does not match Vespa shape");
            }
            for (int onnxIndex = 0; onnxIndex < type.dimensions().size(); ++onnxIndex) {
                int vespaIndex = type.dimensionMap(onnxIndex);
                Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(onnxIndex);
                TensorType.Dimension vespaDimension = type.type().dimensions().get(vespaIndex);
                if (onnxDimension.getDimValue() != vespaDimension.size().orElse(-1L)) {
                    throw new IllegalArgumentException("Onnx dimensions of does not match Vespa dimensions");
                }
            }
        }
    }

    public static OrderedTensorType fromOnnxType(Onnx.TypeProto type) {
        return fromOnnxType(type, "d");  // standard naming convention: d0, d1, ...
    }

    public static OrderedTensorType fromOnnxType(Onnx.TypeProto type, String dimensionPrefix) {
        Onnx.TensorShapeProto shape = type.getTensorType().getShape();
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder();
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(i);
            if (onnxDimension.getDimValue() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, onnxDimension.getDimValue()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

}
