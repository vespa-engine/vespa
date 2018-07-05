// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.javacc;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class FastCharStreamTestCase {

    @Test
    public void requireThatInputCanBeRead() throws IOException {
        FastCharStream input = new FastCharStream("foo");
        assertEquals('f', input.readChar());
        assertEquals('o', input.readChar());
        assertEquals('o', input.readChar());
        try {
            input.readChar();
            fail();
        } catch (IOException e) {

        }
    }

    @Test
    public void requireThatColumnIsTracked() throws IOException {
        FastCharStream input = new FastCharStream("foo");
        assertEquals(1, input.getEndColumn());
        input.readChar();
        assertEquals(2, input.getEndColumn());
        input.readChar();
        assertEquals(3, input.getEndColumn());
        input.readChar();
        assertEquals(4, input.getEndColumn());
    }

    @Test
    public void requireThatLineIsNotTracked() throws IOException {
        FastCharStream input = new FastCharStream("f\no");
        assertEquals(-1, input.getEndLine());
        assertEquals(-1, input.getBeginLine());
        input.readChar();
        assertEquals(-1, input.getBeginLine());
        input.readChar();
        assertEquals(-1, input.getBeginLine());
        input.readChar();
        assertEquals(-1, input.getBeginLine());
        assertEquals(-1, input.getEndLine());
    }


    @Test
    public void requireThatBackupIsSupported() throws IOException {
        FastCharStream input = new FastCharStream("foo");
        assertEquals('f', input.readChar());
        input.backup(1);
        assertEquals('f', input.readChar());
        assertEquals('o', input.readChar());
        input.backup(2);
        assertEquals('f', input.readChar());
        assertEquals('o', input.readChar());
        assertEquals('o', input.readChar());
        input.backup(3);
        assertEquals('f', input.readChar());
        assertEquals('o', input.readChar());
        assertEquals('o', input.readChar());
        input.backup(2);
        assertEquals('o', input.readChar());
        assertEquals('o', input.readChar());
        input.backup(1);
        assertEquals('o', input.readChar());
    }

    @Test
    public void requireThatSuffixIsNotSupported() {
        try {
            new FastCharStream("foo").GetSuffix(0);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatDoneDoesNotThrowException() {
        FastCharStream input = new FastCharStream("foo");
        input.Done();
    }

    @Test
    public void requireThatTokensCanBeRetrieved() throws IOException {
        FastCharStream input = new FastCharStream("foo bar baz");
        input.readChar();
        input.readChar();
        input.readChar();
        input.readChar();
        assertEquals('b', input.BeginToken());
        assertEquals(5, input.getBeginColumn());
        assertEquals(-1, input.getBeginLine());
        assertEquals(6, input.getEndColumn());
        assertEquals(-1, input.getEndLine());
        assertEquals('a', input.readChar());
        assertEquals('r', input.readChar());
        assertEquals(8, input.getEndColumn());
        assertEquals(-1, input.getEndLine());
        assertEquals("bar", input.GetImage());
    }

    @Test
    public void requireThatExceptionsDetectLineNumber() {
        FastCharStream input = new FastCharStream("foo\nbar");
        assertEquals("line 2, column 1\n" +
                     "At position:\n" +
                     "bar\n" +
                     "^",
                     input.formatException("line -1, column 5"));
        assertEquals("line 2, column 2\n" +
                     "At position:\n" +
                     "bar\n" +
                     " ^",
                     input.formatException("line -1, column 6"));
        assertEquals("line 2, column 3\n" +
                     "At position:\n" +
                     "bar\n" +
                     "  ^",
                     input.formatException("line -1, column 7"));
        assertEquals("line 2, column 4\n" +
                     "At position:\n" +
                     "bar\n" +
                     "   ^",
                     input.formatException("line -1, column 8"));
        assertEquals("foo line 2, column 2\n" +
                     "At position:\n" +
                     "bar\n" +
                     " ^",
                     input.formatException("foo line -1, column 6"));
        assertEquals("foo line 2, column 2 bar\n" +
                     "At position:\n" +
                     "bar\n" +
                     " ^",
                     input.formatException("foo line -1, column 6 bar"));
        assertEquals("line 2, column 2 bar\n" +
                     "At position:\n" +
                     "bar\n" +
                     " ^",
                     input.formatException("line -1, column 6 bar"));
    }

    @Test
    public void requireErrorMsgExceptionAtEOF() {
        FastCharStream input = new FastCharStream("\n");
        assertEquals("line 1, column 1\n" +
                     "At position:\n" +
                     "EOF\n" +
                     "^",
                     input.formatException("line -1, column 1"));
    }
    
    @Test
    public void requireThatUnknownExceptionFormatIsIgnored() {
        FastCharStream input = new FastCharStream("foo\nbar");
        assertEquals("",
                     input.formatException(""));
        assertEquals("foo",
                     input.formatException("foo"));
        assertEquals("foo line -1, column ",
                     input.formatException("foo line -1, column "));
        assertEquals("foo line -1, column bar",
                     input.formatException("foo line -1, column bar"));
    }

    @Test
    public void requireThatIllegalExceptionColumnIsIgnored() {
        FastCharStream input = new FastCharStream("foo\nbar");
        assertEquals("line -1, column 9",
                     input.formatException("line -1, column 9"));
    }
}
