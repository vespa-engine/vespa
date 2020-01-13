// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.TensorRemoveUpdate;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectCompositeEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;

/**
 * Reader of a "remove" update of a tensor field.
 */
public class TensorRemoveUpdateReader {

    public static final String TENSOR_ADDRESSES = "addresses";

    static TensorRemoveUpdate createTensorRemoveUpdate(TokenBuffer buffer, Field field) {
        expectObjectStart(buffer.currentToken());
        expectTensorTypeHasSparseDimensions(field);

        TensorDataType tensorDataType = (TensorDataType)field.getDataType();
        TensorType originalType = tensorDataType.getTensorType();
        TensorType convertedType = TensorRemoveUpdate.extractSparseDimensions(originalType);
        Tensor tensor = readRemoveUpdateTensor(buffer, convertedType, originalType);

        expectAddressesAreNonEmpty(field, tensor);
        return new TensorRemoveUpdate(new TensorFieldValue(tensor));
    }

    private static void expectTensorTypeHasSparseDimensions(Field field) {
        TensorType tensorType = ((TensorDataType)field.getDataType()).getTensorType();
        if (tensorType.dimensions().stream().allMatch(TensorType.Dimension::isIndexed)) {
            throw new IllegalArgumentException("A remove update can only be applied to tensors " +
                                               "with at least one sparse dimension. Field '" + field.getName() +
                                               "' has unsupported tensor type '" + tensorType + "'");
        }
    }

    private static void expectAddressesAreNonEmpty(Field field, Tensor tensor) {
        if (tensor.isEmpty()) {
            throw new IllegalArgumentException("Remove update for field '" + field.getName() +
                                               "' does not contain tensor addresses");
        }
    }

    /**
     * Reads all addresses in buffer and returns a tensor where addresses have cell value 1.0
     */
    private static Tensor readRemoveUpdateTensor(TokenBuffer buffer, TensorType type, TensorType originalType) {
        Tensor.Builder builder = Tensor.Builder.of(type);
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            if (TENSOR_ADDRESSES.equals(buffer.currentName())) {
                expectArrayStart(buffer.currentToken());
                int nesting = buffer.nesting();
                for (buffer.next(); buffer.nesting() >= nesting; buffer.next()) {
                    builder.cell(readTensorAddress(buffer, type, originalType), 1.0);
                }
                expectCompositeEnd(buffer.currentToken());
            }
        }
        expectObjectEnd(buffer.currentToken());
        return builder.build();
    }

    private static TensorAddress readTensorAddress(TokenBuffer buffer, TensorType type, TensorType originalType) {
        TensorAddress.Builder builder = new TensorAddress.Builder(type);
        expectObjectStart(buffer.currentToken());
        int initNesting = buffer.nesting();
        for (buffer.next(); buffer.nesting() >= initNesting; buffer.next()) {
            String dimension = buffer.currentName();
            if ( type.dimension(dimension).isEmpty() && originalType.dimension(dimension).isPresent()) {
                throw new IllegalArgumentException("Indexed dimension address '" + dimension +
                                                   "' should not be specified in remove update");
            }
            String label = buffer.currentText();
            builder.add(dimension, label);
        }
        expectObjectEnd(buffer.currentToken());
        return builder.build();
    }

}
