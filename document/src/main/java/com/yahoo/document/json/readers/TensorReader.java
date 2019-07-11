// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.lang.MutableInteger;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Type;
import com.yahoo.tensor.IndexedTensor;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;

import static com.yahoo.document.json.readers.JsonParserHelpers.*;

/**
 * Reads the tensor format described at
 * http://docs.vespa.ai/documentation/reference/document-json-format.html#tensor
 *
 * @author geirst
 * @author bratseth
 */
public class TensorReader {

    public static final String TENSOR_ADDRESS = "address";
    public static final String TENSOR_DIMENSIONS = "dimensions";
    public static final String TENSOR_CELLS = "cells";
    public static final String TENSOR_VALUES = "values";
    public static final String TENSOR_VALUE = "value";

    static void fillTensor(TokenBuffer buffer, TensorFieldValue tensorFieldValue) {
        // TODO: Switch implementation to om.yahoo.tensor.serialization.JsonFormat.decode
        Tensor.Builder builder = Tensor.Builder.of(tensorFieldValue.getDataType().getTensorType());
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            if (TENSOR_CELLS.equals(buffer.currentName()))
                readTensorCells(buffer, builder);
            else if (TENSOR_VALUES.equals(buffer.currentName()))
                readTensorValues(buffer, builder);
            else if (builder.type().dimensions().stream().anyMatch(d -> d.isIndexed())) // sparse can be empty
                throw new IllegalArgumentException("Expected a tensor value to contain either 'cells' or 'values'");
        }
        expectObjectEnd(buffer.currentToken());
        tensorFieldValue.assign(builder.build());
    }

    static void readTensorCells(TokenBuffer buffer, Tensor.Builder builder) {
        expectArrayStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            readTensorCell(buffer, builder);
        expectCompositeEnd(buffer.currentToken());
    }

    private static void readTensorCell(TokenBuffer buffer, Tensor.Builder builder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        double cellValue = 0.0;
        Tensor.Builder.CellBuilder cellBuilder = builder.cell();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String currentName = buffer.currentName();
            if (TensorReader.TENSOR_ADDRESS.equals(currentName)) {
                readTensorAddress(buffer, cellBuilder);
            } else if (TensorReader.TENSOR_VALUE.equals(currentName)) {
                cellValue = readDouble(buffer);
            }
        }
        expectObjectEnd(buffer.currentToken());
        cellBuilder.value(cellValue);
    }

    private static void readTensorAddress(TokenBuffer buffer, MappedTensor.Builder.CellBuilder cellBuilder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String dimension = buffer.currentName();
            String label = buffer.currentText();
            cellBuilder.label(dimension, label);
        }
        expectObjectEnd(buffer.currentToken());
    }

    private static void readTensorValues(TokenBuffer buffer, Tensor.Builder builder) {
        if ( ! (builder instanceof IndexedTensor.BoundBuilder))
            throw new IllegalArgumentException("The 'values' field can only be used with dense tensors. " +
                                               "Use 'cells' instead");
        expectArrayStart(buffer.currentToken());

        IndexedTensor.BoundBuilder indexedBuilder = (IndexedTensor.BoundBuilder)builder;
        int index = 0;
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            indexedBuilder.cellByDirectIndex(index++, readDouble(buffer));
        expectCompositeEnd(buffer.currentToken());
    }

    private static double readDouble(TokenBuffer buffer) {
        try {
            return Double.valueOf(buffer.currentText());
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a number but got '" + buffer.currentText());
        }
    }

}
