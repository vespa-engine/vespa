// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A node in a search cluster. This class is multithread safe.
 *
 * @author bratseth
 * @author ollivir
 */
public class Node {

    private final int key;
    private int pathIndex;
    private final String hostname;
    private final int fs4port;
    final int group;

    private final AtomicBoolean working = new AtomicBoolean(true);
    private final AtomicLong activeDocuments = new AtomicLong(0);

    public Node(int key, String hostname, int fs4port, int group) {
        this.key = key;
        this.hostname = hostname;
        this.fs4port = fs4port;
        this.group = group;
    }

    /** Returns the unique and stable distribution key of this node */
    public int key() { return key; }

    public int pathIndex() { return pathIndex; }

    void setPathIndex(int index) {
        pathIndex = index;
    }

    public String hostname() { return hostname; }

    public int fs4port() { return fs4port; }

    /** Returns the id of this group this node belongs to */
    public int group() { return group; }

    public void setWorking(boolean working) {
        this.working.lazySet(working);
    }

    /** Returns whether this node is currently responding to requests */
    public boolean isWorking() { return working.get(); }

    /** Updates the active documents on this node */
    void setActiveDocuments(long activeDocuments) {
        this.activeDocuments.set(activeDocuments);
    }

    /** Returns the active documents on this node. If unknown, 0 is returned. */
    public long getActiveDocuments() {
        return this.activeDocuments.get();
    }

    @Override
    public int hashCode() { return Objects.hash(hostname, fs4port); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Node)) return false;
        Node other = (Node)o;
        if ( ! Objects.equals(this.hostname, other.hostname)) return false;
        if ( ! Objects.equals(this.fs4port, other.fs4port)) return false;
        return true;
    }

    @Override
    public String toString() { return "search node " + hostname + ":" + fs4port + " in group " + group; }
}
