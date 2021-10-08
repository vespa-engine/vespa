// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/**
 * @author Einar M R Rosenvinge
 */
public class TemporaryDataTypeTestCase {

    @Test
    public void requireNulls() {
        TemporaryDataType type = new TemporaryDataType(0, "");
        assertNull(type.createFieldValue(new Object()));
        assertNull(type.createFieldValue());
        assertNull(type.getValueClass());
        assertFalse(type.isValueCompatible(new StringFieldValue("")));
    }

}
