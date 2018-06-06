// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.ml.importer.tensorflow;

import com.yahoo.searchlib.rankingexpression.integration.ml.importer.OrderedTensorType;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import org.tensorflow.framework.TensorProto;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;


/**
 * Converts TensorFlow tensors into Vespa tensors.
 *
 * @author bratseth
 * @author lesters
 */
public class TensorConverter {

    public static Tensor toVespaTensor(org.tensorflow.Tensor<?> tfTensor) {
        return toVespaTensor(tfTensor, "d");
    }

    public static Tensor toVespaTensor(org.tensorflow.Tensor<?> tfTensor, String dimensionPrefix) {
        TensorType type = toVespaTensorType(tfTensor.shape(), dimensionPrefix);
        Values values = readValuesOf(tfTensor);
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type);
        for (int i = 0; i < values.size(); i++)
            builder.cellByDirectIndex(i, values.get(i));
        return builder.build();
    }

    public static Tensor toVespaTensor(org.tensorflow.Tensor<?> tfTensor, OrderedTensorType type) {
        Values values = readValuesOf(tfTensor);
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type.type());
        for (int i = 0; i < values.size(); i++) {
            builder.cellByDirectIndex(type.toDirectIndex(i), values.get(i));
        }
        return builder.build();
    }

    public static Tensor toVespaTensor(TensorProto tensorProto, TensorType type) {
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type);
        Values values = readValuesOf(tensorProto);
        for (int i = 0; i < values.size(); ++i) {
            builder.cellByDirectIndex(i, values.get(i));
        }
        return builder.build();
    }

    private static TensorType toVespaTensorType(long[] shape, String dimensionPrefix) {
        TensorType.Builder b = new TensorType.Builder();
        int dimensionIndex = 0;
        for (long dimensionSize : shape) {
            if (dimensionSize == 0) dimensionSize = 1; // TensorFlow ...
            b.indexed(dimensionPrefix + (dimensionIndex++), dimensionSize);
        }
        return b.build();
    }

    public static Long tensorSize(TensorType type) {
        Long size = 1L;
        for (TensorType.Dimension dimension : type.dimensions()) {
            size *= dimensionSize(dimension);
        }
        return size;
    }

    public static Long dimensionSize(TensorType.Dimension dim) {
        return dim.size().orElseThrow(() -> new IllegalArgumentException("Dimension has no size"));
    }

    private static Values readValuesOf(org.tensorflow.Tensor<?> tfTensor) {
        switch (tfTensor.dataType()) {
            case DOUBLE: return new DoubleValues(tfTensor);
            case FLOAT: return new FloatValues(tfTensor);
            case BOOL: return new BoolValues(tfTensor);
            case UINT8: return new IntValues(tfTensor);
            case INT32: return new IntValues(tfTensor);
            case INT64: return new LongValues(tfTensor);
        }
        throw new IllegalArgumentException("Cannot convert a tensor with elements of type " +
                tfTensor.dataType() + " to a Vespa tensor");
    }

    private static Values readValuesOf(TensorProto tensorProto) {
        switch (tensorProto.getDtype()) {
            case DT_BOOL:
                return new ProtoBoolValues(tensorProto);
            case DT_HALF:
                return new ProtoHalfValues(tensorProto);
            case DT_INT16:
            case DT_INT32:
                return new ProtoIntValues(tensorProto);
            case DT_INT64:
                return new ProtoInt64Values(tensorProto);
            case DT_FLOAT:
                return new ProtoFloatValues(tensorProto);
            case DT_DOUBLE:
                return new ProtoDoubleValues(tensorProto);
        }
        throw new IllegalArgumentException("Unsupported data type in attribute tensor import");
    }

    /** Allows reading values from buffers of various numeric types as bytes */
    private static abstract class Values {
        abstract double get(int i);
        abstract int size();
    }

    private static abstract class TensorFlowValues extends Values {
        private final int size;
        TensorFlowValues(int size) {
            this.size = size;
        }
        @Override int size() { return this.size; }
    }

    private static class DoubleValues extends TensorFlowValues {
        private final DoubleBuffer values;
        DoubleValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = DoubleBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }
        @Override double get(int i) {
            return values.get(i);
        }
    }

    private static class FloatValues extends TensorFlowValues {
        private final FloatBuffer values;
        FloatValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = FloatBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }
        @Override double get(int i) {
            return values.get(i);
        }
    }

    private static class BoolValues extends TensorFlowValues {
        private final ByteBuffer values;
        BoolValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = ByteBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }
        @Override double get(int i) {
            return values.get(i);
        }
    }

    private static class IntValues extends TensorFlowValues {
        private final IntBuffer values;
        IntValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = IntBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }
        @Override double get(int i) {
            return values.get(i);
        }
    }

    private static class LongValues extends TensorFlowValues {
        private final LongBuffer values;
        LongValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = LongBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }
        @Override double get(int i) {
            return values.get(i);
        }
    }

    private static abstract class ProtoValues extends Values {
        protected final TensorProto tensorProto;
        protected ProtoValues(TensorProto tensorProto) { this.tensorProto = tensorProto; }
    }

    private static class ProtoBoolValues extends ProtoValues {
        ProtoBoolValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getBoolVal(i) ? 1.0 : 0.0; }
        @Override int size() { return tensorProto.getBoolValCount(); }
    }

    private static class ProtoHalfValues extends ProtoValues {
        ProtoHalfValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getHalfVal(i); }
        @Override int size() { return tensorProto.getHalfValCount(); }
    }

    private static class ProtoIntValues extends ProtoValues {
        ProtoIntValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getIntVal(i); }
        @Override int size() { return tensorProto.getIntValCount(); }
    }

    private static class ProtoInt64Values extends ProtoValues {
        ProtoInt64Values(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getInt64Val(i); }
        @Override int size() { return tensorProto.getInt64ValCount(); }
    }

    private static class ProtoFloatValues extends ProtoValues {
        ProtoFloatValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getFloatVal(i); }
        @Override int size() { return tensorProto.getFloatValCount(); }
    }

    private static class ProtoDoubleValues extends ProtoValues {
        ProtoDoubleValues(TensorProto tensorProto) { super(tensorProto); }
        @Override double get(int i) { return tensorProto.getDoubleVal(i); }
        @Override int size() { return tensorProto.getDoubleValCount(); }
    }

}
