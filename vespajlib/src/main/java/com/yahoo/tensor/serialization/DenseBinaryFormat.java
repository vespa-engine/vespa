// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.DimensionSizes;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

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

    private final TensorType.Value serializationValueType;

    DenseBinaryFormat() {
        this(TensorType.Value.DOUBLE);
    }
    DenseBinaryFormat(TensorType.Value serializationValueType) {
        this.serializationValueType = serializationValueType;
    }

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        if ( ! ( tensor instanceof IndexedTensor))
            throw new RuntimeException("The dense format is only supported for indexed tensors");
        encodeDimensions(buffer, (IndexedTensor)tensor);
        encodeCells(buffer, (IndexedTensor)tensor);
    }

    private void encodeDimensions(GrowableByteBuffer buffer, IndexedTensor tensor) {
        buffer.putInt1_4Bytes(tensor.type().dimensions().size());
        for (int i = 0; i < tensor.type().dimensions().size(); i++) {
            buffer.putUtf8String(tensor.type().dimensions().get(i).name());
            buffer.putInt1_4Bytes((int)tensor.dimensionSizes().size(i)); // XXX: Size truncation
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, IndexedTensor tensor) {
        switch (serializationValueType) {
            case DOUBLE: encodeDoubleCells(tensor, buffer); break;
            case FLOAT: encodeFloatCells(tensor, buffer); break;
            case BFLOAT16: encodeBFloat16Cells(tensor, buffer); break;
            case INT8: encodeInt8Cells(tensor, buffer); break;
        }
    }

    private void encodeDoubleCells(IndexedTensor tensor, GrowableByteBuffer buffer) {
        for (int i = 0; i < tensor.size(); i++)
            buffer.putDouble(tensor.get(i));
    }

    private void encodeFloatCells(IndexedTensor tensor, GrowableByteBuffer buffer) {
        for (int i = 0; i < tensor.size(); i++)
            buffer.putFloat(tensor.getFloat(i));
    }

    private void encodeBFloat16Cells(IndexedTensor tensor, GrowableByteBuffer buffer) {
        for (int i = 0; i < tensor.size(); i++)
            buffer.putShort(TypedBinaryFormat.bFloat16BitsFromFloat(tensor.getFloat(i)));
    }

    private void encodeInt8Cells(IndexedTensor tensor, GrowableByteBuffer buffer) {
        for (int i = 0; i < tensor.size(); i++)
            buffer.put((byte) tensor.getFloat(i));
    }

    @Override
    public Tensor decode(Optional<TensorType> optionalType, GrowableByteBuffer buffer) {
        TensorType type;
        DimensionSizes sizes;
        if (optionalType.isPresent()) {
            type = optionalType.get();
            if (type.valueType() != this.serializationValueType) {
                throw new IllegalArgumentException("Tensor value type mismatch. Value type " + type.valueType() +
                                                   " is not " + this.serializationValueType);
            }
            TensorType serializedType = decodeType(buffer);
            if ( ! serializedType.isAssignableTo(type))
                throw new IllegalArgumentException("Type/instance mismatch: A tensor of type " + serializedType +
                                                   " cannot be assigned to type " + type);
            sizes = sizesFromType(serializedType);
        }
        else {
            type = decodeType(buffer);
            sizes = sizesFromType(type);
        }
        Tensor.Builder builder = Tensor.Builder.of(type, sizes);
        decodeCells(sizes, buffer, (IndexedTensor.BoundBuilder)builder);
        return builder.build();
    }

    private TensorType decodeType(GrowableByteBuffer buffer) {
        TensorType.Builder builder = new TensorType.Builder(serializationValueType);
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

    private void decodeCells(DimensionSizes sizes, GrowableByteBuffer buffer, IndexedTensor.BoundBuilder builder) {
        switch (serializationValueType) {
            case DOUBLE: decodeDoubleCells(sizes, builder, buffer); break;
            case FLOAT: decodeFloatCells(sizes, builder, buffer); break;
            case BFLOAT16: decodeBFloat16Cells(sizes, builder, buffer); break;
            case INT8: decodeInt8Cells(sizes, builder, buffer); break;
        }
    }

    private void decodeDoubleCells(DimensionSizes sizes, IndexedTensor.BoundBuilder builder, GrowableByteBuffer buffer) {
        for (long i = 0; i < sizes.totalSize(); i++)
            builder.cellByDirectIndex(i, buffer.getDouble());
    }

    private void decodeFloatCells(DimensionSizes sizes, IndexedTensor.BoundBuilder builder, GrowableByteBuffer buffer) {
        for (long i = 0; i < sizes.totalSize(); i++)
            builder.cellByDirectIndex(i, buffer.getFloat());
    }

    private void decodeBFloat16Cells(DimensionSizes sizes, IndexedTensor.BoundBuilder builder, GrowableByteBuffer buffer) {
        for (long i = 0; i < sizes.totalSize(); i++) {
            builder.cellByDirectIndex(i, TypedBinaryFormat.floatFromBFloat16Bits(buffer.getShort()));
        }
    }

    private void decodeInt8Cells(DimensionSizes sizes, IndexedTensor.BoundBuilder builder, GrowableByteBuffer buffer) {
        for (long i = 0; i < sizes.totalSize(); i++) {
            builder.cellByDirectIndex(i, (float) buffer.get());
        }
    }

}
