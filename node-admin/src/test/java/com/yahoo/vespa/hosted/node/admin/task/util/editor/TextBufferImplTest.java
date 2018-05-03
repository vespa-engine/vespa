// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextBufferImplTest {
    private final TextBufferImpl textBuffer = new TextBufferImpl();

    @Test
    public void testWrite() {
        assertEquals("", textBuffer.getString());
        assertWrite(2, 0, "foo\nbar\n",
                0, 0, "foo\nbar\n");

        assertWrite(1, 6, "fofirst\nsecondo\nbar\n",
                0, 2, "first\nsecond");

        assertWrite(3, 1, "fofirst\nsecondo\nbar\na",
                3, 0, "a");
        assertWrite(4, 0, "fofirst\nsecondo\nbar\na\n",
                3, 1, "\n");
    }

    @Test
    public void testDelete() {
        write(0, 0, "foo\nbar\nzoo\n");
        delete(0, 2, 2, 1);
        assertEquals("fooo\n", textBuffer.getString());

        delete(0, 4, 1, 0);
        assertEquals("fooo", textBuffer.getString());

        delete(0, 0, 0, 4);
        assertEquals("", textBuffer.getString());

        delete(0, 0, 0, 0);
        assertEquals("", textBuffer.getString());
    }

    private void assertWrite(int expectedLineIndex, int expectedColumnIndex, String expectedString,
                             int lineIndex, int columnIndex, String text) {
        Position position = write(lineIndex, columnIndex, text);
        assertEquals(new Position(expectedLineIndex, expectedColumnIndex), position);
        assertEquals(expectedString, textBuffer.getString());
    }

    private Position write(int lineIndex, int columnIndex, String text) {
        return textBuffer.write(new Position(lineIndex, columnIndex), text);
    }

    private void delete(int startLineIndex, int startColumnIndex,
                        int endLineIndex, int endColumnIndex) {
        textBuffer.delete(new Position(startLineIndex, startColumnIndex),
                new Position(endLineIndex, endColumnIndex));
    }
}