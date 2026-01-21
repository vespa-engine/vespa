// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;
import com.google.common.base.Splitter;
import com.yahoo.text.Lowercase;

import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Module local test of the basic property name building block.
 *
 * @author Steinar Knutsen
 */
public class CompoundNameTestCase {

    private static final String NAME = "com.yahoo.processing.request.CompoundNameTestCase";
    private static final CompoundName C_NAME = CompoundName.from(NAME);
    private static final CompoundName C_abcde = CompoundName.from("a.b.c.d.e");

    void verifyStrict(CompoundName expected, CompoundName actual) {
        assertEquals(expected, actual);
        assertEquals(expected.asList(), actual.asList());
    }
    void verifyStrict(String expected, CompoundName actual) {
        verifyStrict(CompoundName.from(expected), actual);
    }

    @Test
    void testLast() {
        assertEquals(NAME.substring(NAME.lastIndexOf('.') + 1), C_NAME.last());
    }

    @Test
    void testFirst() {
        assertEquals(NAME.substring(0, NAME.indexOf('.')), C_NAME.first());
    }

    @Test
    void testRest() {
        verifyStrict(NAME.substring(NAME.indexOf('.') + 1), C_NAME.rest());
    }

    @Test
    void testRestN() {
        verifyStrict("a.b.c.d.e", C_abcde.rest(0));
        verifyStrict("b.c.d.e", C_abcde.rest(1));
        verifyStrict("c.d.e", C_abcde.rest(2));
        verifyStrict("d.e", C_abcde.rest(3));
        verifyStrict("e", C_abcde.rest(4));
        verifyStrict(CompoundName.empty, C_abcde.rest(5));
    }

    @Test
    void testFirstN() {
        verifyStrict("a.b.c.d.e", C_abcde.first(5));
        verifyStrict("a.b.c.d", C_abcde.first(4));
        verifyStrict("a.b.c", C_abcde.first(3));
        verifyStrict("a.b", C_abcde.first(2));
        verifyStrict("a", C_abcde.first(1));
        verifyStrict(CompoundName.empty, C_abcde.first(0));
    }

    @Test
    void testPrefix() {
        CompoundName abcc = CompoundName.from("a.b.cc");
        assertTrue(abcc.hasPrefix(CompoundName.empty));
        assertTrue(abcc.hasPrefix(CompoundName.from("a")));
        assertTrue(abcc.hasPrefix(CompoundName.from("a.b")));
        assertTrue(abcc.hasPrefix(CompoundName.from("a.b.cc")));

        assertFalse(abcc.hasPrefix(CompoundName.from("a.b.c")));
        assertFalse(abcc.hasPrefix(CompoundName.from("a.b.c.d")));
        assertFalse(abcc.hasPrefix(CompoundName.from("a.b.d")));
    }

    @Test
    void testIllegalCompound() {
        assertEquals("'a.' is not a legal compound name. Names can not end with a dot.",
                     assertThrows(IllegalArgumentException.class,
                                  () -> CompoundName.from("a."))
                             .getMessage());
        assertEquals("'.b' is not a legal compound name. Consecutive, leading or trailing dots are not allowed.",
                     assertThrows(IllegalArgumentException.class,
                                  () -> CompoundName.from(".b"))
                             .getMessage());
        assertEquals("'a..b' is not a legal compound name. Consecutive, leading or trailing dots are not allowed.",
                     assertThrows(IllegalArgumentException.class,
                                  () -> CompoundName.from("a..b"))
                             .getMessage());
    }

    @Test
    void testFromComponents() {
        verifyStrict("a", CompoundName.fromComponents("a"));
        verifyStrict("a.b", CompoundName.fromComponents("a", "b"));
    }

    @Test
    void testSize() {
        Splitter s = Splitter.on('.');
        Iterable<String> i = s.split(NAME);
        int n = 0;
        for (@SuppressWarnings("unused") String x : i) {
            ++n;
        }
        assertEquals(n, C_NAME.size());
    }

    @Test
    void testGet() {
        String s = C_NAME.get(0);
        assertEquals(NAME.substring(0, NAME.indexOf('.')), s);
    }

    @Test
    void testIsCompound() {
        assertTrue(C_NAME.isCompound());
    }

    @Test
    void testIsEmpty() {
        assertFalse(C_NAME.isEmpty());
    }

    @Test
    void testAsList() {
        List<String> l = C_NAME.asList();
        Splitter peoplesFront = Splitter.on('.');
        Iterable<String> answer = peoplesFront.split(NAME);
        Iterator<String> expected = answer.iterator();
        for (String s : l) {
            assertEquals(expected.next(), s);
        }
        assertFalse(expected.hasNext());
    }

    @Test
    void testEqualsObject() {
        assertNotEquals(C_NAME, NAME);
        assertNotEquals(C_NAME, null);
        verifyStrict(C_NAME, C_NAME);
        verifyStrict(C_NAME, CompoundName.from(NAME));
    }

    @Test
    void testEmptyNonEmpty() {
        assertTrue(CompoundName.empty.isEmpty());
        assertEquals(0, CompoundName.empty.size());
        assertFalse(CompoundName.from("a").isEmpty());
        assertEquals(1, CompoundName.from("a").size());
        CompoundName empty = CompoundName.from("a.b.c");
        verifyStrict(empty, empty.rest(0));
        assertNotEquals(empty, empty.rest(1));
    }

    @Test
    void testGetLowerCasedName() {
        assertEquals(Lowercase.toLowerCase(NAME), C_NAME.getLowerCasedName());
    }

    @Test
    void testAppendCompound() {
        verifyStrict("a.b.c.d", CompoundName.empty.append(CompoundName.from("a.b.c.d")));
        verifyStrict("a.b.c.d", CompoundName.from("a").append(CompoundName.from("b.c.d")));
        verifyStrict("a.b.c.d", CompoundName.from("a.b").append(CompoundName.from("c.d")));
        verifyStrict("a.b.c.d", CompoundName.from("a.b.c").append(CompoundName.from("d")));
        verifyStrict("a.b.c.d", CompoundName.from("a.b.c.d").append(CompoundName.empty));
    }

    @Test
    void empty_CompoundName_is_prefix_of_any_CompoundName() {
        CompoundName empty = new CompoundName("");

        assertTrue(empty.hasPrefix(empty));
        assertTrue(CompoundName.from("a").hasPrefix(empty));
    }

    @Test
    void whole_components_must_match_to_be_prefix() {
        CompoundName stringPrefix = CompoundName.from("a");
        CompoundName name         = CompoundName.from("aa");

        assertFalse(name.hasPrefix(stringPrefix));
    }

    @Test
    void testFirstRest() {
        verifyStrict(CompoundName.empty, CompoundName.empty.rest());

        CompoundName n = CompoundName.from("on.two.three");
        assertEquals("on", n.first());
        verifyStrict("two.three", n.rest());
        n = n.rest();
        assertEquals("two", n.first());
        verifyStrict("three", n.rest());
        n = n.rest();
        assertEquals("three", n.first());
        verifyStrict("", n.rest());
        n = n.rest();
        assertEquals("", n.first());
        verifyStrict("", n.rest());
        n = n.rest();
        assertEquals("", n.first());
        verifyStrict("", n.rest());
    }

    @Test
    void testHashCodeAndEquals() {
        CompoundName n1 = CompoundName.from("venn.d.a");
        CompoundName n2 = new CompoundName(n1.asList());
        assertEquals(n1.hashCode(), n2.hashCode());
        verifyStrict(n1, n2);
    }

    @Test
    void testAppendString() {
        verifyStrict("a", CompoundName.from("a").append(""));
        verifyStrict("a", CompoundName.empty.append("a"));
        verifyStrict("a.b", CompoundName.from("a").append("b"));
        verifyStrict("a.b.c.d", CompoundName.from("a.b").append("c.d"));

        CompoundName name = CompoundName.from("a.b");
        verifyStrict("a.b.c", name.append("c"));
        verifyStrict("a.b.d", name.append("d"));
        verifyStrict("a.b.d.e", name.append("d.e"));
    }

    @Test
    void testEmpty() {
        CompoundName empty = new CompoundName("");
        assertEquals("", empty.toString());
        assertEquals(0, empty.asList().size());
    }

    @Test
    void testAsList2() {
        assertEquals("[one]", CompoundName.from("one").asList().toString());
        assertEquals("[one, two, three]", CompoundName.from("one.two.three").asList().toString());
    }

}
