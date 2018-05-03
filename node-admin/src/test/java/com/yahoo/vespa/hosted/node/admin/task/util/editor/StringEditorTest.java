// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import org.junit.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StringEditorTest {
    private final StringEditor editor = new StringEditor();
    private final Cursor cursor = editor.cursor();

    @Test
    public void testBasics() {
        assertCursor(0, 0, "");

        cursor.write("hello");
        assertCursor(0, 5, "hello");

        cursor.write("one\ntwo");
        assertCursor(1, 3, "helloone\ntwo");

        cursor.deleteAll();
        assertCursor(0, 0, "");

        cursor.moveForward();
        assertCursor(0, 0, "");

        cursor.writeLine("foo");
        assertCursor(1, 0, "foo\n");

        cursor.writeLines("one", "two");
        assertCursor(3, 0, "foo\none\ntwo\n");

        cursor.deleteBackward();
        assertCursor(2, 3, "foo\none\ntwo");

        cursor.deleteBackward(2);
        assertCursor(2, 1, "foo\none\nt");

        Mark mark = cursor.createMark();

        cursor.moveToStartOfPreviousLine().moveBackward(2);
        assertCursor(0, 2, "foo\none\nt");

        assertEquals("o\none\nt", cursor.getTextTo(mark));

        cursor.deleteTo(mark);
        assertCursor(0, 2, "fo");

        cursor.deleteBackward(2);
        assertCursor(0, 0, "");

        cursor.writeLines("one", "two", "three").moveToStartOfBuffer();
        assertCursor(0, 0, "one\ntwo\nthree\n");

        Pattern pattern = Pattern.compile("t(.)");
        Optional<Match> match = cursor.moveForwardToEndOfMatch(pattern);
        assertCursor(1, 2, "one\ntwo\nthree\n");
        assertTrue(match.isPresent());
        assertEquals("tw", match.get().match());
        assertEquals("", match.get().prefix());
        assertEquals("o", match.get().suffix());
        assertEquals(new Position(1, 0), match.get().startOfMatch());
        assertEquals(new Position(1, 2), match.get().endOfMatch());
        assertEquals(1, match.get().groupCount());
        assertEquals("w", match.get().group(1));

        match = cursor.moveForwardToEndOfMatch(pattern);
        assertCursor(2, 2, "one\ntwo\nthree\n");
        assertTrue(match.isPresent());
        assertEquals("th", match.get().match());
        assertEquals(1, match.get().groupCount());
        assertEquals("h", match.get().group(1));

        match = cursor.moveForwardToEndOfMatch(pattern);
        assertCursor(2, 2, "one\ntwo\nthree\n");
        assertFalse(match.isPresent());

        assertTrue(cursor.skipBackward("h"));
        assertCursor(2, 1, "one\ntwo\nthree\n");
        assertFalse(cursor.skipBackward("x"));

        assertTrue(cursor.skipForward("hre"));
        assertCursor(2, 4, "one\ntwo\nthree\n");
        assertFalse(cursor.skipForward("x"));

        try {
            cursor.moveTo(mark);
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        mark = cursor.createMark();
        cursor.moveToStartOfBuffer();
        assertEquals(new Position(0, 0), cursor.getPosition());
        cursor.moveTo(mark);
        assertEquals(new Position(2, 4), cursor.getPosition());

        cursor.moveTo(1, 2);
        assertCursor(1, 2, "one\ntwo\nthree\n");

        cursor.deleteSuffix();
        assertCursor(1, 2, "one\ntw\nthree\n");

        cursor.deletePrefix();
        assertCursor(1, 0, "one\n\nthree\n");

        cursor.deleteLine();
        assertCursor(1, 0, "one\nthree\n");

        cursor.deleteLine();
        assertCursor(1, 0, "one\n");

        cursor.deleteLine();
        assertCursor(1, 0, "one\n");

        cursor.moveToStartOfBuffer().moveForward().writeNewlineAfter();
        assertCursor(0, 1, "o\nne\n");

        cursor.deleteAll().writeLines("one", "two", "three", "four");
        cursor.moveToStartOfBuffer().moveToStartOfNextLine();
        assertCursor(1, 0, "one\ntwo\nthree\nfour\n");
        Pattern pattern2 = Pattern.compile("(o)(.)?");
        int count = cursor.replaceMatches(pattern2, m -> {
            String prefix = m.group(2) == null ? "" : m.group(2);
            return prefix + m.match() + m.group(1);
        });
        assertCursor(3, 5, "one\ntwoo\nthree\nfuouor\n");
        assertEquals(2, count);

        cursor.moveToStartOfBuffer().moveToEndOfLine();
        Pattern pattern3 = Pattern.compile("o");
        count = cursor.replaceMatches(pattern3, m -> "a");
        assertEquals(4, count);
        assertCursor(3, 5, "one\ntwaa\nthree\nfuauar\n");
    }

    private void assertCursor(int lineIndex, int columnIndex, String text) {
        assertEquals(text, cursor.getBufferText());
        assertEquals(new Position(lineIndex, columnIndex), cursor.getPosition());
    }

}