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

    // TODO populateComposite is extremely similar to add/remove, refactor
    // yes, this suppresswarnings ugliness is by intention, the code relies on the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes" })
    public static void populateComposite(TokenBuffer buffer, FieldValue parent, JsonToken token) {
        if ((token != JsonToken.START_OBJECT) && (token != JsonToken.START_ARRAY)) {
            throw new IllegalArgumentException("Expected '[' or '{'. Got '" + token + "'.");
        }
        if (parent instanceof CollectionFieldValue) {
            DataType valueType = ((CollectionFieldValue) parent).getDataType().getNestedType();
            if (parent instanceof WeightedSet) {
                fillWeightedSet(buffer, valueType, (WeightedSet) parent);
            } else {
                fillArray(buffer, (CollectionFieldValue) parent, valueType);
            }
        } else if (parent instanceof MapFieldValue) {
            MapReader.fillMap(buffer, (MapFieldValue) parent);
        } else if (parent instanceof StructuredFieldValue) {
            StructReader.fillStruct(buffer, (StructuredFieldValue) parent);
        } else if (parent instanceof TensorFieldValue) {
            TensorReader.fillTensor(buffer, (TensorFieldValue) parent);
        } else {
            throw new IllegalStateException("Has created a composite field"
                    + " value the reader does not know how to handle: "
                    + parent.getClass().getName() + " This is a bug. token = " + token);
        }
        expectCompositeEnd(buffer.currentToken());
    }
}
