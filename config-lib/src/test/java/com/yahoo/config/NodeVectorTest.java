// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class NodeVectorTest {
    @Test
    public void require_vector_is_resized() {
        TestNodeVector v = new TestNodeVector("foo");
        v.setSize(2);
        assertThat(v.size(), is(2));
        v.setSize(1);
        assertThat(v.size(), is(1));
    }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_add_throws_exception() { new TestNodeVector("foo").add("bar"); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addindex_throws_exception() { new TestNodeVector("foo").add(0, "bar"); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addAll_throws_exception() { new TestNodeVector("foo").addAll(Arrays.asList("bar")); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addAllindex_throws_exception() { new TestNodeVector("foo").addAll(0, Arrays.asList("bar")); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_clear_throws_exception() { new TestNodeVector("foo").clear(); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_remove_index_throws_exception() { new TestNodeVector("foo").remove(0); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_remove_object_throws_exception() { new TestNodeVector("foo").remove(null); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_removeAll_throws_exception() { new TestNodeVector("foo").removeAll(null); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_retainAll_throws_exception() { new TestNodeVector("foo").retainAll(null); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_set_throws_exception() { new TestNodeVector("foo").set(0, null); }

    @Test
    public void require_that_contains_works() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val);
        v.setSize(1);
        assertTrue(v.contains(val));
        assertFalse(v.contains("bar"));
        assertTrue(v.containsAll(Arrays.asList(val)));
        assertFalse(v.containsAll(Arrays.asList(val, "bar")));
    }

    @Test
    public void require_that_indexOf_works() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val);
        assertTrue(v.isEmpty());
        v.setSize(1);
        assertFalse(v.isEmpty());
        assertThat(v.indexOf(val), is(0));
        assertThat(v.indexOf("bar"), is(-1));
        assertThat(v.lastIndexOf(val), is(0));
        assertThat(v.lastIndexOf("bar"), is(-1));
    }

    @Test
    public void require_that_iterators_work() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val);
        v.setSize(3);
        assertTrue(v.listIterator().hasNext());
        assertTrue(v.listIterator(0).hasNext());
        assertTrue(v.listIterator(1).hasNext());
        assertTrue(v.listIterator(2).hasNext());
        assertFalse(v.listIterator(3).hasNext());
    }

    @Test
    public void require_that_sublisting_works() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val);
        v.setSize(3);
        assertThat(v.subList(0, 1).size(), is(1));
        assertThat(v.subList(0, 2).size(), is(2));
        assertThat(v.subList(0, 3).size(), is(3));
        String[] vals = v.toArray(new String[0]);
        assertThat(vals.length, is(3));
    }

    private static class TestNodeVector extends NodeVector<String> {
        private final String value;
        public TestNodeVector(String value) {
            this.value = value;
        }

        @Override
        protected String createNew() {
            return value;
        }
    }
}
