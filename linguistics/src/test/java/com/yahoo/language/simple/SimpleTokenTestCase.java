// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.TokenScript;
import com.yahoo.language.process.TokenType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleTokenTestCase {

    @Test
    public void requireThatOrigAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals("foo", token.getOrig());

        assertEquals(token, new SimpleToken("foo"));
        assertFalse(token.equals(new SimpleToken("bar")));
    }

    @Test
    public void requireThatComponentAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals(0, token.getNumComponents());
        SimpleToken bar = new SimpleToken("bar");
        SimpleToken baz = new SimpleToken("baz");
        token.addComponent(bar);
        token.addComponent(baz);
        assertEquals(2, token.getNumComponents());
        assertSame(bar, token.getComponent(0));
        assertSame(baz, token.getComponent(1));

        SimpleToken other = new SimpleToken("foo");
        assertFalse(token.equals(other));
        other.addComponent(bar);
        assertFalse(token.equals(other));
        other.addComponent(baz);
        assertEquals(token, other);

        other = new SimpleToken("foo");
        other.addComponent(baz);
        other.addComponent(bar);
        assertFalse(token.equals(other));
    }

    @Test
    public void requireThatStemAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals(0, token.getNumStems());
        assertNull(token.getStem(0));
        token.setTokenString("bar");
        assertEquals(1, token.getNumStems());
        assertEquals("bar", token.getStem(0));
    }

    @Test
    public void requireThatTokenStringAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertNull(token.getTokenString());
        token.setTokenString("bar");
        assertEquals("bar", token.getTokenString());
        SimpleToken other = new SimpleToken("foo");
        assertFalse(token.equals(other));
        other.setTokenString("bar");
        assertEquals(token, other);
    }

    @Test
    public void requireThatTypeAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals(TokenType.UNKNOWN, token.getType());
        for (TokenType type : TokenType.values()) {
            token.setType(type);
            assertEquals(type, token.getType());
        }

        SimpleToken other = new SimpleToken("foo");
        for (TokenType type : TokenType.values()) {
            other.setType(type);
            if (type == token.getType()) {
                assertEquals(token, other);
            } else {
                assertFalse(token.equals(other));
            }
        }
    }

    @Test
    public void requireThatScriptAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals(TokenScript.UNKNOWN, token.getScript());
        for (TokenScript script : TokenScript.values()) {
            token.setScript(script);
            assertEquals(script, token.getScript());
        }

        SimpleToken other = new SimpleToken("foo");
        for (TokenScript script : TokenScript.values()) {
            other.setScript(script);
            if (script == token.getScript()) {
                assertEquals(token, other);
            } else {
                assertFalse(token.equals(other));
            }
        }
    }

    @Test
    public void requireThatSpecialTokenAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertFalse(token.isSpecialToken());
        token.setSpecialToken(true);
        assertTrue(token.isSpecialToken());
        token.setSpecialToken(false);
        assertFalse(token.isSpecialToken());

        SimpleToken other = new SimpleToken("foo");
        other.setSpecialToken(true);
        assertFalse(token.equals(other));
        other.setSpecialToken(false);
        assertEquals(token, other);
    }

    @Test
    public void requireThatOffsetAccessorsWork() {
        SimpleToken token = new SimpleToken("foo");
        assertEquals(0, token.getOffset());
        token.setOffset(69);
        assertEquals(69, token.getOffset());

        SimpleToken other = new SimpleToken("foo");
        assertFalse(token.equals(other));
        other.setOffset(69);
        assertEquals(token, other);
    }

    @Test
    public void testDetailString() {
        SimpleToken token = new SimpleToken("my_orig");
        token.addComponent(new SimpleToken("my_component_1"));
        token.addComponent(new SimpleToken("my_component_2"));
        token.setTokenString("my_token_string");
        token.setType(TokenType.ALPHABETIC);
        token.setScript(TokenScript.ARABIC);
        token.setOffset(1);

        String expected = "token : SimpleToken {\n" +
                          "    components : {\n" +
                          "        [0] : SimpleToken {\n" +
                          "            components : {\n" +
                          "            }\n" +
                          "            offset : 0\n" +
                          "            orig : 'my_component_1'\n" +
                          "            script : UNKNOWN\n" +
                          "            special : false\n" +
                          "            token string : null\n" +
                          "            type : UNKNOWN\n" +
                          "        }\n" +
                          "        [1] : SimpleToken {\n" +
                          "            components : {\n" +
                          "            }\n" +
                          "            offset : 0\n" +
                          "            orig : 'my_component_2'\n" +
                          "            script : UNKNOWN\n" +
                          "            special : false\n" +
                          "            token string : null\n" +
                          "            type : UNKNOWN\n" +
                          "        }\n" +
                          "    }\n" +
                          "    offset : 1\n" +
                          "    orig : 'my_orig'\n" +
                          "    script : ARABIC\n" +
                          "    special : false\n" +
                          "    token string : 'my_token_string'\n" +
                          "    type : ALPHABETIC\n" +
                          "}";
        assertEquals(expected, token.toDetailString());
    }

    @Test
    public void requireThatHashCodeIsImplemented() {
        assertEquals(new SimpleToken("foo").hashCode(), new SimpleToken("foo").hashCode());
    }

    @Test
    public void requireThatEqualsIsImplemented() {
        assertFalse(new SimpleToken("foo").equals(new Object()));
        assertEquals(new SimpleToken("foo"), new SimpleToken("foo"));
    }

}
