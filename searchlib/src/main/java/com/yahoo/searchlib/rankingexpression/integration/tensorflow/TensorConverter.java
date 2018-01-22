// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;


/**
 * @author bratseth
 */
public class TensorConverter {

    public Tensor toVespaTensor(org.tensorflow.Tensor<?> tfTensor) {
        TensorType type = toVespaTensorType(tfTensor.shape());
        Values values = readValuesOf(tfTensor);
        IndexedTensor.BoundBuilder builder = (IndexedTensor.BoundBuilder)Tensor.Builder.of(type);
        for (int i = 0; i < values.size(); i++)
            builder.cellByDirectIndex(i, values.get(i));
        return builder.build();
    }

    private TensorType toVespaTensorType(long[] shape) {
        TensorType.Builder b = new TensorType.Builder();
        int dimensionIndex = 0;
        for (long dimensionSize : shape) {
            if (dimensionSize == 0) dimensionSize = 1; // TensorFlow ...
            b.indexed("d" + (dimensionIndex++), dimensionSize);
        }
        return b.build();
    }

    private Values readValuesOf(org.tensorflow.Tensor<?> tfTensor) {
        switch (tfTensor.dataType()) {
            case DOUBLE: return new DoubleValues(tfTensor);
            case FLOAT: return new FloatValues(tfTensor);
            case BOOL: return new BoolValues(tfTensor);
            case UINT8: return new IntValues(tfTensor);
            case INT32: return new IntValues(tfTensor);
            case INT64: return new LongValues(tfTensor);
            default:
                throw new IllegalArgumentException("Cannot convert a tensor with elements of type " +
                                                   tfTensor.dataType() + " to a Vespa tensor");
        }
    }

    /** Allows reading values from buffers of various numeric types as bytes */
    private static abstract class Values {

        private final int size;

        protected Values(int size) {
            this.size = size;
        }

        abstract double get(int i);

        int size() { return size; }

    }

    private static class DoubleValues extends Values {

        private final DoubleBuffer values;

        DoubleValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = DoubleBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }

        @Override
        double get(int i) {
            return values.get(i);
        }

    }

    private static class FloatValues extends Values {

        private final FloatBuffer values;

        FloatValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = FloatBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }

        @Override
        double get(int i) {
            return values.get(i);
        }

    }

    private static class BoolValues extends Values {

        private final ByteBuffer values;

        BoolValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = ByteBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }

        @Override
        double get(int i) {
            return values.get(i);
        }

    }

    private static class IntValues extends Values {

        private final IntBuffer values;

        IntValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = IntBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }

        @Override
        double get(int i) {
            return values.get(i);
        }

    }

    private static class LongValues extends Values {

        private final LongBuffer values;

        LongValues(org.tensorflow.Tensor<?> tfTensor) {
            super(tfTensor.numElements());
            values = LongBuffer.allocate(tfTensor.numElements());
            tfTensor.writeTo(values);
        }

        @Override
        double get(int i) {
            return values.get(i);
        }

    }
}
