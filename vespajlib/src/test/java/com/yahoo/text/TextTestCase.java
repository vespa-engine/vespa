package com.yahoo.text;

import org.junit.Test;

import java.util.Arrays;
import java.util.OptionalInt;

import static com.yahoo.text.Text.stripSuffix;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class TextTestCase {

    @Test
    public void testValidateTextString() {
        assertFalse(Text.validateTextString("valid").isPresent());
        assertEquals(OptionalInt.of(1), Text.validateTextString("text\u0001text\u0003"));
        assertEquals(OptionalInt.of(917503),
                     Text.validateTextString(new StringBuilder().appendCodePoint(0xDFFFF).toString()));
        assertEquals(OptionalInt.of(917503),
                     Text.validateTextString(new StringBuilder("foo").appendCodePoint(0xDFFFF).toString()));
        assertEquals(OptionalInt.of(917503),
                     Text.validateTextString(new StringBuilder().appendCodePoint(0xDFFFF).append("foo").toString()));
        assertEquals(OptionalInt.of(917503),
                     Text.validateTextString(new StringBuilder("foo").appendCodePoint(0xDFFFF).append("foo").toString()));
    }

    @Test
    public void testStripTextString() {
        assertEquals("", Text.stripInvalidCharacters(""));
        assertEquals("valid", Text.stripInvalidCharacters("valid"));
        assertEquals("text text ", Text.stripInvalidCharacters("text\u0001text\u0003"));
        assertEquals(" ",
                     Text.stripInvalidCharacters(new StringBuilder().appendCodePoint(0xDFFFF).toString()));
        assertEquals("foo ",
                     Text.stripInvalidCharacters(new StringBuilder("foo").appendCodePoint(0xDFFFF).toString()));
        assertEquals(" foo",
                     Text.stripInvalidCharacters(new StringBuilder().appendCodePoint(0xDFFFF).append("foo").toString()));
        assertEquals("foo foo",
                     Text.stripInvalidCharacters(new StringBuilder("foo").appendCodePoint(0xDFFFF).append("foo").toString()));
    }

    @Test
    public void testStripSuffix() {
        assertThat(stripSuffix("abc.def", ".def"), is("abc"));
        assertThat(stripSuffix("abc.def", ""), is("abc.def"));
        assertThat(stripSuffix("", ".def"), is(""));
        assertThat(stripSuffix("", ""), is(""));
    }

    @Test
    public void testImplode() {
        assertEquals(StringUtilities.implode(null, null), null);
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
        assertEquals(StringUtilities.implodeMultiline(Arrays.asList("foo", "bar")), "foo\nbar");
        assertEquals(StringUtilities.implodeMultiline(Arrays.asList("")), "");
        assertEquals(StringUtilities.implodeMultiline(null), null);
        assertEquals(StringUtilities.implodeMultiline(Arrays.asList("\n")), "\n");
    }

}
