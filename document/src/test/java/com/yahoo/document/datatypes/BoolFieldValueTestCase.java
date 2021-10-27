// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author baldersheim
 */
public class BoolFieldValueTestCase {
    @Test
    public void requireCorrectConstruction() {
        assertEquals(false, new BoolFieldValue().getBoolean());
        assertEquals(true, new BoolFieldValue(true).getBoolean());
        assertEquals(false, new BoolFieldValue(false).getBoolean());
        assertEquals(true, new BoolFieldValue("true").getBoolean());
        assertEquals(false, new BoolFieldValue("false").getBoolean());
    }

    private void verifyAssign(boolean expected, Object o) {
        BoolFieldValue v = new BoolFieldValue(!expected);
        v.assign(o);
        assertEquals(expected, v.getBoolean());
    }
    @Test
    public void requireCorrectAssign() {
        verifyAssign(true, "true");
        verifyAssign(false, "false");
        verifyAssign(false, "");
        verifyAssign(true, new StringFieldValue("true"));
        verifyAssign(false, new StringFieldValue("false"));
        verifyAssign(true, true);
        verifyAssign(false, false);
        verifyAssign(true, new BoolFieldValue(true));
        verifyAssign(false, new BoolFieldValue(false));
    }
}
