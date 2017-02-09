// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.JsonReader;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.FieldUpdate;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectCompositeEnd;
import static com.yahoo.document.json.readers.WeightedSetReader.fillWeightedSetUpdate;

public class AddRemoveCreator {

    // yes, this suppresswarnings ugliness is by intention, the code relies on
    // the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes", "unchecked" })
    public static void createAddsOrRemoves(TokenBuffer buffer, Field field, FieldUpdate update, JsonReader.FieldOperation op) {
        FieldValue container = field.getDataType().createFieldValue();
        FieldUpdate singleUpdate;
        int initNesting = buffer.nesting();
        JsonToken token;

        Preconditions.checkState(buffer.currentToken().isStructStart(), "Expected start of composite, got %s", buffer.currentToken());
        if (container instanceof CollectionFieldValue) {
            token = buffer.next();
            DataType valueType = ((CollectionFieldValue) container).getDataType().getNestedType();
            if (container instanceof WeightedSet) {
                // these are objects with string keys (which are the nested
                // types) and values which are the weight
                WeightedSet weightedSet = (WeightedSet) container;
                fillWeightedSetUpdate(buffer, initNesting, valueType, weightedSet);
                if (op == JsonReader.FieldOperation.REMOVE) {
                    singleUpdate = FieldUpdate.createRemoveAll(field, weightedSet);
                } else {
                    singleUpdate = FieldUpdate.createAddAll(field, weightedSet);

                }
            } else {
                List<FieldValue> arrayContents = new ArrayList<>();
                token = ArrayReader.fillArrayUpdate(buffer, initNesting, token, valueType, arrayContents);
                if (token != JsonToken.END_ARRAY) {
                    throw new IllegalStateException("Expected END_ARRAY. Got '" + token + "'.");
                }
                if (op == JsonReader.FieldOperation.REMOVE) {
                    singleUpdate = FieldUpdate.createRemoveAll(field, arrayContents);
                } else {
                    singleUpdate = FieldUpdate.createAddAll(field, arrayContents);
                }
            }
        } else {
            throw new UnsupportedOperationException(
                    "Trying to add or remove from a field of a type the reader does not know how to handle: "
                            + container.getClass().getName());
        }
        expectCompositeEnd(buffer.currentToken());
        update.addAll(singleUpdate);
    }
}
