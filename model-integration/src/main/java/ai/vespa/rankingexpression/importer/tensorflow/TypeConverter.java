// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.tensorflow;

import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.TensorType;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.TensorShapeProto;

import java.util.List;

/**
 * Converts and verifies TensorFlow tensor types into Vespa tensor types.
 *
 * @author lesters
 */
class TypeConverter {

    static void verifyType(NodeDef node, OrderedTensorType type) {
        TensorShapeProto shape = tensorFlowShape(node);
        if (shape != null) {
            if (shape.getDimCount() != type.rank()) {
                throw new IllegalArgumentException("TensorFlow shape of '" + node.getName() + "' " +
                                                   "does not match Vespa shape");
            }
            for (int tensorFlowIndex = 0; tensorFlowIndex < type.dimensions().size(); ++tensorFlowIndex) {
                int vespaIndex = type.dimensionMap(tensorFlowIndex);
                TensorShapeProto.Dim tensorFlowDimension = shape.getDim(tensorFlowIndex);
                TensorType.Dimension vespaDimension = type.type().dimensions().get(vespaIndex);
                if (tensorFlowDimension.getSize() != vespaDimension.size().orElse(-1L)) {
                    throw new IllegalArgumentException("TensorFlow dimensions of '" + node.getName() + "' " +
                                                       "does not match Vespa dimensions");
                }
            }
        }
    }

    private static TensorShapeProto tensorFlowShape(NodeDef node) {
        AttrValue attrValueList = node.getAttrMap().get("_output_shapes");
        if (attrValueList == null)
            throw new IllegalArgumentException("_output_shapes attribute of '" + node.getName() + "' " +
                                               "does not exist");
        if (attrValueList.getValueCase() != AttrValue.ValueCase.LIST)
            throw new IllegalArgumentException("_output_shapes attribute of '" + node.getName() + "' " +
                                               "is not of expected type");

        return attrValueList.getList().getShape(0); // support multiple outputs?
    }

    private static DataType tensorFlowValueType(NodeDef node) {
        AttrValue attrValueList = node.getAttrMap().get("dtypes");
        if (attrValueList == null)
            return DataType.DT_DOUBLE; // default. This will usually (always?) be used. TODO: How can we do better?
        if (attrValueList.getValueCase() != AttrValue.ValueCase.LIST)
            return DataType.DT_DOUBLE; // default

        return attrValueList.getList().getType(0); // support multiple outputs?
    }

    static OrderedTensorType fromTensorFlowType(NodeDef node) {
        return fromTensorFlowType(node, "d");  // standard naming convention: d0, d1, ...
    }

    private static OrderedTensorType fromTensorFlowType(NodeDef node, String dimensionPrefix) {
        TensorShapeProto shape = tensorFlowShape(node);
        OrderedTensorType.Builder builder = new OrderedTensorType.Builder(toValueType(tensorFlowValueType(node)));
        for (int i = 0; i < shape.getDimCount(); ++ i) {
            String dimensionName = dimensionPrefix + i;
            TensorShapeProto.Dim tensorFlowDimension = shape.getDim(i);
            if (tensorFlowDimension.getSize() >= 0) {
                builder.add(TensorType.Dimension.indexed(dimensionName, tensorFlowDimension.getSize()));
            } else {
                builder.add(TensorType.Dimension.indexed(dimensionName));
            }
        }
        return builder.build();
    }

    /** TensorFlow has two different DataType classes. This must be kept in sync with TensorConverter.toValueType */
    static TensorType.Value toValueType(DataType dataType) {
        switch (dataType) {
            case DT_FLOAT: return TensorType.Value.FLOAT;
            case DT_DOUBLE: return TensorType.Value.DOUBLE;
            // Imperfect conversion, for now:
            case DT_BOOL: return TensorType.Value.FLOAT;
            case DT_BFLOAT16: return TensorType.Value.FLOAT;
            case DT_HALF: return TensorType.Value.FLOAT;
            case DT_INT8: return TensorType.Value.FLOAT;
            case DT_INT16: return TensorType.Value.FLOAT;
            case DT_INT32: return TensorType.Value.FLOAT;
            case DT_INT64: return TensorType.Value.DOUBLE;
            case DT_UINT8: return TensorType.Value.FLOAT;
            case DT_UINT16: return TensorType.Value.FLOAT;
            case DT_UINT32: return TensorType.Value.FLOAT;
            case DT_UINT64: return TensorType.Value.DOUBLE;
            default: throw new IllegalArgumentException("A TensorFlow tensor with data type " + dataType +
                                                        " cannot be converted to a Vespa tensor type");
        }
    }

}
