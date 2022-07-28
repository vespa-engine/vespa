// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CompoundNameTestCase {

    private static final String NAME = "com.yahoo.processing.request.CompoundNameTestCase";
    private final CompoundName cn = new CompoundName(NAME);

    void verifyStrict(CompoundName expected, CompoundName actual) {
        assertEquals(expected, actual);
        assertEquals(expected.asList(), actual.asList());
    }
    void verifyStrict(String expected, CompoundName actual) {
        verifyStrict(new CompoundName(expected), actual);
    }

    @Test
    final void testLast() {
        assertEquals(NAME.substring(NAME.lastIndexOf('.') + 1), cn.last());
    }

    @Test
    final void testFirst() {
        assertEquals(NAME.substring(0, NAME.indexOf('.')), cn.first());
    }

    @Test
    final void testRest() {
        verifyStrict(NAME.substring(NAME.indexOf('.') + 1), cn.rest());
    }

    @Test
    final void testRestN() {
        verifyStrict("a.b.c.d.e", new CompoundName("a.b.c.d.e").rest(0));
        verifyStrict("b.c.d.e", new CompoundName("a.b.c.d.e").rest(1));
        verifyStrict("c.d.e", new CompoundName("a.b.c.d.e").rest(2));
        verifyStrict("d.e", new CompoundName("a.b.c.d.e").rest(3));
        verifyStrict("e", new CompoundName("a.b.c.d.e").rest(4));
        verifyStrict(CompoundName.empty, new CompoundName("a.b.c.d.e").rest(5));
    }

    @Test
    final void testPrefix() {
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.c")));

        assertFalse(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.c.d")));
        assertFalse(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.d")));
    }

    @Test
    void testFromComponents() {
        verifyStrict("a", CompoundName.fromComponents("a"));
        verifyStrict("a.b", CompoundName.fromComponents("a", "b"));
    }

    @Test
    final void testSize() {
        Splitter s = Splitter.on('.');
        Iterable<String> i = s.split(NAME);
        int n = 0;
        for (@SuppressWarnings("unused") String x : i) {
            ++n;
        }
        assertEquals(n, cn.size());
    }

    @Test
    final void testGet() {
        String s = cn.get(0);
        assertEquals(NAME.substring(0, NAME.indexOf('.')), s);
    }

    @Test
    final void testIsCompound() {
        assertTrue(cn.isCompound());
    }

    @Test
    final void testIsEmpty() {
        assertFalse(cn.isEmpty());
    }

    @Test
    final void testAsList() {
        List<String> l = cn.asList();
        Splitter peoplesFront = Splitter.on('.');
        Iterable<String> answer = peoplesFront.split(NAME);
        Iterator<String> expected = answer.iterator();
        for (int i = 0; i < l.size(); ++i) {
            assertEquals(expected.next(), l.get(i));
        }
        assertFalse(expected.hasNext());
    }

    @Test
    final void testEqualsObject() {
        assertNotEquals(cn, NAME);
        assertNotEquals(cn, null);
        verifyStrict(cn, cn);
        verifyStrict(cn, new CompoundName(NAME));
    }

    @Test
    final void testEmptyNonEmpty() {
        assertTrue(new CompoundName("").isEmpty());
        assertEquals(0, new CompoundName("").size());
        assertFalse(new CompoundName("a").isEmpty());
        assertEquals(1, new CompoundName("a").size());
        CompoundName empty = new CompoundName("a.b.c");
        verifyStrict(empty, empty.rest(0));
        assertNotEquals(empty, empty.rest(1));
    }

    @Test
    final void testGetLowerCasedName() {
        assertEquals(Lowercase.toLowerCase(NAME), cn.getLowerCasedName());
    }

    @Test
    void testAppendCompound() {
        verifyStrict("a.b.c.d", new CompoundName("").append(new CompoundName("a.b.c.d")));
        verifyStrict("a.b.c.d", new CompoundName("a").append(new CompoundName("b.c.d")));
        verifyStrict("a.b.c.d", new CompoundName("a.b").append(new CompoundName("c.d")));
        verifyStrict("a.b.c.d", new CompoundName("a.b.c").append(new CompoundName("d")));
        verifyStrict("a.b.c.d", new CompoundName("a.b.c.d").append(new CompoundName("")));
    }

    @Test
    void empty_CompoundName_is_prefix_of_any_CompoundName() {
        CompoundName empty = new CompoundName("");

        assertTrue(empty.hasPrefix(empty));
        assertTrue(new CompoundName("a").hasPrefix(empty));
    }

    @Test
    void whole_components_must_match_to_be_prefix() {
        CompoundName stringPrefix = new CompoundName("a");
        CompoundName name         = new CompoundName("aa");

        assertFalse(name.hasPrefix(stringPrefix));
    }

    @Test
    void testFirstRest() {
        verifyStrict(CompoundName.empty, CompoundName.empty.rest());

        CompoundName n = new CompoundName("on.two.three");
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
        CompoundName n1 = new CompoundName("venn.d.a");
        CompoundName n2 = new CompoundName(n1.asList());
        assertEquals(n1.hashCode(), n2.hashCode());
        verifyStrict(n1, n2);
    }

    @Test
    void testAppendString() {
        verifyStrict("a", new CompoundName("a").append(""));
        verifyStrict("a", new CompoundName("").append("a"));
        verifyStrict("a.b", new CompoundName("a").append("b"));
        verifyStrict("a.b.c.d", new CompoundName("a.b").append("c.d"));

        CompoundName name = new CompoundName("a.b");
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
        assertEquals("[one]", new CompoundName("one").asList().toString());
        assertEquals("[one, two, three]", new CompoundName("one.two.three").asList().toString());
    }
}
