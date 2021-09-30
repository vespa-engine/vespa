// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.trace;

import com.yahoo.yolean.concurrent.ThreadRobustList;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * <p>This class represents a single node in a tree of <code>TraceNodes</code>. The trace forms a tree where there is a
 * branch for each parallel execution, and a node within such a branch for each traced event. As each <code>TraceNode</code>
 * may contain a payload of any type, the trace tree can be used to exchange any thread-safe state between producers and
 * consumers in different threads, whether or not the shape of the trace tree is relevant to the particular
 * information.</p>
 * <p>This class uses a {@link ThreadRobustList} for its children. That list allows multiple threads to inspect the
 * hierarchy of a <code>TraceNode</code> tree while there are other threads concurrently modifying it, without incurring the
 * cost of memory synchronization. The only caveat being that for each <code>TraceNode</code> there can never be more than
 * exactly one writer thread. If multiple threads need to mutate a single <code>TraceNode</code>, then the writer threads
 * need to synchronize their access on the <code>TraceNode</code>.</p>
 *
 * @author Steinar Knutsen
 * @author bratseth
 * @since 5.1.15
 */
public class TraceNode {

    private final Object payload;
    private final long timestamp;
    private ThreadRobustList<TraceNode> children;
    private TraceNode parent;

    /**
     * <p>Creates a new instance of this class.</p>
     *
     * @param payload   the payload to assign to this, may be <code>null</code>
     * @param timestamp the timestamp to assign to this
     */
    public TraceNode(Object payload, long timestamp) {
        this.payload = payload;
        this.timestamp = timestamp;
    }

    /**
     * <p>Adds another <code>TraceNode</code> as a child to this.</p>
     *
     * @param child the TraceNode to add
     * @return this, to allow chaining
     * @throws IllegalArgumentException if <code>child</code> is not a root TraceNode
     * @see #isRoot()
     */
    public TraceNode add(TraceNode child) {
        if (child.parent != null) {
            throw new IllegalArgumentException("Can not add " + child + " to " + this + "; it is not a root.");
        }
        child.parent = this;
        if (children == null) {
            children = new ThreadRobustList<>();
        }
        children.add(child);
        return this;
    }

    /**
     * <p>Returns a read-only iterable of all {@link #payload() payloads} that are instances of <code>payloadType</code>,
     * in all its decendants. The payload of <em>this</em> <code>TraceNode</code> is ignored.</p>
     * <p>The payloads are retrieved in depth-first, prefix order.</p>
     *
     * @param payloadType the type of payloads to retrieve
     * @return the payloads, never <code>null</code>
     */
    public <PAYLOADTYPE> Iterable<PAYLOADTYPE> descendants(final Class<PAYLOADTYPE> payloadType) {
        if (children == null) {
            return Collections.emptyList();
        }
        return new Iterable<PAYLOADTYPE>() {

            @Override
            public Iterator<PAYLOADTYPE> iterator() {
                return new PayloadIterator<>(TraceNode.this, payloadType);
            }
        };
    }

    /**
     * <p>Returns the payload of this <code>TraceNode</code>, or null if none.</p>
     *
     * @return the payload
     */
    public Object payload() {
        return payload;
    }

    /**
     * <p>Returns the timestamp of this <code>TraceNode</code>.</p>
     *
     * @return the timestamp
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * <p>Returns the parent <code>TraceNode</code> of this.</p>
     *
     * @return the parent
     */
    public TraceNode parent() {
        return parent;
    }

    /**
     * <p>Returns the child <code>TraceNodes</code> of this.</p>
     *
     * @return the children
     */
    public Iterable<TraceNode> children() {
        if (children == null) {
            return Collections.emptyList();
        }
        return children;
    }

    /**
     * <p>Returns whether or not this <code>TraceNode</code> is a root node (i.e. it has no parent).</p>
     *
     * @return <code>true</code> if {@link #parent()} returns <code>null</code>
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * <p>Returns the root <code>TraceNode</code> of the tree that this <code>TraceNode</code> belongs to.</p>
     *
     * @return the root
     */
    public TraceNode root() {
        TraceNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    /**
     * <p>Visits this <code>TraceNode</code> and all of its descendants in depth-first, prefix order.</p>
     *
     * @param visitor The visitor to accept.
     * @return The <code>visitor</code> parameter.
     */
    public <T extends TraceVisitor> T accept(T visitor) {
        visitor.visit(this);
        if (children == null || children.isEmpty()) {
            return visitor;
        }
        visitor.entering(this);
        for (TraceNode child : children) {
            child.accept(visitor);
        }
        visitor.leaving(this);
        return visitor;
    }

    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder("[ ");
        accept(new TraceVisitor() {

            @Override
            public void visit(TraceNode node) {
                if (node.payload != null) {
                    out.append(node.payload).append(" ");
                }
            }

            @Override
            public void entering(TraceNode node) {
                out.append("[ ");
            }

            @Override
            public void leaving(TraceNode node) {
                out.append("] ");
            }
        });
        return out.append("]").toString();
    }

    private static class PayloadIterator<PAYLOADTYPE> implements Iterator<PAYLOADTYPE> {

        final List<TraceNode> unexploredNodes = new LinkedList<>();
        final Class<PAYLOADTYPE> payloadType;
        PAYLOADTYPE next;

        PayloadIterator(TraceNode root, Class<PAYLOADTYPE> payloadType) {
            payloadType.getClass(); // throws NullPointerException
            this.payloadType = payloadType;
            unexploredNodes.add(root);
            next = advance();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public PAYLOADTYPE next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            PAYLOADTYPE current = next;
            next = advance();
            return current;
        }

        PAYLOADTYPE advance() {
            // Current node is depleted, find next
            while (unexploredNodes.size() > 0) {
                // Take the next node
                TraceNode node = unexploredNodes.remove(0);

                // Add its children to the list of nodes we1'll look at
                if (node.children != null) {
                    int i = 0; // used to fabricate depth-first traversal order
                    for (TraceNode child : node.children) {
                        unexploredNodes.add(i++, child);
                    }
                }

                Object payload = node.payload();
                if (payloadType.isInstance(payload)) {
                    return payloadType.cast(payload);
                }
            }
            return null;
        }
    }
}
