// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.ValueUpdate;

public class SingleValueReader {
    public static final String UPDATE_ASSIGN = "assign";
    public static final String UPDATE_INCREMENT = "increment";
    public static final String UPDATE_DECREMENT = "decrement";
    public static final String UPDATE_MULTIPLY = "multiply";
    public static final String UPDATE_DIVIDE = "divide";

    public static FieldValue readSingleValue(TokenBuffer buffer, JsonToken t, DataType expectedType) {
        if (t.isScalarValue()) {
            return JsonReader.readAtomic(buffer, expectedType);
        } else {
            return CompositeReader.createComposite(buffer, expectedType);
        }
    }

    @SuppressWarnings("rawtypes")
    public static ValueUpdate readSingleUpdate(TokenBuffer buffer, DataType expectedType, String action) {
        ValueUpdate update;

        switch (action) {
            case UPDATE_ASSIGN:
                update = (buffer.currentToken() == JsonToken.VALUE_NULL)
                        ? ValueUpdate.createClear()
                        : ValueUpdate.createAssign(readSingleValue(buffer, buffer.currentToken(), expectedType));
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

}
