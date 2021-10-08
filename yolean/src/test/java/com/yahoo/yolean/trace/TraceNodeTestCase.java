// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.trace;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author bratseth
 */
public class TraceNodeTestCase {

    @Test
    public void requireThatAccessorsWork() {
        TraceNode node = new TraceNode(null, 6);
        assertNull(node.payload());
        assertEquals(6, node.timestamp());
        assertFalse(node.children().iterator().hasNext());
        assertFalse(node.descendants(Object.class).iterator().hasNext());
        assertTrue(node.isRoot());
        assertNull(node.parent());
        assertSame(node, node.root());
    }

    @Test
    public void requireThatToStringIsReadable() {
        TraceNode trace = new TraceNode(null, 0)
                .add(new TraceNode("a", 1))
                .add(new TraceNode("b", 2)
                             .add(new TraceNode("c", 3)));
        assertEquals("[ [ a b [ c ] ] ]", trace.toString());
    }

    @Test
    public void requireThatPayloadMayBeNull() {
        TraceNode node = new TraceNode(null, 6);
        assertNull(node.payload());
    }

    @Test
    public void requireThatRootNodesCanBeAdded() {
        TraceNode parent = new TraceNode(null, 1);

        TraceNode foo = new TraceNode(null, 2);
        parent.add(foo);
        assertSame(parent, foo.parent());

        TraceNode bar = new TraceNode(null, 3);
        parent.add(bar);
        assertSame(parent, bar.parent());

        Iterator<TraceNode> children = parent.children().iterator();
        assertTrue(children.hasNext());
        assertSame(foo, children.next());
        assertTrue(children.hasNext());
        assertSame(bar, children.next());
        assertFalse(children.hasNext());

        Iterator<Object> payloads = parent.descendants(Object.class).iterator();
        assertFalse(payloads.hasNext());
    }

    @Test
    public void requireThatNonRootNodeCanNotBeAdded() {
        TraceNode foo = new TraceNode(null, 0);
        TraceNode bar = new TraceNode(null, 0);
        TraceNode baz = new TraceNode(null, 0);
        bar.add(baz);
        try {
            foo.add(baz);
            fail();
        } catch (IllegalArgumentException e) {

        }
        assertSame(bar, baz.parent());
        assertTrue(bar.children().iterator().hasNext());
        assertFalse(foo.children().iterator().hasNext());
    }

    @Test
    public void requireThatChildrenIsNeverNull() {
        assertNotNull(new TraceNode(null, 69).children());
    }

    @Test
    public void requireThatDescendantsIsNeverNull() {
        assertNotNull(new TraceNode(null, 69).descendants(Object.class));
    }

    @Test
    public void requireThatDescendantsOrderIsDepthFirstPrefix() {
        TraceNode trace = new TraceNode(null, 0)
                .add(new TraceNode("a", 0)
                             .add(new TraceNode("b", 0))
                             .add(new TraceNode("c", 0)
                                          .add(new TraceNode("d", 0))
                                          .add(new TraceNode("e", 0))))
                .add(new TraceNode("f", 0)
                             .add(new TraceNode("g", 0)));

        Iterator<String> it = trace.descendants(String.class).iterator();
        assertTrue(it.hasNext());
        assertEquals("a", it.next());
        assertTrue(it.hasNext());
        assertEquals("b", it.next());
        assertTrue(it.hasNext());
        assertEquals("c", it.next());
        assertTrue(it.hasNext());
        assertEquals("d", it.next());
        assertTrue(it.hasNext());
        assertEquals("e", it.next());
        assertTrue(it.hasNext());
        assertEquals("f", it.next());
        assertTrue(it.hasNext());
        assertEquals("g", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void requireThatDescendantsFilterPayloads() {
        TraceNode trace = new TraceNode(null, 0)
                .add(new TraceNode("a", 0)
                             .add(new TraceNode(69, 0))
                             .add(new TraceNode("b", 0)
                                          .add(new TraceNode("c", 0))
                                          .add(new TraceNode(new Object(), 0))))
                .add(new TraceNode("d", 0)
                             .add(new TraceNode("e", 0)));

        Iterator<String> it = trace.descendants(String.class).iterator();
        assertTrue(it.hasNext());
        assertEquals("a", it.next());
        assertTrue(it.hasNext());
        assertEquals("b", it.next());
        assertTrue(it.hasNext());
        assertEquals("c", it.next());
        assertTrue(it.hasNext());
        assertEquals("d", it.next());
        assertTrue(it.hasNext());
        assertEquals("e", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void requireThatVisitorOrderIsDepthFirstPrefix() {
        TraceNode trace = new TraceNode(null, 0)
                .add(new TraceNode("a", 0)
                             .add(new TraceNode("b", 0))
                             .add(new TraceNode("c", 0)
                                          .add(new TraceNode("d", 0))
                                          .add(new TraceNode(3, 0))))
                .add(new TraceNode("f", 0)
                             .add(new TraceNode("g", 0)));

        final List<Object> payloads = new ArrayList<>();
        trace.accept(new TraceVisitor() {

            @Override
            public void visit(TraceNode node) {
                payloads.add(node.payload());
            }
        });
        assertEquals(Arrays.<Object>asList(null, "a", "b", "c", "d", 3, "f", "g"),
                     payloads);
    }

    @Test
    public void requireThatVisitorDoesNotEnterOrLeaveNodesThatHaveNoChildren() {
        TraceNode trace = new TraceNode(null, 0);
        trace.accept(new TraceVisitor() {

            @Override
            public void visit(TraceNode node) {

            }

            @Override
            public void entering(TraceNode node) {
                fail();
            }

            @Override
            public void leaving(TraceNode node) {
                fail();
            }
        });
    }

    @Test
    public void requireThatVisitorEntersAndLeavesNodesThatHaveChildren() {
        TraceNode trace = new TraceNode("", 0)
                .add(new TraceNode("a", 0)
                             .add(new TraceNode("b", 0))
                             .add(new TraceNode("c", 0)
                                          .add(new TraceNode("d", 0))
                                          .add(new TraceNode("e", 0))))
                .add(new TraceNode("f", 0)
                             .add(new TraceNode("g", 0)));

        final StringBuilder out = new StringBuilder();
        trace.accept(new TraceVisitor() {

            @Override
            public void visit(TraceNode node) {
                out.append(node.payload());
            }

            @Override
            public void entering(TraceNode node) {
                out.append("[");
            }

            @Override
            public void leaving(TraceNode node) {
                out.append("]");
            }
        });
        assertEquals("[a[bc[de]]f[g]]", out.toString());
    }
}
