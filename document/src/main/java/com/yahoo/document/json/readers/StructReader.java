// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.json.JsonReaderException;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class StructReader {
    public static void fillStruct(TokenBuffer buffer, StructuredFieldValue parent) {
        // do note the order of initializing initNesting and token is relevant for empty docs
        int initNesting = buffer.nesting();
        buffer.next();

        while (buffer.nesting() >= initNesting) {
            Field f = getField(buffer, parent);
            try {
                // skip fields set to null
                if (buffer.currentToken() != JsonToken.VALUE_NULL) {
                    FieldValue v = readSingleValue(buffer, f.getDataType());
                    parent.setFieldValue(f, v);
                }
                buffer.next();
            } catch (IllegalArgumentException e) {
                throw new JsonReaderException(f, e);
            }
        }
    }

    public static Field getField(TokenBuffer buffer, StructuredFieldValue parent) {
        Field f = parent.getField(buffer.currentName());
        if (f == null) {
            throw new NullPointerException("Could not get field \"" + buffer.currentName() +
                    "\" in the structure of type \"" + parent.getDataType().getDataTypeName() + "\".");
        }
        return f;
    }

}
