// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.TokenType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Check simple token types.
 *
 * @author Steinar Knutsen
 */
public class SimpleTokenTypeTestCase {

    @Test
    public final void test() {
        assertEquals(TokenType.ALPHABETIC, tokenType('a'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\u02c1'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\u02c1'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\u01c0'));
        assertEquals(TokenType.SYMBOL, tokenType('\u20dd'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\u0912'));
        assertEquals(TokenType.NUMERIC, tokenType('1'));
        assertEquals(TokenType.PUNCTUATION, tokenType('.'));
        assertEquals(TokenType.PUNCTUATION, tokenType('\u0f3b'));
        assertEquals(TokenType.PUNCTUATION, tokenType('\u0f3c'));
        assertEquals(TokenType.PUNCTUATION, tokenType('\u203f'));
        assertEquals(TokenType.SYMBOL, tokenType('\u2044'));
        assertEquals(TokenType.SYMBOL, tokenType('$'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\u2132'));
        assertEquals(TokenType.ALPHABETIC, tokenType('\uD800', '\uDFC8'));
    }

    private static TokenType tokenType(char c) {
        return SimpleTokenType.valueOf(c);
    }

    private static TokenType tokenType(char high, char low) {
        return SimpleTokenType.valueOf(Character.toCodePoint(high, low));
    }

}
