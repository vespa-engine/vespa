// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.google.common.annotations.Beta;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;
import com.yahoo.text.Utf8;

import java.util.*;

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
@Beta
class SparseBinaryFormat implements BinaryFormat {

    @Override
    public void encode(GrowableByteBuffer buffer, Tensor tensor) {
        encodeDimensions(buffer, tensor.type().dimensions());
        encodeCells(buffer, tensor);
    }

    private static void encodeDimensions(GrowableByteBuffer buffer, List<TensorType.Dimension> sortedDimensions) {
        buffer.putInt1_4Bytes(sortedDimensions.size());
        for (TensorType.Dimension dimension : sortedDimensions) {
            encodeString(buffer, dimension.name());
        }
    }

    private static void encodeCells(GrowableByteBuffer buffer, Tensor tensor) {
        buffer.putInt1_4Bytes(tensor.size());
        for (Iterator<Tensor.Cell> i = tensor.cellIterator(); i.hasNext(); ) {
            Map.Entry<TensorAddress, Double> cell = i.next();
            encodeAddress(buffer, cell.getKey());
            buffer.putDouble(cell.getValue());
        }
    }

    private static void encodeAddress(GrowableByteBuffer buffer, TensorAddress address) {
        for (int i = 0; i < address.size(); i++)
            encodeString(buffer, address.label(i));
    }

    private static void encodeString(GrowableByteBuffer buffer, String value) {
        byte[] stringBytes = Utf8.toBytes(value);
        buffer.putInt1_4Bytes(stringBytes.length);
        buffer.put(stringBytes);
    }

    @Override
    public Tensor decode(GrowableByteBuffer buffer) {
        TensorType type = decodeDimensions(buffer);
        Tensor.Builder builder = Tensor.Builder.of(type);
        decodeCells(buffer, builder, type);
        return builder.build();
    }

    private static TensorType decodeDimensions(GrowableByteBuffer buffer) {
        TensorType.Builder builder = new TensorType.Builder();
        int numDimensions = buffer.getInt1_4Bytes();
        for (int i = 0; i < numDimensions; ++i) {
            builder.mapped(decodeString(buffer)); // TODO: Support indexed
        }
        return builder.build();
    }

    private static void decodeCells(GrowableByteBuffer buffer, Tensor.Builder builder, TensorType type) {
        int numCells = buffer.getInt1_4Bytes();
        for (int i = 0; i < numCells; ++i) {
            Tensor.Builder.CellBuilder cellBuilder = builder.cell();
            decodeAddress(buffer, cellBuilder, type);
            cellBuilder.value(buffer.getDouble());
        }
    }

    private static void decodeAddress(GrowableByteBuffer buffer, Tensor.Builder.CellBuilder builder, TensorType type) {
        for (TensorType.Dimension dimension : type.dimensions()) {
            String label = decodeString(buffer);
            if ( ! label.isEmpty()) {
                builder.label(dimension.name(), label);
            }
        }
    }

    private static String decodeString(GrowableByteBuffer buffer) {
        int stringLength = buffer.getInt1_4Bytes();
        byte[] stringBytes = new byte[stringLength];
        buffer.get(stringBytes);
        return Utf8.toString(stringBytes);
    }

}
