// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.DataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.ArrayReader.fillArray;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectCompositeEnd;
import static com.yahoo.document.json.readers.WeightedSetReader.fillWeightedSet;

public class CompositeReader {

    public static boolean populateComposite(TokenBuffer buffer, FieldValue fieldValue, boolean ignoreUndefinedFields) {
        boolean fullyApplied = populateComposite(buffer.currentToken(), buffer, fieldValue, ignoreUndefinedFields);
        expectCompositeEnd(buffer.currentToken());
        return fullyApplied;
    }

    // TODO: createComposite is extremely similar to add/remove, refactor
    // yes, this suppresswarnings ugliness is by intention, the code relies on the contracts in the builders
    @SuppressWarnings({ "cast", "rawtypes" })
    private static boolean populateComposite(JsonToken token, TokenBuffer buffer, FieldValue fieldValue,
                                             boolean ignoreUndefinedFields) {
        if ((token != JsonToken.START_OBJECT) && (token != JsonToken.START_ARRAY)) {
            throw new IllegalArgumentException("Expected '[' or '{'. Got '" + token + "'.");
        }

        boolean fullyApplied = true;
        if (fieldValue instanceof CollectionFieldValue) {
            DataType valueType = ((CollectionFieldValue) fieldValue).getDataType().getNestedType();
            if (fieldValue instanceof WeightedSet) {
                fillWeightedSet(buffer, valueType, (WeightedSet) fieldValue);
            } else {
                fillArray(buffer, (CollectionFieldValue) fieldValue, valueType, ignoreUndefinedFields);
            }
        } else if (fieldValue instanceof MapFieldValue) {
            MapReader.fillMap(buffer, (MapFieldValue) fieldValue, ignoreUndefinedFields);
        } else if (PositionDataType.INSTANCE.equals(fieldValue.getDataType())) {
            GeoPositionReader.fillGeoPosition(buffer, fieldValue);
        } else if (fieldValue instanceof StructuredFieldValue) {
            fullyApplied = StructReader.fillStruct(buffer, (StructuredFieldValue) fieldValue, ignoreUndefinedFields);
        } else if (fieldValue instanceof TensorFieldValue) {
            TensorReader.fillTensor(buffer, (TensorFieldValue) fieldValue);
        } else {
            throw new IllegalArgumentException("Expected a " + fieldValue.getClass().getName() + " but got an " +
                                               (token == JsonToken.START_OBJECT ? "object" : "array" ));
        }
        return fullyApplied;
    }

}
