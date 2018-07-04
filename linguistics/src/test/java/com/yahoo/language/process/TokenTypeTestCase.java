// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.process;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class TokenTypeTestCase {

    @Test
    @SuppressWarnings("deprecation")
    public void requireThatValueOfWorks() {
        for (TokenType type : TokenType.values()) {
            assertEquals(type, TokenType.valueOf(type.getValue()));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void requireThatValueOfUnknownIsUnknown() {
        assertEquals(TokenType.UNKNOWN, TokenType.valueOf(-1));
    }

    @Test
    public void requireThatOnlyAlphaNumericsAreIndexable() {
        for (TokenType type : TokenType.values()) {
            if (type == TokenType.ALPHABETIC || type == TokenType.NUMERIC) {
                assertTrue(type.isIndexable());
            } else {
                assertFalse(type.isIndexable());
            }
        }
    }

}
