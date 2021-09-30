// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.request;

import static org.junit.Assert.*;

import java.util.Iterator;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Splitter;
import com.yahoo.text.Lowercase;

/**
 * Module local test of the basic property name building block.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CompoundNameTestCase {

    private static final String NAME = "com.yahoo.processing.request.CompoundNameTestCase";
    private CompoundName cn;

    @Before
    public void setUp() throws Exception {
        cn = new CompoundName(NAME);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public final void testLast() {
        assertEquals(NAME.substring(NAME.lastIndexOf('.') + 1), cn.last());
    }

    @Test
    public final void testFirst() {
        assertEquals(NAME.substring(0, NAME.indexOf('.')), cn.first());
    }

    @Test
    public final void testRest() {
        assertEquals(NAME.substring(NAME.indexOf('.') + 1), cn.rest().toString());
    }

    @Test
    public final void testRestN() {
        assertEquals("a.b.c.d.e", new CompoundName("a.b.c.d.e").rest(0).toString());
        assertEquals("b.c.d.e", new CompoundName("a.b.c.d.e").rest(1).toString());
        assertEquals("c.d.e", new CompoundName("a.b.c.d.e").rest(2).toString());
        assertEquals("d.e", new CompoundName("a.b.c.d.e").rest(3).toString());
        assertEquals("e", new CompoundName("a.b.c.d.e").rest(4).toString());
        assertEquals("", new CompoundName("a.b.c.d.e").rest(5).toString());
    }

    @Test
    public final void testPrefix() {
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b")));
        assertTrue(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.c")));

        assertFalse(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.c.d")));
        assertFalse(new CompoundName("a.b.c").hasPrefix(new CompoundName("a.b.d")));
    }

    @Test
    public final void testSize() {
        Splitter s = Splitter.on('.');
        Iterable<String> i = s.split(NAME);
        int n = 0;
        for (@SuppressWarnings("unused") String x : i) {
            ++n;
        }
        assertEquals(n, cn.size());
    }

    @Test
    public final void testGet() {
        String s = cn.get(0);
        assertEquals(NAME.substring(0, NAME.indexOf('.')), s);
    }

    @Test
    public final void testIsCompound() {
        assertTrue(cn.isCompound());
    }

    @Test
    public final void testIsEmpty() {
        assertFalse(cn.isEmpty());
    }

    @Test
    public final void testAsList() {
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
    public final void testEqualsObject() {
        assertFalse(cn.equals(NAME));
        assertFalse(cn.equals(null));
        assertTrue(cn.equals(cn));
        assertTrue(cn.equals(new CompoundName(NAME)));
    }

    @Test
    public final void testEmptyNonEmpty() {
        assertTrue(new CompoundName("").isEmpty());
        assertEquals(0, new CompoundName("").size());
        assertFalse(new CompoundName("a").isEmpty());
        assertEquals(1, new CompoundName("a").size());
        CompoundName empty = new CompoundName("a.b.c");
        assertTrue(empty == empty.rest(0));
        assertFalse(empty == empty.rest(1));
    }

    @Test
    public final void testGetLowerCasedName() {
        assertEquals(Lowercase.toLowerCase(NAME), cn.getLowerCasedName());
    }

    @Test
    public void testAppend() {
        assertEquals(new CompoundName("a.b.c.d"), new CompoundName("").append(new CompoundName("a.b.c.d")));
        assertEquals(new CompoundName("a.b.c.d"), new CompoundName("a").append(new CompoundName("b.c.d")));
        assertEquals(new CompoundName("a.b.c.d"), new CompoundName("a.b").append(new CompoundName("c.d")));
        assertEquals(new CompoundName("a.b.c.d"), new CompoundName("a.b.c").append(new CompoundName("d")));
        assertEquals(new CompoundName("a.b.c.d"), new CompoundName("a.b.c.d").append(new CompoundName("")));
    }

    @Test
    public void empty_CompoundName_is_prefix_of_any_CompoundName() {
        CompoundName empty = new CompoundName("");

        assertTrue(empty.hasPrefix(empty));
        assertTrue(new CompoundName("a").hasPrefix(empty));
    }

    @Test
    public void whole_components_must_match_to_be_prefix() {
        CompoundName stringPrefix = new CompoundName("a");
        CompoundName name         = new CompoundName("aa");

        assertFalse(name.hasPrefix(stringPrefix));
    }
}
