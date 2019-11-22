// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import onnx.Onnx;

/**
 * Converts and verifies ONNX tensor types into Vespa tensor types.
 *
 * @author lesters
 */
class TypeConverter {

    static void verifyType(Onnx.TypeProto typeProto, OrderedTensorType type) {
        Onnx.TensorShapeProto shape = typeProto.getTensorType().getShape();
        if (shape != null) {
            if (shape.getDimCount() != type.rank()) {
                throw new IllegalArgumentException("Onnx shape of does not match Vespa shape");
            }
            for (int onnxIndex = 0; onnxIndex < type.dimensions().size(); ++onnxIndex) {
                int vespaIndex = type.dimensionMap(onnxIndex);
                Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(onnxIndex);
                TensorType.Dimension vespaDimension = type.type().dimensions().get(vespaIndex);
                long onnxDimensionSize = onnxDimension.getDimValue() == 0 ? 1 : onnxDimension.getDimValue();
                if (onnxDimensionSize != vespaDimension.size().orElse(-1L)) {
                    throw new IllegalArgumentException("Onnx dimensions of does not match Vespa dimensions");
                }
            }
        }
    }

    static OrderedTensorType typeFrom(Onnx.TypeProto type) {
        String dimensionPrefix = "d"; // standard naming convention: d0, d1, ...
        Onnx.TensorShapeProto shape = type.getTensorType().getShape();
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(toValueType(type.getTensorType().getElemType()));
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            Onnx.TensorShapeProto.Dimension onnxDimension = shape.getDim(i);
            long onnxDimensionSize = onnxDimension.getDimValue() == 0 ? 1 : onnxDimension.getDimValue();
            if (onnxDimensionSize >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, onnxDimensionSize));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    static OrderedTensorType typeFrom(Onnx.TensorProto tensor) {
        return OrderedTensorType.fromDimensionList(toValueType(tensor.getDataType()),
                                                   tensor.getDimsList());
    }

    private static TensorType.Value toValueType(Onnx.TensorProto.DataType dataType) {
        switch (dataType) {
            case FLOAT: return TensorType.Value.FLOAT;
            case DOUBLE: return TensorType.Value.DOUBLE;
            // Imperfect conversion, for now:
            case BOOL: return TensorType.Value.FLOAT;
            case INT8: return TensorType.Value.FLOAT;
            case INT16: return TensorType.Value.FLOAT;
            case INT32: return TensorType.Value.DOUBLE;
            case INT64: return TensorType.Value.DOUBLE;
            case UINT8: return TensorType.Value.FLOAT;
            case UINT16: return TensorType.Value.FLOAT;
            case UINT32: return TensorType.Value.DOUBLE;
            case UINT64: return TensorType.Value.DOUBLE;
            default: throw new IllegalArgumentException("A ONNX tensor with data type " + dataType +
                                                        " cannot be converted to a Vespa tensor type");
        }
    }

}
