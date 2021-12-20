// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.List;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilitiesTest {

    @Test
    public void testEscape() {
        assertEquals("abz019ABZ", StringUtilities.escape("abz019ABZ"));
        assertEquals("\\t", StringUtilities.escape("\t"));
        assertEquals("\\n", StringUtilities.escape("\n"));
        assertEquals("\\r", StringUtilities.escape("\r"));
        assertEquals("\\\"", StringUtilities.escape("\""));
        assertEquals("\\f", StringUtilities.escape("\f"));
        assertEquals("\\\\", StringUtilities.escape("\\"));
        assertEquals("\\x05", StringUtilities.escape("" + (char) 5));
        assertEquals("\\tA\\ncombined\\r\\x055test", StringUtilities.escape("\tA\ncombined\r" + ((char) 5) + "5test"));
        assertEquals("A\\x20space\\x20separated\\x20string", StringUtilities.escape("A space separated string", ' '));
    }

    @Test
    public void testUnescape() {
        assertEquals("abz019ABZ", StringUtilities.unescape("abz019ABZ"));
        assertEquals("\t", StringUtilities.unescape("\\t"));
        assertEquals("\n", StringUtilities.unescape("\\n"));
        assertEquals("\r", StringUtilities.unescape("\\r"));
        assertEquals("\"", StringUtilities.unescape("\\\""));
        assertEquals("\f", StringUtilities.unescape("\\f"));
        assertEquals("\\", StringUtilities.unescape("\\\\"));
        assertEquals("" + (char) 5, StringUtilities.unescape("\\x05"));
        assertEquals("\tA\ncombined\r" + ((char) 5) + "5test", StringUtilities.unescape("\\tA\\ncombined\\r\\x055test"));
        assertEquals("A space separated string", StringUtilities.unescape("A\\x20space\\x20separated\\x20string"));
    }

    @Test
    public void testImplode() {
        assertNull(StringUtilities.implode(null, null));
        assertEquals(StringUtilities.implode(new String[0], null), "");
        assertEquals(StringUtilities.implode(new String[] {"foo"}, null), "foo");
        assertEquals(StringUtilities.implode(new String[] {"foo"}, "asdfsdfsadfsadfasdfs"), "foo");
        assertEquals(StringUtilities.implode(new String[] {"foo", "bar"}, null), "foobar");
        assertEquals(StringUtilities.implode(new String[] {"foo", "bar"}, "\n"), "foo\nbar");
        assertEquals(StringUtilities.implode(new String[] {"foo"}, "\n"), "foo");
        assertEquals(StringUtilities.implode(new String[] {"foo", "bar", null}, "\n"), "foo\nbar\nnull");
        assertEquals(StringUtilities.implode(new String[] {"foo", "bar"}, "\n"), "foo\nbar");
        assertEquals(StringUtilities.implode(new String[] {"foo", "bar", "baz"}, null), "foobarbaz");

    }
    
    @Test
    public void testImplodeMultiline() {
        assertEquals(StringUtilities.implodeMultiline(List.of("foo", "bar")), "foo\nbar");
        assertEquals(StringUtilities.implodeMultiline(List.of("")), "");
        assertNull(StringUtilities.implodeMultiline(null));
        assertEquals(StringUtilities.implodeMultiline(List.of("\n")), "\n");
    }

    @Test
    public void testTruncation() {
        String a = "abbc";
        assertSame(a, StringUtilities.truncateSequencesIfNecessary(a, 2));
        assertNotSame(a, StringUtilities.truncateSequencesIfNecessary(a, 1));
        assertEquals("abc", StringUtilities.truncateSequencesIfNecessary(a, 1));
        assertEquals("abc", StringUtilities.truncateSequencesIfNecessary("aabbccc", 1));
        assertEquals("abc", StringUtilities.truncateSequencesIfNecessary("abcc", 1));
        assertEquals("abc", StringUtilities.truncateSequencesIfNecessary("aabc", 1));
        assertEquals("abcb", StringUtilities.truncateSequencesIfNecessary("abcb", 1));
        assertEquals("g g g g g g g g g g\n     g g g g g g g g g g\n     g g g g g g g g g g", StringUtilities.truncateSequencesIfNecessary("g g g g g g g g g g\n        g g g g g g g g g g\n        g g g g g g g g g g", 5));
    }


    @Test
    public void testStripSuffix() {
        assertEquals("abc", StringUtilities.stripSuffix("abc.def", ".def"));
        assertEquals("abc.def", StringUtilities.stripSuffix("abc.def", ""));
        assertTrue(StringUtilities.stripSuffix("", ".def").isEmpty());
        assertTrue(StringUtilities.stripSuffix("", "").isEmpty());
    }
}
