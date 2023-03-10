// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
                long onnxDimensionSize = onnxDimension.getDimValue() == 0 ? 1 : onnxDimension.getDimValue();
                if (onnxDimensionSize == -1) {
                    continue;  // disregard batch dimensions
                }
                TensorType.Dimension vespaDimension = type.type().dimensions().get(vespaIndex);
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

    private static TensorType.Value toValueType(Onnx.TensorProto.DataType onnxType) {
        // NOTE:
        // should match best_cell_type in onnx_wrapper.cpp
        switch (onnxType) {
            case BOOL: // Imperfect conversion fallthrough
            case INT8:
                return TensorType.Value.INT8;
            case BFLOAT16:
                return TensorType.Value.BFLOAT16;
            case UINT8: // Imperfect conversion fallthrough
            case INT16: // Imperfect conversion fallthrough
            case UINT16: // Imperfect conversion fallthrough
            case FLOAT:
                return TensorType.Value.FLOAT;
            case INT32: // Imperfect conversion fallthrough
            case INT64: // Imperfect conversion fallthrough
            case UINT32: // Imperfect conversion fallthrough
            case UINT64: // Imperfect conversion fallthrough
            case DOUBLE:
                return TensorType.Value.DOUBLE;
            default: throw new IllegalArgumentException("A ONNX tensor with data type " + onnxType +
                                                        " cannot be converted to a Vespa tensor type");
        }
    }

}
