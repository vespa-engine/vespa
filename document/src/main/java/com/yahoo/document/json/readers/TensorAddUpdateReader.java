// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.TensorReader.fillTensor;

/**
 * Reader of an "add" update of a tensor field.
 */
public class TensorAddUpdateReader {

    public static boolean isTensorField(Field field) {
        return field.getDataType() instanceof TensorDataType;
    }

    public static TensorAddUpdate createTensorAddUpdate(TokenBuffer buffer, Field field) {
        expectObjectStart(buffer.current());
        expectTensorTypeHasSparseDimensions(field);

        TensorDataType tensorDataType = (TensorDataType)field.getDataType();
        TensorType tensorType = tensorDataType.getTensorType();
        TensorFieldValue tensorFieldValue = new TensorFieldValue(tensorType);
        fillTensor(buffer, tensorFieldValue);

        expectTensorIsNonEmpty(field, tensorFieldValue.getTensor().get());
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
