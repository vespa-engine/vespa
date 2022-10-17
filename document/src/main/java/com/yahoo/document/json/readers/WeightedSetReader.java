// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.JsonParserHelpers.expectObjectStart;


public class WeightedSetReader {

    public static void fillWeightedSet(TokenBuffer buffer, DataType valueType, @SuppressWarnings("rawtypes") WeightedSet weightedSet) {
        int initNesting = buffer.nesting();
        expectObjectStart(buffer.currentToken());
        buffer.next();
        iterateThroughWeightedSet(buffer, initNesting, valueType, weightedSet);
    }

    public static void fillWeightedSetUpdate(TokenBuffer buffer, int initNesting, DataType valueType, @SuppressWarnings("rawtypes") WeightedSet weightedSet) {
        iterateThroughWeightedSet(buffer, initNesting, valueType, weightedSet);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void iterateThroughWeightedSet(TokenBuffer buffer, int initNesting, DataType valueType, WeightedSet weightedSet) {
        while (buffer.nesting() >= initNesting) {
            // XXX the keys are defined in the spec to always be represented as strings
            FieldValue v = valueType.createFieldValue(buffer.currentName());
            weightedSet.put(v, Integer.valueOf(buffer.currentText()));
            buffer.next();
        }
    }

}
