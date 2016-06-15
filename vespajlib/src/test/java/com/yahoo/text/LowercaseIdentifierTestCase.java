// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created with IntelliJ IDEA.
 * User: balder
 * Date: 11.11.12
 * Time: 20:54
 * To change this template use File | Settings | File Templates.
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
