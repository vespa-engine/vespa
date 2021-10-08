// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class NodeVectorTest {

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_add_throws_exception() { new TestNodeVector("foo").add(barNode()); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addindex_throws_exception() { new TestNodeVector("foo").add(0, barNode()); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addAll_throws_exception() { new TestNodeVector("foo").addAll(Arrays.asList(barNode())); }

    @Test(expected = NodeVector.ReadOnlyException.class)
    public void require_that_addAllindex_throws_exception() { new TestNodeVector("foo").addAll(0, Arrays.asList(barNode())); }

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
        StringNode val = new StringNode("foo");
        TestNodeVector v = new TestNodeVector(val.getValue());
        assertTrue(v.contains(val));
        assertFalse(v.contains(barNode()));
        assertTrue(v.containsAll(Arrays.asList(val)));
        assertFalse(v.containsAll(Arrays.asList(val, barNode())));
    }

    @Test
    public void require_that_indexOf_works() {
        StringNode val = new StringNode("foo");
        TestNodeVector v = new TestNodeVector(val.getValue());
        assertFalse(v.isEmpty());
        assertThat(v.indexOf(val), is(0));
        assertThat(v.indexOf(barNode()), is(-1));
        assertThat(v.lastIndexOf(val), is(0));
        assertThat(v.lastIndexOf(barNode()), is(-1));
    }

    @Test
    public void require_that_iterators_work() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val, val, val);
        assertTrue(v.listIterator().hasNext());
        assertTrue(v.listIterator(0).hasNext());
        assertTrue(v.listIterator(1).hasNext());
        assertTrue(v.listIterator(2).hasNext());
        assertFalse(v.listIterator(3).hasNext());
    }

    @Test
    public void require_that_sublisting_works() {
        String val = "foo";
        TestNodeVector v = new TestNodeVector(val, val, val);
        assertThat(v.subList(0, 1).size(), is(1));
        assertThat(v.subList(0, 2).size(), is(2));
        assertThat(v.subList(0, 3).size(), is(3));
        StringNode[] vals = v.toArray(new StringNode[0]);
        assertThat(vals.length, is(3));
    }

    private StringNode barNode() { return new StringNode("bar");}

    private static class TestNodeVector extends LeafNodeVector<String, StringNode> {

        TestNodeVector(String... values) {
            super(Arrays.asList(values), new StringNode());
        }
    }

}
