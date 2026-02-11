// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * @author baldersheim
 */
public class IdentifierTestCase {
    @Test
    public void testIdentifier() {
        assertEquals(new Identifier("").toString(), "");
        assertEquals(new Identifier("a").toString(), "a");
        assertEquals(new Identifier("z").toString(), "z");
        assertEquals(new Identifier("B").toString(), "B");
        assertEquals(new Identifier("Z").toString(), "Z");
        assertEquals(new Identifier("_").toString(), "_");
        try {
            assertEquals(new Identifier("0").toString(), "0");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '0' of identifier '0'.");
        }
        try {
            assertEquals(new Identifier("-").toString(), "-");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '-' of identifier '-'.");
        }
        assertEquals(new Identifier("a0_9").toString(), "a0_9");
        assertEquals(new Identifier("a9Z_").toString(), "a9Z_");
        try {
            assertEquals(new Identifier("a-b").toString(), "a-b");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal character '-' of identifier 'a-b'.");
        }

    }
}
