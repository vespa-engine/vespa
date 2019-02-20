// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.document.update.TensorModifyUpdate;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;
import com.yahoo.tensor.TensorType;

import java.util.Iterator;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.TensorModifyUpdateReader.validateBounds;
import static com.yahoo.document.json.readers.TensorReader.fillTensor;

/**
 * Class used to read an add update for a tensor field.
 */
public class TensorAddUpdateReader {

    public static boolean isTensorField(Field field) {
        return field.getDataType() instanceof TensorDataType;
    }

    public static TensorAddUpdate createTensorAddUpdate(TokenBuffer buffer, Field field) {
        expectObjectStart(buffer.currentToken());
        expectTensorTypeHasSparseDimensions(field);

        // Convert update type to sparse
        TensorDataType tensorDataType = (TensorDataType)field.getDataType();
        TensorType originalType = tensorDataType.getTensorType();
        TensorType convertedType = TensorModifyUpdate.convertToCompatibleType(originalType);

        TensorFieldValue tensorFieldValue = new TensorFieldValue(convertedType);
        fillTensor(buffer, tensorFieldValue);
        expectTensorIsNonEmpty(field, tensorFieldValue.getTensor().get());
        validateBounds(tensorFieldValue.getTensor().get(), originalType);

        return new TensorAddUpdate(tensorFieldValue);
    }

    private static void expectTensorTypeHasSparseDimensions(Field field) {
        TensorType tensorType = ((TensorDataType)field.getDataType()).getTensorType();
        if (tensorType.dimensions().stream().allMatch(TensorType.Dimension::isIndexed)) {
            throw new IllegalArgumentException("An add update can only be applied to tensors " +
                    "with at least one sparse dimension. Field '" + field.getName() +
                    "' has unsupported tensor type '" + tensorType + "'");
        }
    }

    private static void expectTensorIsNonEmpty(Field field, Tensor tensor) {
        if (tensor.isEmpty()) {
            throw new IllegalArgumentException("Add update for field '" + field.getName() + "' does not contain tensor cells");
        }
    }

}
