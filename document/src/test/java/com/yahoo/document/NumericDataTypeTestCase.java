// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class NumericDataTypeTestCase {

    @Test
    public void basic() {
        NumericDataType type = new NumericDataType("foo", 0, FieldValue.class, IntegerFieldValue.getFactory());
        NumericDataType clonedType = type.clone();
        assertEquals(type,clonedType);
        assertNotSame(type, clonedType);
    }

    @Test
    public void create() {
        try {
            new NumericDataType("foo", 0, IntegerFieldValue.class, IntegerFieldValue.getFactory()).createFieldValue(new Object());
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Class class java.lang.Object not applicable to an class " +
                         "com.yahoo.document.datatypes.IntegerFieldValue instance.", e.getMessage());
        }
    }
}
