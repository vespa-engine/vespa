// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;

/**
 * @author Simon Thoresen Hult
 */
public class UnresolvedFieldValue extends FieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new UnresolvedFieldValue(); }
        @Override public FieldValue create(String value) { throw new UnsupportedOperationException(); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }

    @Override
    public DataType getDataType() {
        return UnresolvedDataType.INSTANCE;
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void assign(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        throw new UnsupportedOperationException();
    }

}
