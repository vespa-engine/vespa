// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.yahoo.search.result.DeepHitIterator;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Ensure that the {@link DeepHitIterator} works as intended.
 *
 * @author havardpe
 */
public class DeepHitIteratorTestCase {

    @Test
    public void testEmpty() {
        HitGroup hits = new HitGroup();
        Iterator<Hit> it = hits.deepIterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {
            // regular iterator behavior
        }
    }

    @Test
    public void testRemove() {
        HitGroup hits = new HitGroup();
        hits.add(new Hit("foo"));
        hits.add(new Hit("bar"));

        Iterator<Hit> it = hits.deepIterator();
        try {
            it.remove();
            fail();
        } catch (IllegalStateException e) {
            // need to call next() first
        }
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertTrue(it.hasNext());
        try {
            it.remove();
            fail();
        } catch (IllegalStateException e) {
            // prefetch done
        }
        assertEquals("bar", it.next().getId().toString());
        it.remove(); // no prefetch done
        assertFalse(it.hasNext());
    }

    @Test
    public void testShallow() {
        HitGroup hits = new HitGroup();
        hits.add(new Hit("foo"));
        hits.add(new Hit("bar"));
        hits.add(new Hit("baz"));

        Iterator<Hit> it = hits.deepIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("bar", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("baz", it.next().getId().toString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testDeep() {
        HitGroup grandParent = new HitGroup();
        grandParent.add(new Hit("a"));
        HitGroup parent = new HitGroup();
        parent.add(new Hit("b"));
        HitGroup child = new HitGroup();
        child.add(new Hit("c"));
        HitGroup grandChild = new HitGroup();
        grandChild.add(new Hit("d"));
        child.add(grandChild);
        child.add(new Hit("e"));
        parent.add(child);
        parent.add(new Hit("f"));
        grandParent.add(parent);
        grandParent.add(new Hit("g"));

        Iterator<Hit> it = grandParent.deepIterator();
        assertTrue(it.hasNext());
        assertEquals("a", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("b", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("c", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("d", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("e", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("f", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("g", it.next().getId().toString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testFirstHitIsGroup() {
        HitGroup root = new HitGroup();
        HitGroup group = new HitGroup();
        group.add(new Hit("foo"));
        root.add(group);
        root.add(new Hit("bar"));

        Iterator<Hit> it = root.deepIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("bar", it.next().getId().toString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testSecondHitIsGroup() {
        HitGroup root = new HitGroup();
        root.add(new Hit("foo"));
        HitGroup group = new HitGroup();
        group.add(new Hit("bar"));
        root.add(group);

        Iterator<Hit> it = root.deepIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertTrue(it.hasNext());
        assertEquals("bar", it.next().getId().toString());
        assertFalse(it.hasNext());
    }

    @Test
    public void testOrder() {
        HitGroup root = new HitGroup();
        MyHitGroup group = new MyHitGroup();
        group.add(new Hit("foo"));
        root.add(group);

        Iterator<Hit> it = root.deepIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertEquals(Boolean.TRUE, group.ordered);
        assertFalse(it.hasNext());

        it = root.unorderedDeepIterator();
        assertTrue(it.hasNext());
        assertEquals("foo", it.next().getId().toString());
        assertEquals(Boolean.FALSE, group.ordered);
        assertFalse(it.hasNext());
    }

    @SuppressWarnings("serial")
    private static class MyHitGroup extends HitGroup {

        Boolean ordered = null;

        @Override
        public Iterator<Hit> iterator() {
            ordered = Boolean.TRUE;
            return super.iterator();
        }

        @Override
        public Iterator<Hit> unorderedIterator() {
            ordered = Boolean.FALSE;
            return super.unorderedIterator();
        }
    }

}
