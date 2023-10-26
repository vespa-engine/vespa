// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author baldersheim
 */
public class LowercaseIdentifierTestCase {
    @Test
    public void testLowercaseIdentifier() {
        assertEquals(new LowercaseIdentifier("").toString(), "");
        assertEquals(new LowercaseIdentifier("a").toString(), "a");
        assertEquals(new LowercaseIdentifier("z").toString(), "z");
        assertEquals(new LowercaseIdentifier("_").toString(), "_");
        try {
            assertEquals(new Identifier("0").toString(), "0");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal starting character '0' of identifier '0'.");
        }
        try {
            assertEquals(new LowercaseIdentifier("Z").toString(), "z");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal uppercase character 'Z' of identifier 'Z'.");
        }
        try {
            assertEquals(new LowercaseIdentifier("aZb").toString(), "azb");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Illegal uppercase character 'Z' of identifier 'aZb'.");
        }



    }
}
