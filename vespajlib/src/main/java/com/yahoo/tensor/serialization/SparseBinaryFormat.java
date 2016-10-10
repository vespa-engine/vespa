// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.tensor.serialization;

import com.google.common.annotations.Beta;
import com.google.common.collect.Sets;
import com.yahoo.io.GrowableByteBuffer;
import com.yahoo.tensor.MapTensorBuilder;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
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
        List<String> sortedDimensions = new ArrayList<>(tensor.dimensions());
        Collections.sort(sortedDimensions);
        encodeDimensions(buffer, sortedDimensions);
        encodeCells(buffer, tensor.cells(), sortedDimensions);
    }

    private static void encodeDimensions(GrowableByteBuffer buffer, List<String> sortedDimensions) {
        buffer.putInt1_4Bytes(sortedDimensions.size());
        for (String dimension : sortedDimensions) {
            encodeString(buffer, dimension);
        }
    }

    private static void encodeCells(GrowableByteBuffer buffer, Map<TensorAddress, Double> cells,
                                    List<String> sortedDimensions) {
        buffer.putInt1_4Bytes(cells.size());
        for (Map.Entry<TensorAddress, Double> cellEntry : cells.entrySet()) {
            encodeAddress(buffer, cellEntry.getKey(), sortedDimensions);
            buffer.putDouble(cellEntry.getValue().doubleValue());
        }
    }

    private static void encodeAddress(GrowableByteBuffer buffer, TensorAddress address, List<String> sortedDimensions) {
        for (String dimension : sortedDimensions) {
            Optional<TensorAddress.Element> element =
                    address.elements().stream().filter(elem -> elem.dimension().equals(dimension)).findFirst();
            String label = (element.isPresent() ? element.get().label() : "");
            encodeString(buffer, label);
        }
    }

    private static void encodeString(GrowableByteBuffer buffer, String value) {
        byte[] stringBytes = Utf8.toBytes(value);
        buffer.putInt1_4Bytes(stringBytes.length);
        buffer.put(stringBytes);
    }

    @Override
    public Tensor decode(GrowableByteBuffer buffer) {
        List<String> sortedDimensions = decodeDimensions(buffer);
        MapTensorBuilder builder = new MapTensorBuilder();
        for (String dimension : sortedDimensions) {
            builder.dimension(dimension);
        }
        decodeCells(buffer, builder, sortedDimensions);
        return builder.build();
    }

    private static List<String> decodeDimensions(GrowableByteBuffer buffer) {
        int numDimensions = buffer.getInt1_4Bytes();
        List<String> sortedDimensions = new ArrayList<>();
        for (int i = 0; i < numDimensions; ++i) {
            sortedDimensions.add(decodeString(buffer));
        }
        return sortedDimensions;
    }

    private static void decodeCells(GrowableByteBuffer buffer, MapTensorBuilder builder,
                                    List<String> sortedDimensions) {
        int numCells = buffer.getInt1_4Bytes();
        for (int i = 0; i < numCells; ++i) {
            MapTensorBuilder.CellBuilder cellBuilder = builder.cell();
            decodeAddress(buffer, cellBuilder, sortedDimensions);
            cellBuilder.value(buffer.getDouble());
        }
    }

    private static void decodeAddress(GrowableByteBuffer buffer, MapTensorBuilder.CellBuilder builder,
                                      List<String> sortedDimensions) {
        for (String dimension : sortedDimensions) {
            String label = decodeString(buffer);
            if (!label.isEmpty()) {
                builder.label(dimension, label);
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
