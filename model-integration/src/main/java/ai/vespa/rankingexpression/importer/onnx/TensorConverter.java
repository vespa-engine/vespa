// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.rankingexpression.importer.onnx;

import com.google.protobuf.ByteString;
import ai.vespa.rankingexpression.importer.OrderedTensorType;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import onnx.Onnx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

/**
 * Converts Onnx tensors into Vespa tensors.
 *
 * @author lesters
 */
class TensorConverter {

    static Tensor toVespaTensor(Onnx.TensorProto tensorProto, OrderedTensorType type) {
        Values values = readValuesOf(tensorProto);
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type.type());
        for (int i = 0; i < values.size(); i++) {
            builder.cellByDirectIndex(type.toDirectIndex(i), values.get(i));
        }
        return builder.build();
    }

    private static Values readValuesOf(Onnx.TensorProto tensorProto) {
        if (tensorProto.hasRawData()) {
            switch (tensorProto.getDataType()) {
                case BOOL: return new RawBoolValues(tensorProto);
                case FLOAT: return new RawFloatValues(tensorProto);
                case DOUBLE: return new RawDoubleValues(tensorProto);
                case INT32: return new RawIntValues(tensorProto);
                case INT64: return new RawLongValues(tensorProto);
            }
        } else {
            switch (tensorProto.getDataType()) {
                case FLOAT: return new FloatValues(tensorProto);
                case DOUBLE: return new DoubleValues(tensorProto);
                case INT32: return new IntValues(tensorProto);
                case INT64: return new LongValues(tensorProto);
            }
        }
        throw new IllegalArgumentException("Cannot convert a tensor with elements of type " +
                tensorProto.getDataType() + " to a Vespa tensor");
    }

    /** Allows reading values from buffers of various numeric types as bytes */
    private static abstract class Values {
        abstract double get(int i);
        abstract int size();
    }

    private static abstract class RawValues extends Values {
        ByteBuffer bytes(Onnx.TensorProto tensorProto) {
            ByteString byteString = tensorProto.getRawData();
            return byteString.asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    private static class RawBoolValues extends RawValues {
        private final IntBuffer values;
        private final int size;
        RawBoolValues(Onnx.TensorProto tensorProto) {
            values = bytes(tensorProto).asIntBuffer();
            size = values.remaining();
        }
        @Override double get(int i) { return values.get(i); }
        @Override int size() { return size; }
    }

    private static class RawFloatValues extends RawValues {
        private final FloatBuffer values;
        private final int size;
        RawFloatValues(Onnx.TensorProto tensorProto) {
            values = bytes(tensorProto).asFloatBuffer();
            size = values.remaining();
        }
        @Override double get(int i) { return values.get(i); }
        @Override int size() { return size; }
    }

    private static class RawDoubleValues extends RawValues {
        private final DoubleBuffer values;
        private final int size;
        RawDoubleValues(Onnx.TensorProto tensorProto) {
            values = bytes(tensorProto).asDoubleBuffer();
            size = values.remaining();
        }
        @Override double get(int i) { return values.get(i); }
        @Override int size() { return size; }
    }

    private static class RawIntValues extends RawValues {
        private final IntBuffer values;
        private final int size;
        RawIntValues(Onnx.TensorProto tensorProto) {
            values = bytes(tensorProto).asIntBuffer();
            size = values.remaining();
        }
        @Override double get(int i) { return values.get(i); }
        @Override int size() { return size; }
    }

    private static class RawLongValues extends RawValues {
        private final LongBuffer values;
        private final int size;
        RawLongValues(Onnx.TensorProto tensorProto) {
            values = bytes(tensorProto).asLongBuffer();
            size = values.remaining();
        }
        @Override double get(int i) { return values.get(i); }
        @Override int size() { return size; }
    }

    private static class FloatValues extends Values {
        private final Onnx.TensorProto tensorProto;
        FloatValues(Onnx.TensorProto tensorProto) {
            this.tensorProto = tensorProto;
        }
        @Override double get(int i) { return tensorProto.getFloatData(i); }
        @Override int size() { return tensorProto.getFloatDataCount(); }
    }

    private static class DoubleValues extends Values {
        private final Onnx.TensorProto tensorProto;
        DoubleValues(Onnx.TensorProto tensorProto) {
            this.tensorProto = tensorProto;
        }
        @Override double get(int i) { return tensorProto.getDoubleData(i); }
        @Override int size() { return tensorProto.getDoubleDataCount(); }
    }

    private static class IntValues extends Values {
        private final Onnx.TensorProto tensorProto;
        IntValues(Onnx.TensorProto tensorProto) {
            this.tensorProto = tensorProto;
        }
        @Override double get(int i) { return tensorProto.getInt32Data(i); }
        @Override int size() { return tensorProto.getInt32DataCount(); }
    }

    private static class LongValues extends Values {
        private final Onnx.TensorProto tensorProto;
        LongValues(Onnx.TensorProto tensorProto) {
            this.tensorProto = tensorProto;
        }
        @Override double get(int i) { return tensorProto.getInt64Data(i); }
        @Override int size() { return tensorProto.getInt64DataCount(); }
    }

}
