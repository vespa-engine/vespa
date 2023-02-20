// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.json.readers;

import com.fasterxml.jackson.core.JsonToken;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.document.json.JsonReaderException;
import com.yahoo.document.json.TokenBuffer;

import static com.yahoo.document.json.readers.SingleValueReader.readSingleValue;

public class StructReader {

    /**
     * Fills this struct.
     *
     * @return true if all this was applied and false if it was ignored because the field does not exist
     */
    public static boolean fillStruct(TokenBuffer buffer, StructuredFieldValue parent, boolean ignoreUndefinedFields) {
        // do note the order of initializing initNesting and token is relevant for empty docs
        int initialNesting = buffer.nesting();
        buffer.next();

        boolean fullyApplied = true;
        while (buffer.nesting() >= initialNesting) {
            Field field = parent.getField(buffer.currentName());
            if (field == null) {
                if (! ignoreUndefinedFields)
                    throw new IllegalArgumentException("No field '" + buffer.currentName() + "' in the structure of type '" +
                                                       parent.getDataType().getDataTypeName() +
                                                       "', which has the fields: " + parent.getDataType().getFields());
                buffer.skipToRelativeNesting(1);
                fullyApplied = false;
                continue;
            }

            try {
                if (buffer.current() != JsonToken.VALUE_NULL) {
                    FieldValue v = readSingleValue(buffer, field.getDataType(), ignoreUndefinedFields);
                    parent.setFieldValue(field, v);
                }
                buffer.next();
            } catch (IllegalArgumentException e) {
                throw new JsonReaderException(field, e);
            }
        }
        return fullyApplied;
    }

}
