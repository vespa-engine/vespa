// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentId;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.ValueUpdate;
import org.apache.commons.codec.binary.Base64;

public class SingleValueReader {
    public static final String UPDATE_ASSIGN = "assign";
    public static final String UPDATE_INCREMENT = "increment";
    public static final String UPDATE_DECREMENT = "decrement";
    public static final String UPDATE_MULTIPLY = "multiply";
    public static final String UPDATE_DIVIDE = "divide";

    public static FieldValue readSingleValue(TokenBuffer buffer, DataType expectedType) {
        if (buffer.currentToken().isScalarValue()) {
            return readAtomic(buffer.currentText(), expectedType);
        } else {
            FieldValue fieldValue = expectedType.createFieldValue();
            CompositeReader.populateComposite(buffer, fieldValue);
            return fieldValue;
        }
    }

    @SuppressWarnings("rawtypes")
    public static ValueUpdate readSingleUpdate(TokenBuffer buffer, DataType expectedType, String action) {
        ValueUpdate update;

        switch (action) {
            case UPDATE_ASSIGN:
                update = (buffer.currentToken() == JsonToken.VALUE_NULL)
                        ? ValueUpdate.createClear()
                        : ValueUpdate.createAssign(readSingleValue(buffer, expectedType));
                break;
            // double is silly, but it's what is used internally anyway
            case UPDATE_INCREMENT:
                update = ValueUpdate.createIncrement(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_DECREMENT:
                update = ValueUpdate.createDecrement(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_MULTIPLY:
                update = ValueUpdate.createMultiply(Double.valueOf(buffer.currentText()));
                break;
            case UPDATE_DIVIDE:
                update = ValueUpdate.createDivide(Double.valueOf(buffer.currentText()));
                break;
            default:
                throw new IllegalArgumentException("Operation \"" + buffer.currentName() + "\" not implemented.");
        }
        return update;
    }

    public static FieldValue readAtomic(String field, DataType expectedType) {
        if (expectedType.equals(DataType.RAW)) {
            return expectedType.createFieldValue(new Base64().decode(field));
        } else if (expectedType.equals(PositionDataType.INSTANCE)) {
            return PositionDataType.fromString(field);
        } else if (expectedType instanceof ReferenceDataType) {
            return readReferenceFieldValue(field, expectedType);
        } else {
            return expectedType.createFieldValue(field);
        }
    }

    private static FieldValue readReferenceFieldValue(final String refText, DataType expectedType) {
        final FieldValue value = expectedType.createFieldValue();
        if (!refText.isEmpty()) {
            value.assign(new DocumentId(refText));
        }
        return value;
    }
}
