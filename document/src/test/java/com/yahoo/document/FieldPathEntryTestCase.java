// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class FieldPathEntryTestCase {
    @Test
    public void testKeyParseResult() {
        FieldPathEntry.KeyParseResult result1 = new FieldPathEntry.KeyParseResult("banana", 2);
        FieldPathEntry.KeyParseResult result2 = new FieldPathEntry.KeyParseResult("banana", 2);
        FieldPathEntry.KeyParseResult result3 = new FieldPathEntry.KeyParseResult("apple", 2);
        FieldPathEntry.KeyParseResult result4 = new FieldPathEntry.KeyParseResult("banana", 3);


        assertEquals(result1, result2);
        assertEquals(result2, result1);
        assertEquals(result1.hashCode(), result2.hashCode());
        assertEquals(result1.toString(), result2.toString());

        assertNotEquals(result1, result3);
        assertNotEquals(result3, result1);
        assertNotEquals(result1.hashCode(), result3.hashCode());
        assertNotEquals(result1.toString(), result3.toString());

        assertNotEquals(result1, result4);
        assertNotEquals(result4, result1);
        assertNotEquals(result1.hashCode(), result4.hashCode());
        assertNotEquals(result1.toString(), result4.toString());
    }
}
