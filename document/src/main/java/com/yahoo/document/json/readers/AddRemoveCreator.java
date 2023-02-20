// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.FieldUpdate;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectCompositeEnd;
import static com.yahoo.document.json.readers.WeightedSetReader.fillWeightedSetUpdate;

public class AddRemoveCreator {

    // yes, this suppresswarnings ugliness is by intention, the code relies on
    // the contracts in the builders
    @SuppressWarnings("cast")
    public static void createAdds(TokenBuffer buffer, Field field, FieldUpdate update, boolean ignoreUndefinedFields) {
        createAddsOrRemoves(buffer, field, update, false, ignoreUndefinedFields);
    }

    // yes, this suppresswarnings ugliness is by intention, the code relies on
    // the contracts in the builders
    @SuppressWarnings("cast")
    public static void createRemoves(TokenBuffer buffer, Field field, FieldUpdate update, boolean ignoreUndefinedFields) {
        createAddsOrRemoves(buffer, field, update, true, ignoreUndefinedFields);
    }

    // yes, this suppresswarnings ugliness is by intention, the code relies on
    // the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes", "unchecked" })
    private static void createAddsOrRemoves(TokenBuffer buffer, Field field, FieldUpdate update, boolean isRemove, boolean ignoreUndefinedFields) {
        FieldValue container = field.getDataType().createFieldValue();
        FieldUpdate singleUpdate;
        int initNesting = buffer.nesting();

        Preconditions.checkState(buffer.current().isStructStart(), "Expected start of composite, got %s", buffer.current());
        if (container instanceof CollectionFieldValue) {
            buffer.next();
            DataType valueType = ((CollectionFieldValue) container).getDataType().getNestedType();
            if (container instanceof WeightedSet weightedSet) {
                // these are objects with string keys (which are the nested
                // types) and values which are the weight
                fillWeightedSetUpdate(buffer, initNesting, valueType, weightedSet);
                if (isRemove) {
                    singleUpdate = FieldUpdate.createRemoveAll(field, weightedSet);
                } else {
                    singleUpdate = FieldUpdate.createAddAll(field, weightedSet);

                }
            } else {
                List<FieldValue> arrayContents = new ArrayList<>();
                ArrayReader.fillArrayUpdate(buffer, initNesting, valueType, arrayContents, ignoreUndefinedFields);
                if (buffer.current() != JsonToken.END_ARRAY) {
                    throw new IllegalArgumentException("Expected END_ARRAY. Got '" + buffer.current() + "'.");
                }
                if (isRemove) {
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
        expectCompositeEnd(buffer.current());
        update.addAll(singleUpdate);
    }
}
