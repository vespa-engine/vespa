// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author baldersheim
 */
public class NumericFieldValueTestCase {
    @Test
    public void requireThatConstructionFromStringWorks() {
        assertEquals(67, new IntegerFieldValue("67").getInteger());
        assertEquals(67, new LongFieldValue("67").getLong());
        assertEquals(67, new ByteFieldValue("67").getByte());
        assertEquals(67.45, new FloatFieldValue("67.45").getFloat(),    0.00001);
        assertEquals(67.45, new DoubleFieldValue("67.45").getDouble(), 0.000001);
    }
}
