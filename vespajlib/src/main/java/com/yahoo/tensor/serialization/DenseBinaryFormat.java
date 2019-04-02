// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.Optional;

/**
 * Implementation of a dense binary format for a tensor on the form:
 *
 * Sorted dimensions = num_dimensions [dimension_str_len dimension_str_bytes dimension_size_int]*
 * Cell_values = [double, double, double, ...]*
 * where values are encoded in order of increasing indexes in each dimension, increasing
 * indexes of later dimensions in the dimension type before earlier.
 *
 * @author bratseth
 */
public class DenseBinaryFormat implements BinaryFormat {

    static private final int DOUBLE_VALUE_TYPE = 0; // Not encoded as it is default, and you know the type when deserializing
    static private final int FLOAT_VALUE_TYPE = 1;

    enum EncodeType {NO_DEFAULT, DOUBLE_IS_DEFAULT}
    private final EncodeType encodeType;
    DenseBinaryFormat() {
        encodeType = EncodeType.DOUBLE_IS_DEFAULT;
    }
    DenseBinaryFormat(EncodeType encodeType) {
        this.encodeType = encodeType;
    }

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        if ( ! ( tensor instanceof IndexedTensor))
            throw new RuntimeException("The dense format is only supported for indexed tensors");
        encodeValueType(buffer, tensor.type().valueType());
        encodeDimensions(buffer, (IndexedTensor)tensor);
        encodeCells(buffer, tensor);
    }

    private void encodeValueType(GrowableByteBuffer buffer, TensorType.Value valueType) {
        switch (valueType) {
            case DOUBLE:
                if (encodeType != EncodeType.DOUBLE_IS_DEFAULT) {
                    buffer.putInt1_4Bytes(DOUBLE_VALUE_TYPE);
                }
                break;
            case FLOAT:
                buffer.putInt1_4Bytes(FLOAT_VALUE_TYPE);
                break;
        }
    }

    private void encodeDimensions(GrowableByteBuffer buffer, IndexedTensor tensor) {
        buffer.putInt1_4Bytes(tensor.type().dimensions().size());
        for (int i = 0; i < tensor.type().dimensions().size(); i++) {
            buffer.putUtf8String(tensor.type().dimensions().get(i).name());
            buffer.putInt1_4Bytes((int)tensor.dimensionSizes().size(i)); // XXX: Size truncation
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, Tensor tensor) {
        switch (tensor.type().valueType()) {
            case DOUBLE:
                encodeCellsAsDouble(buffer, tensor);
                break;
            case FLOAT:
                encodeCellsAsFloat(buffer, tensor);
                break;
        }
    }

    private void encodeCellsAsDouble(GrowableByteBuffer buffer, Tensor tensor) {
        Iterator<Double> i = tensor.valueIterator();
        while (i.hasNext())
            buffer.putDouble(i.next());
    }

    private void encodeCellsAsFloat(GrowableByteBuffer buffer, Tensor tensor) {
        Iterator<Double> i = tensor.valueIterator();
        while (i.hasNext())
            buffer.putFloat(i.next().floatValue());
    }

    @Override
    public Tensor decode(Optional<TensorType> optionalType, GrowableByteBuffer buffer) {
        TensorType type;
        DimensionSizes sizes;
        if (optionalType.isPresent()) {
            type = optionalType.get();
            TensorType serializedType = decodeType(buffer, type.valueType());
            if ( ! serializedType.isAssignableTo(type))
                throw new IllegalArgumentException("Type/instance mismatch: A tensor of type " + serializedType +
                                                   " cannot be assigned to type " + type);
            sizes = sizesFromType(serializedType);
        }
        else {
            type = decodeType(buffer, TensorType.Value.DOUBLE);
            sizes = sizesFromType(type);
        }
        Tensor.Builder builder = Tensor.Builder.of(type, sizes);
        decodeCells(type.valueType(), sizes, buffer, (IndexedTensor.BoundBuilder)builder);
        return builder.build();
    }

    private TensorType decodeType(GrowableByteBuffer buffer, TensorType.Value valueType) {
        TensorType.Value serializedValueType = TensorType.Value.DOUBLE;
        if ((valueType != TensorType.Value.DOUBLE) || (encodeType != EncodeType.DOUBLE_IS_DEFAULT)) {
            int type = buffer.getInt1_4Bytes();
            switch (type) {
                case DOUBLE_VALUE_TYPE:
                    serializedValueType = TensorType.Value.DOUBLE;
                    break;
                case FLOAT_VALUE_TYPE:
                    serializedValueType = TensorType.Value.FLOAT;
                    break;
                default:
                    throw new IllegalArgumentException("Received tensor value type '" + serializedValueType + "'. Only 0(double), or 1(float) are legal.");
            }
        }
        if (valueType != serializedValueType) {
            throw new IllegalArgumentException("Expected " + valueType + ", got " + serializedValueType);
        }
        TensorType.Builder builder = new TensorType.Builder(serializedValueType);
        int dimensionCount = buffer.getInt1_4Bytes();
        for (int i = 0; i < dimensionCount; i++)
            builder.indexed(buffer.getUtf8String(), buffer.getInt1_4Bytes()); // XXX: Size truncation
        return builder.build();
    }

    /** Returns dimension sizes from a type consisting of fully specified, indexed dimensions only */
    private DimensionSizes sizesFromType(TensorType type) {
        DimensionSizes.Builder builder = new DimensionSizes.Builder(type.dimensions().size());
        for (int i = 0; i < type.dimensions().size(); i++)
            builder.set(i, type.dimensions().get(i).size().get());
        return builder.build();
    }

    private void decodeCells(TensorType.Value valueType, DimensionSizes sizes, GrowableByteBuffer buffer, IndexedTensor.BoundBuilder builder) {
        switch (valueType) {
            case DOUBLE:
                decodeCellsAsDouble(sizes, buffer, builder);
                break;
            case FLOAT:
                decodeCellsAsFloat(sizes, buffer, builder);
                break;
        }
    }

    private void decodeCellsAsDouble(DimensionSizes sizes, GrowableByteBuffer buffer, IndexedTensor.BoundBuilder builder) {
        for (long i = 0; i < sizes.totalSize(); i++)
            builder.cellByDirectIndex(i, buffer.getDouble());
    }
    private void decodeCellsAsFloat(DimensionSizes sizes, GrowableByteBuffer buffer, IndexedTensor.BoundBuilder builder) {
        for (long i = 0; i < sizes.totalSize(); i++)
            builder.cellByDirectIndex(i, buffer.getFloat());
    }

}
