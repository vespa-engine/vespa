// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.JsonReaderException;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class StructReader {
    public static void fillStruct(TokenBuffer buffer, StructuredFieldValue parent) {
        // do note the order of initializing initNesting and token is relevant for empty docs
        int initNesting = buffer.nesting();
        JsonToken token = buffer.next();

        while (buffer.nesting() >= initNesting) {
            Field f = JsonReader.getField(buffer, parent);
            try {
                FieldValue v = readSingleValue(buffer, token, f.getDataType());
                parent.setFieldValue(f, v);
                token = buffer.next();
            } catch (IllegalArgumentException e) {
                throw new JsonReaderException(f, e);
            }
        }
    }
}
