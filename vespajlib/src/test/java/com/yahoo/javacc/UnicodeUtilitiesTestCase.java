// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.javacc;

import org.junit.Test;

import static com.yahoo.javacc.UnicodeUtilities.quote;
import static com.yahoo.javacc.UnicodeUtilities.unquote;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class UnicodeUtilitiesTestCase {

    @Test
    public void testQuote() {
        assertEquals("'\\f'", quote("\f", '\''));
        assertEquals("'\\n'", quote("\n", '\''));
        assertEquals("'\\r'", quote("\r", '\''));
        assertEquals("'\\t'", quote("\t", '\''));

        for (char c = 'a'; c <= 'z'; ++c) {
            assertEquals("'" + c + "'", quote(String.valueOf(c), '\''));
        }

        assertEquals("'\\u4f73'", quote("\u4f73", '\''));
        assertEquals("'\\u80fd'", quote("\u80fd", '\''));
        assertEquals("'\\u7d22'", quote("\u7d22", '\''));
        assertEquals("'\\u5c3c'", quote("\u5c3c", '\''));
        assertEquals("'\\u60e0'", quote("\u60e0", '\''));
        assertEquals("'\\u666e'", quote("\u666e", '\''));

        assertEquals("\"foo\"", quote("foo", '"'));
        assertEquals("\"'foo\"", quote("'foo", '"'));
        assertEquals("\"foo'\"", quote("foo'", '"'));
        assertEquals("\"'foo'\"", quote("'foo'", '"'));
        assertEquals("\"\\\"foo\"", quote("\"foo", '"'));
        assertEquals("\"foo\\\"\"", quote("foo\"", '"'));
        assertEquals("\"\\\"foo\\\"\"", quote("\"foo\"", '"'));
        assertEquals("\"\\\"'foo'\\\"\"", quote("\"'foo'\"", '"'));
        assertEquals("\"'\\\"foo\\\"'\"", quote("'\"foo\"'", '"'));
        assertEquals("\"'f\\\\'o\\\"o\\\\\\\\'\"", quote("'f\\'o\"o\\\\'", '"'));

        assertEquals("\"\\female \\nude fa\\rt fe\\tish\"", quote("\female \nude fa\rt fe\tish", '"'));
        assertEquals("\"\\u666e\"", quote("\u666e", '"'));
    }

    @Test
    public void testQuoteUnquote() {
        assertEquals("\"foo\"", quote(unquote("'foo'"), '"'));
        assertEquals("\"\\foo\"", quote(unquote(quote("\foo", '"')), '"'));
        assertEquals("\u666e", unquote(quote("\u666e", '"')));
    }

    @Test
    public void testUnquote() {
        assertEquals("foo", unquote("foo"));
        assertEquals("'foo", unquote("'foo"));
        assertEquals("foo'", unquote("foo'"));
        assertEquals("foo", unquote("'foo'"));
        assertEquals("\"foo", unquote("\"foo"));
        assertEquals("foo\"", unquote("foo\""));
        assertEquals("foo", unquote("\"foo\""));
        assertEquals("'foo'", unquote("\"'foo'\""));
        assertEquals("\"foo\"", unquote("'\"foo\"'"));
        assertEquals("f'o\"o\\", unquote("'f\\'o\"o\\\\'"));

        assertEquals("\female \nude fa\rt fe\tish", unquote("'\\female \\nude fa\\rt fe\\tish'"));
        assertEquals("\u666e", unquote("\"\\u666e\""));

        try {
            unquote("\"\\uSiM0N\"");
            fail();
        } catch (IllegalArgumentException e) {

        }
        assertEquals("simo\n", unquote("'\\s\\i\\m\\o\\n'"));
        try {
            unquote("\"foo\"bar\"");
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            unquote("'foo'bar'");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatTokenIncludesOnlyAcceptedChars() {
        assertEquals("\"\\u0000\",\"\\u7777\",\"\\uffff\",",
                     UnicodeUtilities.generateToken(new UnicodeUtilities.Predicate() {

                         @Override
                         public boolean accepts(char c) {
                             return c == 0x0000 || c == 0x7777 || c == 0xffff;
                         }
                     }));
        assertEquals("\"\\u0006\",\"\\u0009\",\"\\u0060\"-\"\\u0069\",",
                     UnicodeUtilities.generateToken(new UnicodeUtilities.Predicate() {

                         @Override
                         public boolean accepts(char c) {
                             return c == 0x6 || c == 0x9 || (c >= 0x60 && c <= 0x69);
                         }
                     }));
    }
}
