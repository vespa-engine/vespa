// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.net.Url;

import java.net.URI;

/**
 * @author Magnar Nedland
 */
public class UriFieldValue extends StringFieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new UriFieldValue(); }
        @Override public FieldValue create(String value) { return new UriFieldValue(value); }
    }
    public static Factory getFactory() { return new Factory(); }

    public UriFieldValue() { super(); }

    public UriFieldValue(String value) {
        super(value);
        Url.fromString(value);  // Throws if value is invalid.
    }

    @Override
    public void assign(Object obj) {
        if (obj instanceof URI) {
            obj = obj.toString();
        }
        super.assign(obj);
    }

    @Override
    public DataType getDataType() {
        return DataType.URI;
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        super.deserialize(field, reader);
        Url.fromString(toString());  // Throws if value is invalid.
    }

}
