// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.JsonReader.expectCompositeEnd;
import static com.yahoo.document.json.readers.ArrayReader.fillArray;
import static com.yahoo.document.json.readers.WeightedSetReader.fillWeightedSet;

public class CompositeReader {

    // TODO createComposite is extremely similar to add/remove, refactor
    // yes, this suppresswarnings ugliness is by intention, the code relies on the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes" })
    public static FieldValue createComposite(TokenBuffer buffer, DataType expectedType) {
        FieldValue fieldValue = expectedType.createFieldValue();
        populateComposite(buffer, fieldValue);
        return fieldValue;
    }

    // TODO createComposite is extremely similar to add/remove, refactor
    // yes, this suppresswarnings ugliness is by intention, the code relies on the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes" })
    public static void populateComposite(TokenBuffer buffer, FieldValue fieldValue) {
        JsonToken token = buffer.currentToken();
        if ((token != JsonToken.START_OBJECT) && (token != JsonToken.START_ARRAY)) {
            throw new IllegalArgumentException("Expected '[' or '{'. Got '" + token + "'.");
        }
        if (fieldValue instanceof CollectionFieldValue) {
            DataType valueType = ((CollectionFieldValue) fieldValue).getDataType().getNestedType();
            if (fieldValue instanceof WeightedSet) {
                fillWeightedSet(buffer, valueType, (WeightedSet) fieldValue);
            } else {
                fillArray(buffer, (CollectionFieldValue) fieldValue, valueType);
            }
        } else if (fieldValue instanceof MapFieldValue) {
            MapReader.fillMap(buffer, (MapFieldValue) fieldValue);
        } else if (fieldValue instanceof StructuredFieldValue) {
            StructReader.fillStruct(buffer, (StructuredFieldValue) fieldValue);
        } else if (fieldValue instanceof TensorFieldValue) {
            TensorReader.fillTensor(buffer, (TensorFieldValue) fieldValue);
        } else {
            throw new IllegalStateException("Has created a composite field"
                    + " value the reader does not know how to handle: "
                    + fieldValue.getClass().getName() + " This is a bug. token = " + token);
        }
        expectCompositeEnd(buffer.currentToken());
    }



}
