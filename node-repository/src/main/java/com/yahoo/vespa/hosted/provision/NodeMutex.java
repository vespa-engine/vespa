// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision;

import com.yahoo.transaction.Mutex;

/**
 * Holds a node and mutex pair, with the mutex closed by {@link #close()}.
 *
 * @author hakon
 */
public class NodeMutex implements Mutex {
    private final Node node;
    private final Mutex mutex;

    public NodeMutex(Node node, Mutex mutex) {
        this.node = node;
        this.mutex = mutex;
    }

    public Node node() { return node; }
    @Override public void close() { mutex.close(); }

    /** Returns a node mutex with the same mutex as this, but the given node.  Be sure to close only one. */
    public NodeMutex with(Node updatedNode) {
        if (!node.equals(updatedNode)) {
            throw new IllegalArgumentException("Updated node not equal to current");
        }

        return new NodeMutex(updatedNode, mutex);
    }
}
