// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.Field;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.TensorAddUpdate;
import com.yahoo.tensor.TensorType;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
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
        expectTensorTypeIsSparse(field);

        TensorDataType tensorDataType = (TensorDataType)field.getDataType();
        TensorType tensorType = tensorDataType.getTensorType();
        TensorFieldValue tensorFieldValue = new TensorFieldValue(tensorType);
        fillTensor(buffer, tensorFieldValue);
        return new TensorAddUpdate(tensorFieldValue);
    }

    private static void expectTensorTypeIsSparse(Field field) {
        TensorType tensorType = ((TensorDataType)field.getDataType()).getTensorType();
        if (tensorType.dimensions().stream()
                .anyMatch(dim -> dim.isIndexed())) {
            throw new IllegalArgumentException("An add update can only be applied to sparse tensors. "
                    + "Field '" + field.getName() + "' has unsupported tensor type '" + tensorType + "'");
        }
    }

}
