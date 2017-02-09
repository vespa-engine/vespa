// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.json.TokenBuffer;

import java.util.List;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class ArrayReader {
    static public JsonToken fillArrayUpdate(TokenBuffer buffer, int initNesting, JsonToken initToken, DataType valueType, List<FieldValue> arrayContents) {
        JsonToken token = initToken;
        while (buffer.nesting() >= initNesting) {
            arrayContents.add(readSingleValue(buffer, token, valueType));
            token = buffer.next();
        }
        return token;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void fillArray(TokenBuffer buffer, CollectionFieldValue parent, DataType valueType) {
        int initNesting = buffer.nesting();
        expectArrayStart(buffer.currentToken());
        JsonToken token = buffer.next();
        while (buffer.nesting() >= initNesting) {
            parent.add(readSingleValue(buffer, token, valueType));
            token = buffer.next();
        }
    }
}
