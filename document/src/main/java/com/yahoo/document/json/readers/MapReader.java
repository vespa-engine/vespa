// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.google.common.base.Preconditions;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.CollectionFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.json.TokenBuffer;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectArrayStart;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectEnd;
import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;
import static com.yahoo.document.json.readers.SingleValueReader.readAtomic;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleUpdate;
import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class MapReader {

    public static final String MAP_KEY = "key";
    public static final String MAP_VALUE = "value";
    public static final String UPDATE_ELEMENT = "element";
    public static final String UPDATE_MATCH = "match";

    public static void fillMap(TokenBuffer buffer, MapFieldValue parent, boolean ignoreUndefinedFields) {
        if (buffer.current() == JsonToken.START_ARRAY) {
            MapReader.fillMapFromArray(buffer, parent, ignoreUndefinedFields);
        } else {
            MapReader.fillMapFromObject(buffer, parent, ignoreUndefinedFields);
        }
    }

    @SuppressWarnings({ "rawtypes", "cast", "unchecked" })
    public static void fillMapFromArray(TokenBuffer buffer, MapFieldValue parent, boolean ignoreUndefinedFields) {
        JsonToken token = buffer.current();
        int initNesting = buffer.nesting();
        expectArrayStart(token);
        token = buffer.next();
        DataType keyType = parent.getDataType().getKeyType();
        DataType valueType = parent.getDataType().getValueType();
        while (buffer.nesting() >= initNesting) {
            FieldValue key = null;
            FieldValue value = null;
            expectObjectStart(token);
            token = buffer.next();
            for (int i = 0; i < 2; ++i) {
                if (MAP_KEY.equals(buffer.currentName())) {
                    key = readSingleValue(buffer, keyType, ignoreUndefinedFields);
                } else if (MAP_VALUE.equals(buffer.currentName())) {
                    value = readSingleValue(buffer, valueType, ignoreUndefinedFields);
                }
                token = buffer.next();
            }
            Preconditions.checkState(key != null && value != null, "Missing key or value for map entry.");
            parent.put(key, value);

            expectObjectEnd(token);
            token = buffer.next(); // array end or next entry
        }
    }

    @SuppressWarnings({ "rawtypes", "cast", "unchecked" })
    public static void fillMapFromObject(TokenBuffer buffer, MapFieldValue parent, boolean ignoreUndefinedFields) {
        JsonToken token = buffer.current();
        int initNesting = buffer.nesting();
        expectObjectStart(token);
        token = buffer.next();
        DataType keyType = parent.getDataType().getKeyType();
        DataType valueType = parent.getDataType().getValueType();
        while (buffer.nesting() >= initNesting) {
            FieldValue key = readAtomic(buffer.currentName(), keyType);
            FieldValue value = readSingleValue(buffer, valueType, ignoreUndefinedFields);

            Preconditions.checkState(key != null && value != null, "Missing key or value for map entry.");
            parent.put(key, value);
            token = buffer.next();
        }
        expectObjectEnd(token);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ValueUpdate createMapUpdate(TokenBuffer buffer,
                                              DataType currentLevel,
                                              boolean ignoreUndefinedFields) {
        if ( ! JsonToken.START_OBJECT.equals(buffer.current()))
            throw new IllegalArgumentException("Expected object for match update, got " + buffer.current());
        buffer.next();

        FieldValue key = null;
        ValueUpdate update;
        boolean elementFirst = UPDATE_ELEMENT.equals(buffer.currentName());
        if (elementFirst) {
            key = keyTypeForMapUpdate(buffer.currentText(), currentLevel);
            buffer.next();
        }

        update = UPDATE_MATCH.equals(buffer.currentName()) ? createMapUpdate(buffer, valueTypeForMapUpdate(currentLevel), ignoreUndefinedFields)
                                                           : readSingleUpdate(buffer, valueTypeForMapUpdate(currentLevel), buffer.currentName(), ignoreUndefinedFields);
        buffer.next();

        if ( ! elementFirst) {
            key = keyTypeForMapUpdate(buffer.currentText(), currentLevel);
            buffer.next();
        }

        if ( ! JsonToken.END_OBJECT.equals(buffer.current()))
            throw new IllegalArgumentException("Expected object end for match update, got " + buffer.current());

        return ValueUpdate.createMap(key, update);
    }

    @SuppressWarnings("rawtypes")
    public static ValueUpdate createMapUpdate(TokenBuffer buffer, Field field, boolean ignoreUndefinedFields) {
        return MapReader.createMapUpdate(buffer, field.getDataType(), ignoreUndefinedFields);
    }

    private static DataType valueTypeForMapUpdate(DataType parentType) {
        if (parentType instanceof WeightedSetDataType) {
            return DataType.INT;
        } else if (parentType instanceof CollectionDataType) {
            return ((CollectionDataType) parentType).getNestedType();
        } else if (parentType instanceof MapDataType) {
            return ((MapDataType) parentType).getValueType();
        } else {
            throw new UnsupportedOperationException("Unexpected parent type: " + parentType);
        }
    }

    private static FieldValue keyTypeForMapUpdate(String elementText, DataType expectedType) {
        FieldValue v;
        if (expectedType instanceof ArrayDataType) {
            v = new IntegerFieldValue(Integer.valueOf(elementText));
        } else if (expectedType instanceof WeightedSetDataType) {
            v = ((WeightedSetDataType) expectedType).getNestedType().createFieldValue(elementText);
        } else if (expectedType instanceof MapDataType) {
            v = ((MapDataType) expectedType).getKeyType().createFieldValue(elementText);
        } else {
            throw new IllegalArgumentException("Container type " + expectedType + " not supported for match update.");
        }
        return v;
    }
}
