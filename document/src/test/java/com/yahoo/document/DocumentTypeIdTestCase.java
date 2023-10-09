// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DocumentTypeIdTestCase {

    @Test
    public void requireThatToStringWorks() {
        DocumentTypeId r = new DocumentTypeId(123);
        assertTrue(r.toString().contains("123"));
    }

    @Test
    public void requireThatEqualsAndHashCodeWorks() {
        DocumentTypeId r1 = new DocumentTypeId(123);
        DocumentTypeId r2 = new DocumentTypeId(123);
        DocumentTypeId r3 = new DocumentTypeId(456);

        assertEquals(r1, r1);
        assertEquals(r1, r2);
        assertEquals(r2, r1);
        assertEquals(r1.hashCode(), r2.hashCode());

        assertNotEquals(r1, r3);
        assertNotEquals(r3, r1);
        assertNotEquals(r2, r3);
        assertNotEquals(r3, r2);
        assertNotEquals(r1.hashCode(), r3.hashCode());

        assertNotEquals(r1, new Object());
        assertFalse(r1.equals("foobar"));
    }

    @Test
    public void requireThatAccessorsWork() {
        DocumentTypeId r1 = new DocumentTypeId(123);
        assertEquals(123, r1.getId());
    }
}
