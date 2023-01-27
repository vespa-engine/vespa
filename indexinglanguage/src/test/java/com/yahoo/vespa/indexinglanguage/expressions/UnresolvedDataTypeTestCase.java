// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlStream;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class UnresolvedDataTypeTestCase {

    @Test
    public void requireThatFieldValueIsUnresolved() {
        assertEquals(UnresolvedFieldValue.class, UnresolvedDataType.INSTANCE.createFieldValue().getClass());
    }

    @Test
    public void requireThatTypeIsCompatibleWithAnything() {
        assertFalse(UnresolvedDataType.INSTANCE.isValueCompatible(null));
        assertTrue(UnresolvedDataType.INSTANCE.isValueCompatible(new MyFieldValue()));
    }

    private static class MyFieldValue extends FieldValue {

        @Override
        public DataType getDataType() {
            return null;
        }

        @Override
        @Deprecated
        public void printXml(XmlStream xml) {

        }

        @Override
        public void clear() {

        }

        @Override
        public void assign(Object o) {

        }

        @Override
        public void serialize(Field field, FieldWriter writer) {

        }

        @Override
        public void deserialize(Field field, FieldReader reader) {

        }
    }
}
