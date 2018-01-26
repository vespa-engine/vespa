// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.framework.AttrValue;
import org.tensorflow.framework.NodeDef;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;

/**
 * @author lesters
 */
public class AttrValueConverter {

    public static Tensor toVespaTensor(NodeDef tfNode, String attr) {
        if (!tfNode.getAttrMap().containsKey(attr)) {
            throw new IllegalArgumentException(tfNode.getName() + " has no attribute called " + attr);
        }
        AttrValue attrValue = tfNode.getAttrMap().get(attr);
        switch (attrValue.getValueCase()) {
            case TENSOR:
                return buildFromTensor(attrValue);
            case B:
                return buildFromSingleValue(attrValue.getB() ? 1.0 : 0.0);
            case F:
                return buildFromSingleValue(attrValue.getF());
            case I:
                return buildFromSingleValue(attrValue.getI());
        }

        throw new IllegalArgumentException(tfNode.getName() +
                ": unsupported attribute type: '" + attrValue.getValueCase().toString() + "'");
    }

    private static Tensor buildFromSingleValue(double value) {
        TensorType type = new TensorType.Builder().build();
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type);
        builder.cellByDirectIndex(0, value);
        return builder.build();
    }

    private static Tensor buildFromTensor(AttrValue attrValue) {
        TensorProto tensorProto = attrValue.getTensor();
        TensorType type = toVespaTensorType(tensorProto.getTensorShape());
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type);
        Values values = valuesOf(tensorProto);
        for (int i = 0; i < values.size(); ++i) {
            builder.cellByDirectIndex(i, values.get(i));
        }
        Tensor tensor = builder.build();
        return tensor;
    }

    private static Values valuesOf(TensorProto tensorProto) {
        switch (tensorProto.getDtype()) {
            case DT_BOOL:
                return new BoolValues(tensorProto);
            case DT_HALF:
                return new HalfValues(tensorProto);
            case DT_INT16:
            case DT_INT32:
                return new IntValues(tensorProto);
            case DT_INT64:
                return new Int64Values(tensorProto);
            case DT_FLOAT:
                return new FloatValues(tensorProto);
            case DT_DOUBLE:
                return new DoubleValues(tensorProto);
        }

        throw new IllegalArgumentException("Unsupported data type in attribute tensor import");
    }

    public static TensorType toVespaTensorType(TensorShapeProto shapeProto) {
        TensorType.Builder b = new TensorType.Builder();
        for (TensorShapeProto.Dim dimension : shapeProto.getDimList()) {
            int dimensionSize = (int)dimension.getSize();
            if (dimensionSize >= 0)
                b.indexed("d" + b.rank(), dimensionSize);
            else
                b.indexed("d" + b.rank()); // unbound size
        }
        return b.build();
    }

    private static abstract class Values {
        protected final TensorProto tensorProto;
        protected Values(TensorProto tensorProto) { this.tensorProto = tensorProto; }
        abstract double get(int i);
        abstract int size();
    }

    private static class BoolValues extends Values {
        BoolValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getBoolVal(i) ? 1.0 : 0.0; }
        @Override int size() { return tensorProto.getBoolValCount(); }
    }

    private static class HalfValues extends Values {
        HalfValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getHalfVal(i); }
        @Override int size() { return tensorProto.getHalfValCount(); }
    }

    private static class IntValues extends Values {
        IntValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getIntVal(i); }
        @Override int size() { return tensorProto.getIntValCount(); }
    }

    private static class Int64Values extends Values {
        Int64Values(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getInt64Val(i); }
        @Override int size() { return tensorProto.getInt64ValCount(); }
    }

    private static class FloatValues extends Values {
        FloatValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getFloatVal(i); }
        @Override int size() { return tensorProto.getFloatValCount(); }
    }

    private static class DoubleValues extends Values {
        DoubleValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getDoubleVal(i); }
        @Override int size() { return tensorProto.getDoubleValCount(); }
    }


}
