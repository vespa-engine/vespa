// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MixedTensor;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import static com.yahoo.document.json.readers.JsonParserHelpers.*;

/**
 * Reads the tensor format defined at
 * See <a href="https://docs.vespa.ai/en/reference/document-json-format.html">https://docs.vespa.ai/en/reference/document-json-format.html</a>
 *
 * @author geirst
 * @author bratseth
 */
public class TensorReader {

    public static final String TENSOR_ADDRESS = "address";
    public static final String TENSOR_DIMENSIONS = "dimensions";
    public static final String TENSOR_CELLS = "cells";
    public static final String TENSOR_VALUES = "values";
    public static final String TENSOR_BLOCKS = "blocks";
    public static final String TENSOR_VALUE = "value";

    // MUST be kept in sync with com.yahoo.tensor.serialization.JsonFormat.decode in vespajlib
    static void fillTensor(TokenBuffer buffer, TensorFieldValue tensorFieldValue) {
        Tensor.Builder builder = Tensor.Builder.of(tensorFieldValue.getDataType().getTensorType());
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            if (TENSOR_CELLS.equals(buffer.currentName()))
                readTensorCells(buffer, builder);
            else if (TENSOR_VALUES.equals(buffer.currentName()))
                readTensorValues(buffer, builder);
            else if (TENSOR_BLOCKS.equals(buffer.currentName()))
                readTensorBlocks(buffer, builder);
            else if (builder.type().dimensions().stream().anyMatch(d -> d.isIndexed())) // sparse can be empty
                throw new IllegalArgumentException("Expected a tensor value to contain either 'cells', 'values' or 'blocks'");
        }
        expectObjectEnd(buffer.currentToken());
        tensorFieldValue.assign(builder.build());
    }

    static void readTensorCells(TokenBuffer buffer, Tensor.Builder builder) {
        if (buffer.currentToken() == JsonToken.START_ARRAY) {
            int initNesting = buffer.nesting();
            for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
                readTensorCell(buffer, builder);
        }
        else if (buffer.currentToken() == JsonToken.START_OBJECT) { // single dimension short form
            int initNesting = buffer.nesting();
            for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
                builder.cell(asAddress(buffer.currentName(), builder.type()), readDouble(buffer));
        }
        else {
            throw new IllegalArgumentException("Expected 'cells' to contain an array or an object, but got " + buffer.currentToken());
        }
        expectCompositeEnd(buffer.currentToken());
    }

    private static void readTensorCell(TokenBuffer buffer, Tensor.Builder builder) {
        expectObjectStart(buffer.currentToken());

        TensorAddress address = null;
        Double value = null;
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String currentName = buffer.currentName();
            if (TensorReader.TENSOR_ADDRESS.equals(currentName)) {
                address = readAddress(buffer, builder.type());
            } else if (TensorReader.TENSOR_VALUE.equals(currentName)) {
                value = readDouble(buffer);
            }
        }
        expectObjectEnd(buffer.currentToken());
        if (address == null)
            throw new IllegalArgumentException("Expected an object in a tensor 'cells' array to contain an 'address' field");
        if (value == null)
            throw new IllegalArgumentException("Expected an object in a tensor 'cells' array to contain a 'value' field");
        builder.cell(address, value);
    }

    private static void readTensorValues(TokenBuffer buffer, Tensor.Builder builder) {
        if ( ! (builder instanceof IndexedTensor.BoundBuilder))
            throw new IllegalArgumentException("The 'values' field can only be used with dense tensors. " +
                                               "Use 'cells' or 'blocks' instead");
        IndexedTensor.BoundBuilder indexedBuilder = (IndexedTensor.BoundBuilder)builder;
        int index = 0;
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            indexedBuilder.cellByDirectIndex(index++, readDouble(buffer));
        expectCompositeEnd(buffer.currentToken());
    }

    static void readTensorBlocks(TokenBuffer buffer, Tensor.Builder builder) {
        if ( ! (builder instanceof MixedTensor.BoundBuilder))
            throw new IllegalArgumentException("The 'blocks' field can only be used with mixed tensors with bound dimensions. " +
                                               "Use 'cells' or 'values' instead");

        MixedTensor.BoundBuilder mixedBuilder = (MixedTensor.BoundBuilder) builder;
        if (buffer.currentToken() == JsonToken.START_ARRAY) {
            int initNesting = buffer.nesting();
            for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
                readTensorBlock(buffer, mixedBuilder);
        }
        else if (buffer.currentToken() == JsonToken.START_OBJECT) {
            int initNesting = buffer.nesting();
            for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
                TensorAddress mappedAddress = asAddress(buffer.currentName(), builder.type().mappedSubtype());
                mixedBuilder.block(mappedAddress,
                                   readValues(buffer, (int) mixedBuilder.denseSubspaceSize(), mappedAddress, mixedBuilder.type()));
            }
        }
        else {
            throw new IllegalArgumentException("Expected 'blocks' to contain an array or an object, but got " +
                                               buffer.currentToken());
        }

        expectCompositeEnd(buffer.currentToken());
    }

    private static void readTensorBlock(TokenBuffer buffer, MixedTensor.BoundBuilder mixedBuilder) {
        expectObjectStart(buffer.currentToken());

        TensorAddress address = null;
        double[] values = null;

        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String currentName = buffer.currentName();
            if (TensorReader.TENSOR_ADDRESS.equals(currentName))
                address = readAddress(buffer, mixedBuilder.type().mappedSubtype());
            else if (TensorReader.TENSOR_VALUES.equals(currentName))
                values = readValues(buffer, (int)mixedBuilder.denseSubspaceSize(), address, mixedBuilder.type());
        }
        expectObjectEnd(buffer.currentToken());
        if (address == null)
            throw new IllegalArgumentException("Expected a 'blocks' array object to contain an object 'address'");
        if (values == null)
            throw new IllegalArgumentException("Expected a 'blocks' array object to contain an array 'values'");
        mixedBuilder.block(address, values);
    }

    private static TensorAddress readAddress(TokenBuffer buffer, TensorType type) {
        expectObjectStart(buffer.currentToken());
        TensorAddress.Builder builder = new TensorAddress.Builder(type);
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            builder.add(buffer.currentName(), buffer.currentText());
        expectObjectEnd(buffer.currentToken());
        return builder.build();
    }

    /**
     * Reads values for a tensor subspace block
     *
     * @param buffer the buffer containing the values
     * @param size the expected number of values
     * @param address the address for the block for error reporting, or null if not known
     * @param type the type of the tensor we are reading
     * @return the values read
     */
    private static double[] readValues(TokenBuffer buffer, int size, TensorAddress address, TensorType type) {
        expectArrayStart(buffer.currentToken());

        int index = 0;
        int initNesting = buffer.nesting();
        double[] values = new double[size];
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            values[index++] = readDouble(buffer);
        if (index != size)
            throw new IllegalArgumentException((address != null ? "At " + address.toString(type) + ": " : "") +
                                               "Expected " + size + " values, but got " + index);
        expectCompositeEnd(buffer.currentToken());
        return values;
    }

    private static double readDouble(TokenBuffer buffer) {
        try {
            return Double.parseDouble(buffer.currentText());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but got '" + buffer.currentText());
        }
    }

    private static TensorAddress asAddress(String label, TensorType type) {
        if (type.dimensions().size() != 1)
            throw new IllegalArgumentException("Expected a tensor with a single dimension but got " + type);
        return new TensorAddress.Builder(type).add(type.dimensions().get(0).name(), label).build();
    }

}
