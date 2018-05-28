// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.searchlib.rankingexpression.integration.onnx.importer;

import com.google.protobuf.ByteString;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import onnx.Onnx;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Converts Onnx tensors into Vespa tensors.
 *
 * @author lesters
 */
public class TensorConverter {

    public static Tensor toVespaTensor(Onnx.TensorProto tensorProto, OrderedTensorType type) {
        Values values = readValuesOf(tensorProto);
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder) Tensor.Builder.of(type.type());
        for (int i = 0; i < values.size(); i++) {
            builder.cellByDirectIndex(type.toDirectIndex(i), values.get(i));
        }
        return builder.build();
    }

    /* todo: support more types */
    private static Values readValuesOf(Onnx.TensorProto tensorProto) {
        if (tensorProto.hasRawData()) {
            switch (tensorProto.getDataType()) {
                case FLOAT: return new RawFloatValues(tensorProto);
            }
        } else {
            switch (tensorProto.getDataType()) {
                case FLOAT: return new FloatValues(tensorProto);
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

    private static class FloatValues extends Values {
        private final Onnx.TensorProto tensorProto;
        FloatValues(Onnx.TensorProto tensorProto) {
            this.tensorProto = tensorProto;
        }
        @Override double get(int i) { return tensorProto.getFloatData(i); }
        @Override int size() { return tensorProto.getFloatDataCount(); }
    }


}
