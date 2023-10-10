// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Simon Thoresen Hult
 */
public class SlobrokListTestCase {

    @Test
    public void requireThatNextSlobrokSpecReturnsNullAtEndOfList() {
        SlobrokList lst = new SlobrokList();
        lst.setup(new String[] { "foo", "bar" });
        if ("[foo, bar]".equals(lst.toString())) {
            assertEquals("foo", lst.nextSlobrokSpec());
            assertEquals("bar", lst.nextSlobrokSpec());
            assertNull(lst.nextSlobrokSpec());
            assertEquals("foo", lst.nextSlobrokSpec());
            assertEquals("bar", lst.nextSlobrokSpec());
            assertNull(lst.nextSlobrokSpec());
            assertEquals("[foo, bar]", lst.toString());
        } else {
            assertEquals("bar", lst.nextSlobrokSpec());
            assertEquals("foo", lst.nextSlobrokSpec());
            assertNull(lst.nextSlobrokSpec());
            assertEquals("bar", lst.nextSlobrokSpec());
            assertEquals("foo", lst.nextSlobrokSpec());
            assertNull(lst.nextSlobrokSpec());
            assertEquals("[bar, foo]", lst.toString());
        }
    }

    @Test
    public void requireThatSiblingsIterateIndependently() {
        SlobrokList foo = new SlobrokList();
        SlobrokList bar = new SlobrokList(foo);
        foo.setup(new String[] { "foo", "bar" });
        if ("[foo, bar]".equals(foo.toString())) {
            assertEquals("foo", foo.nextSlobrokSpec());
            assertEquals("foo", bar.nextSlobrokSpec());
            assertEquals("bar", foo.nextSlobrokSpec());
            assertEquals("bar", bar.nextSlobrokSpec());
            assertNull(foo.nextSlobrokSpec());
            assertNull(bar.nextSlobrokSpec());
        } else {
            assertEquals("bar", foo.nextSlobrokSpec());
            assertEquals("bar", bar.nextSlobrokSpec());
            assertEquals("foo", foo.nextSlobrokSpec());
            assertEquals("foo", bar.nextSlobrokSpec());
            assertNull(foo.nextSlobrokSpec());
            assertNull(bar.nextSlobrokSpec());
        }
    }

    @Test
    public void requireThatLengthIsUpdatedBySetup() {
        SlobrokList foo = new SlobrokList();
        assertEquals(0, foo.length());
        foo.setup(new String[69]);
        assertEquals(69, foo.length());
    }

    @Test
    public void requireThatIndexIsResetOnSetup() {
        SlobrokList lst = new SlobrokList();
        lst.setup(new String[] { "foo", "foo" });
        assertEquals("foo", lst.nextSlobrokSpec());
        lst.setup(new String[] { "baz" });
        assertEquals("baz", lst.nextSlobrokSpec());
        assertNull(lst.nextSlobrokSpec());
        assertEquals("[baz]", lst.toString());
    }

    @Test
    public void requireThatUpdateAffectsSiblings() {
        SlobrokList foo = new SlobrokList();
        SlobrokList bar = new SlobrokList(foo);

        assertEquals(0, foo.length());
        assertEquals(0, bar.length());

        foo.setup(new String[] { "foo" });
        assertEquals(1, foo.length());
        assertEquals(1, bar.length());
        assertEquals("foo", foo.nextSlobrokSpec());
        assertEquals("foo", bar.nextSlobrokSpec());
        assertEquals("[foo]", foo.toString());
        assertEquals("[foo]", bar.toString());

        foo.setup(new String[] { "baz" });
        assertEquals(1, foo.length());
        assertEquals(1, bar.length());
        assertEquals("baz", bar.nextSlobrokSpec());
        assertEquals("baz", foo.nextSlobrokSpec());
        assertNull(foo.nextSlobrokSpec());
        assertNull(bar.nextSlobrokSpec());
        assertEquals("[baz]", foo.toString());
        assertEquals("[baz]", bar.toString());
    }

    @Test
    public void requireThatUpdateAffectsContains() {
        SlobrokList foo = new SlobrokList();
        foo.setup(new String[] { "foo", "bar" });
        assertEquals(2, foo.length());
        String one = foo.nextSlobrokSpec();
        String two = foo.nextSlobrokSpec();
        assertNull(foo.nextSlobrokSpec());
        assertEquals(true, foo.contains(one));
        assertEquals(true, foo.contains(two));
        assertEquals(true, foo.contains("foo"));
        assertEquals(true, foo.contains("bar"));
        assertEquals(false, foo.contains("baz"));

        foo.setup(new String[] { "foo", "baz" });
        assertEquals(2, foo.length());
        assertEquals(true, foo.contains("foo"));
        assertEquals(false, foo.contains("bar"));
        assertEquals(true, foo.contains("baz"));
        one = foo.nextSlobrokSpec();
        two = foo.nextSlobrokSpec();
        assertNull(foo.nextSlobrokSpec());
        assertEquals(true, foo.contains(one));
        assertEquals(true, foo.contains(two));
    }
}
