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
    private final int group;

    private final AtomicBoolean statusIsKnown = new AtomicBoolean(false);
    private final AtomicBoolean working = new AtomicBoolean(true);
    private final AtomicLong activeDocuments = new AtomicLong(0);
    private final AtomicBoolean pendingPing = new AtomicBoolean();

    public Node(int key, String hostname, int group) {
        this.key = key;
        this.hostname = hostname;
        this.group = group;
    }

    public boolean sendPing() { return ! pendingPing.getAndSet(true); }
    public void receivePing() { pendingPing.set(false); }

    /** Returns the unique and stable distribution key of this node */
    public int key() { return key; }

    public int pathIndex() { return pathIndex; }

    void setPathIndex(int index) {
        pathIndex = index;
    }

    public String hostname() { return hostname; }

    /** Returns the id of this group this node belongs to */
    public int group() { return group; }

    public void setWorking(boolean working) {
        this.statusIsKnown.lazySet(true);
        this.working.lazySet(working);
        if ( ! working ) {
            activeDocuments.set(0);
        }
    }

    /** Returns whether this node is currently responding to requests, or null if status is not known */
    public Boolean isWorking() {
        return statusIsKnown.get() ? working.get() : null;
    }

    /** Updates the active documents on this node */
    void setActiveDocuments(long activeDocuments) {
        this.activeDocuments.set(activeDocuments);
    }

    /** Returns the active documents on this node. If unknown, 0 is returned. */
    long getActiveDocuments() {
        return activeDocuments.get();
    }

    @Override
    public int hashCode() { return Objects.hash(hostname, key, pathIndex, group); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Node)) return false;
        Node other = (Node)o;
        if ( ! Objects.equals(this.hostname, other.hostname)) return false;
        if ( ! Objects.equals(this.key, other.key)) return false;
        if ( ! Objects.equals(this.pathIndex, other.pathIndex)) return false;
        if ( ! Objects.equals(this.group, other.group)) return false;

        return true;
    }

    @Override
    public String toString() {
        return "search node key = " + key + " hostname = "+ hostname + " path = " + pathIndex + " in group " + group +
               " statusIsKnown = " + statusIsKnown.get() + " working = " + working.get() +
               " activeDocs = " + activeDocuments.get();
    }

}
