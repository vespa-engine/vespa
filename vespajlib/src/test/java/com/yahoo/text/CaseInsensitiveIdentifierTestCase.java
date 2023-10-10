// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;


import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class CaseInsensitiveIdentifierTestCase {
    @Test
    public void testCaseInsentivitity() {
        assertEquals(new CaseInsensitiveIdentifier("").toString(), "");
        assertEquals(new CaseInsensitiveIdentifier("a").toString(), "a");
        assertEquals(new CaseInsensitiveIdentifier("z").toString(), "z");
        assertEquals(new CaseInsensitiveIdentifier("B").toString(), "B");
        assertEquals(new CaseInsensitiveIdentifier("Z").toString(), "Z");
        assertEquals(new CaseInsensitiveIdentifier("_").toString(), "_");
        try {
            assertEquals(new CaseInsensitiveIdentifier("0").toString(), "0");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '0' of identifier '0'.");
        }
        try {
            assertEquals(new CaseInsensitiveIdentifier("-").toString(), "-");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '-' of identifier '-'.");
        }
        assertEquals(new CaseInsensitiveIdentifier("a0_9").toString(), "a0_9");
        assertEquals(new Identifier("a9Z_").toString(), "a9Z_");
        try {
            assertEquals(new CaseInsensitiveIdentifier("a-b").toString(), "a-b");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal character '-' of identifier 'a-b'.");
        }
        assertEquals(new CaseInsensitiveIdentifier("AbC"), new CaseInsensitiveIdentifier("ABC"));
        assertEquals(new CaseInsensitiveIdentifier("AbC").hashCode(), new CaseInsensitiveIdentifier("ABC").hashCode());

    }

}
