// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            encodeAddress(buffer, cell.getKey());
            buffer.putDouble(cell.getValue());
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
        TensorType.Builder builder = new TensorType.Builder();
        for (int i = 0; i < numDimensions; ++i)
            builder.mapped(buffer.getUtf8String());
        return builder.build();
    }

    private void decodeCells(GrowableByteBuffer buffer, Tensor.Builder builder, TensorType type) {
        long numCells = buffer.getInt1_4Bytes(); // XXX: Size truncation
        for (long i = 0; i < numCells; ++i) {
            Tensor.Builder.CellBuilder cellBuilder = builder.cell();
            decodeAddress(buffer, cellBuilder, type);
            cellBuilder.value(buffer.getDouble());
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
