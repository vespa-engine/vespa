// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.tensor.MappedTensor;
import com.yahoo.tensor.Tensor;

import static com.yahoo.document.json.readers.JsonParserHelpers.*;

/**
 * Reads the tensor format described at
 * http://docs.vespa.ai/documentation/reference/document-json-put-format.html#tensor
 */
public class TensorReader {

    public static final String TENSOR_ADDRESS = "address";
    public static final String TENSOR_DIMENSIONS = "dimensions";
    public static final String TENSOR_CELLS = "cells";
    public static final String TENSOR_VALUE = "value";

    public static void fillTensor(TokenBuffer buffer, TensorFieldValue tensorFieldValue) {
        Tensor.Builder tensorBuilder = Tensor.Builder.of(tensorFieldValue.getDataType().getTensorType());
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        // read tensor cell fields and ignore everything else
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            if (TensorReader.TENSOR_CELLS.equals(buffer.currentName()))
                readTensorCells(buffer, tensorBuilder);
        }
        expectObjectEnd(buffer.currentToken());
        tensorFieldValue.assign(tensorBuilder.build());
    }

    public static void readTensorCells(TokenBuffer buffer, Tensor.Builder tensorBuilder) {
        expectArrayStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next())
            readTensorCell(buffer, tensorBuilder);
        expectCompositeEnd(buffer.currentToken());
    }

    public static void readTensorCell(TokenBuffer buffer, Tensor.Builder tensorBuilder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        double cellValue = 0.0;
        Tensor.Builder.CellBuilder cellBuilder = tensorBuilder.cell();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String currentName = buffer.currentName();
            if (TensorReader.TENSOR_ADDRESS.equals(currentName)) {
                readTensorAddress(buffer, cellBuilder);
            } else if (TensorReader.TENSOR_VALUE.equals(currentName)) {
                cellValue = Double.valueOf(buffer.currentText());
            }
        }
        expectObjectEnd(buffer.currentToken());
        cellBuilder.value(cellValue);
    }

    public static void readTensorAddress(TokenBuffer buffer, MappedTensor.Builder.CellBuilder cellBuilder) {
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String dimension = buffer.currentName();
            String label = buffer.currentText();
            cellBuilder.label(dimension, label);
        }
        expectObjectEnd(buffer.currentToken());
    }
}
