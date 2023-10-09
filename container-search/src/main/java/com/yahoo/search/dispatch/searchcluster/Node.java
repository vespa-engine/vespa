// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A node in a search cluster. This class is multithread safe.
 *
 * @author bratseth
 * @author ollivir
 */
public class Node {

    private final String clusterName;
    private final int key;
    private final String hostname;
    private final int group;
    private int pathIndex;

    private final AtomicLong pingSequence = new AtomicLong(0);
    private final AtomicLong lastPong = new AtomicLong(0);
    private volatile long activeDocuments = 0;
    private volatile long targetActiveDocuments = 0;
    private volatile boolean statusIsKnown = false;
    private volatile boolean working = true;
    private volatile boolean isBlockingWrites = false;

    public Node(String clusterName, int key, String hostname, int group) {
        this.clusterName = clusterName;
        this.key = key;
        this.hostname = hostname;
        this.group = group;
    }

    /** Give a monotonically increasing sequence number.*/
    public long createPingSequenceId() { return pingSequence.incrementAndGet(); }
    /** Checks if this pong is received in line and accepted, or out of band and should be ignored. */
    public boolean isLastReceivedPong(long pingId ) {
        long last = lastPong.get();
        while ((pingId > last) && ! lastPong.compareAndSet(last, pingId)) {
            last = lastPong.get();
        }
        return last < pingId;
    }
    public long getLastReceivedPongId() { return lastPong.get(); }

    /** Returns the unique and stable distribution key of this node */
    public int key() { return key; }

    public int pathIndex() { return pathIndex; }

    void setPathIndex(int index) {
        pathIndex = index;
    }

    public String hostname() { return hostname; }

    /**
     * Returns the index of the group this node belongs to.
     * This is a 0-base continuous integer id, not necessarily the same as the group id assigned by the
     * application/node repo.
     */
    public int group() { return group; }

    public void setWorking(boolean working) {
        this.statusIsKnown = true;
        this.working = working;
        if ( ! working ) {
            activeDocuments = 0;
            targetActiveDocuments = 0;
        }
    }

    /** Returns whether this node is currently responding to requests, or null if status is not known */
    public Boolean isWorking() {
        return statusIsKnown ? working : null;
    }

    /** Updates the active documents on this node */
    public void setActiveDocuments(long documents) { this.activeDocuments = documents; }
    public void setTargetActiveDocuments(long documents) { this.targetActiveDocuments = documents; }

    /** Returns the active documents on this node. If unknown, 0 is returned. */
    long getActiveDocuments() { return activeDocuments; }
    long getTargetActiveDocuments() { return targetActiveDocuments; }

    public void setBlockingWrites(boolean isBlockingWrites) { this.isBlockingWrites = isBlockingWrites; }

    boolean isBlockingWrites() { return isBlockingWrites; }

    @Override
    public int hashCode() { return Objects.hash(hostname, key, group); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Node other)) return false;
        if ( ! Objects.equals(this.hostname, other.hostname)) return false;
        if ( ! Objects.equals(this.key, other.key)) return false;
        if ( ! Objects.equals(this.group, other.group)) return false;

        return true;
    }

    @Override
    public String toString() {
        return "search node in cluster = " + clusterName + " key = " + key + " hostname = "+ hostname +
               " path = " + pathIndex + " in group " + group + " statusIsKnown = " + statusIsKnown + " working = " + working +
               " activeDocs = " + getActiveDocuments() + " targetActiveDocs = " + getTargetActiveDocuments();
    }

}
