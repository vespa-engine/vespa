// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.TokenBuffer;

import java.util.List;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class ArrayReader {

    public static void fillArrayUpdate(TokenBuffer buffer, int initNesting, DataType valueType,
                                       List<FieldValue> arrayContents, boolean ignoreUndefinedFields) {
        while (buffer.nesting() >= initNesting) {
            Preconditions.checkArgument(buffer.current() != JsonToken.VALUE_NULL, "Illegal null value for array entry");
            arrayContents.add(readSingleValue(buffer, valueType, ignoreUndefinedFields));
            buffer.next();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void fillArray(TokenBuffer buffer, CollectionFieldValue parent, DataType valueType, boolean ignoreUndefinedFields) {
        int initNesting = buffer.nesting();
        expectArrayStart(buffer.current());
        buffer.next();
        while (buffer.nesting() >= initNesting) {
            Preconditions.checkArgument(buffer.current() != JsonToken.VALUE_NULL, "Illegal null value for array entry");
            parent.add(readSingleValue(buffer, valueType, ignoreUndefinedFields));
            buffer.next();
        }
    }

}
