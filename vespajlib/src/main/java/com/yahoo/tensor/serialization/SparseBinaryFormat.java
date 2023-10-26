// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of a sparse binary format for a tensor on the form:
 *
 * Sorted dimensions = num_dimensions [dimension_str_len dimension_str_bytes]*
 * Cells = num_cells [label_1_str_len label_1_str_bytes ... label_N_str_len label_N_str_bytes cell_value]*
 *
 * Note that the dimensions are sorted and the tensor address labels are given in the same sorted order.
 * Unspecified labels are encoded as the empty string "".
 *
 * @author geirst
 */
class SparseBinaryFormat implements BinaryFormat {

    private final TensorType.Value serializationValueType;

    SparseBinaryFormat() {
        this(TensorType.Value.DOUBLE);
    }
    SparseBinaryFormat(TensorType.Value serializationValueType) {
        this.serializationValueType = serializationValueType;
    }

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        encodeDimensions(buffer, tensor.type().dimensions());
        encodeCells(buffer, tensor);
    }

    private void encodeDimensions(GrowableByteBuffer buffer, List<TensorType.Dimension> sortedDimensions) {
        buffer.putInt1_4Bytes(sortedDimensions.size());
        for (TensorType.Dimension dimension : sortedDimensions) {
            buffer.putUtf8String(dimension.name());
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, Tensor tensor) {
        buffer.putInt1_4Bytes((int)tensor.size()); // XXX: Size truncation
        switch (serializationValueType) {
            case DOUBLE: encodeCells(buffer, tensor, buffer::putDouble); break;
            case FLOAT: encodeCells(buffer, tensor, (val) -> buffer.putFloat(val.floatValue())); break;
            case BFLOAT16: encodeCells(buffer, tensor, (val) ->
                    buffer.putShort(TypedBinaryFormat.bFloat16BitsFromFloat(val.floatValue()))); break;
            case INT8: encodeCells(buffer, tensor, (val) -> buffer.put((byte)(val.floatValue()))); break;
        }
    }

    private void encodeCells(GrowableByteBuffer buffer, Tensor tensor, Consumer<Double> consumer) {
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Tensor.Cell cell = i.next();
            encodeAddress(buffer, cell.getKey());
            consumer.accept(cell.getValue());
        }
    }

    private void encodeAddress(GrowableByteBuffer buffer, TensorAddress address) {
        for (int i = 0; i < address.size(); i++)
            buffer.putUtf8String(address.label(i));
    }

    @Override
    public Tensor decode(Optional<TensorType> optionalType, GrowableByteBuffer buffer) {
        TensorType type;
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
        }
        else {
            type = decodeType(buffer);
        }
        Tensor.Builder builder = Tensor.Builder.of(type);
        decodeCells(buffer, builder, type);
        return builder.build();
    }

    private TensorType decodeType(GrowableByteBuffer buffer) {
        int numDimensions = buffer.getInt1_4Bytes();
        TensorType.Builder builder = new TensorType.Builder(serializationValueType);
        for (int i = 0; i < numDimensions; ++i)
            builder.mapped(buffer.getUtf8String());
        return builder.build();
    }

    private void decodeCells(GrowableByteBuffer buffer, Tensor.Builder builder, TensorType type) {
        switch (serializationValueType) {
            case DOUBLE: decodeCells(buffer, builder, type, buffer::getDouble); break;
            case FLOAT: decodeCells(buffer, builder, type, () -> (double)buffer.getFloat()); break;
            case BFLOAT16: decodeCells(buffer, builder, type, () ->
                    (double)TypedBinaryFormat.floatFromBFloat16Bits(buffer.getShort())); break;
            case INT8: decodeCells(buffer, builder, type, () -> (double)buffer.get()); break;
        }
    }

    private void decodeCells(GrowableByteBuffer buffer, Tensor.Builder builder, TensorType type, Supplier<Double> supplier) {
        long numCells = buffer.getInt1_4Bytes(); // XXX: Size truncation
        for (long i = 0; i < numCells; ++i) {
            Tensor.Builder.CellBuilder cellBuilder = builder.cell();
            decodeAddress(buffer, cellBuilder, type);
            cellBuilder.value(supplier.get());
        }
    }

    private void decodeAddress(GrowableByteBuffer buffer, Tensor.Builder.CellBuilder builder, TensorType type) {
        for (TensorType.Dimension dimension : type.dimensions()) {
            String label = buffer.getUtf8String();
            if ( ! label.isEmpty()) {
                builder.label(dimension.name(), label);
            }
        }
    }

}
