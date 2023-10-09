// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.node;

/**
 * An immutable generation, with wanted and current generation fields.  Wanted generation
 * is increased when an action (restart services or reboot are the available
 * actions) is wanted, current is updated when the action has been done on the node.
 *
 * @author hmusum
 */
public class Generation {

    private final long wanted;
    private final long current;

    public Generation(long wanted, long current) {
        this.wanted = wanted;
        this.current = current;
    }

    public long wanted() {
        return wanted;
    }

    public long current() {
        return current;
    }

    public boolean pending() {
        return current < wanted;
    }

    public Generation withIncreasedWanted() {
        return new Generation(wanted + 1, current);
    }

    public Generation withCurrent(long newValue) {
        return new Generation(wanted, newValue);
    }

    @Override
    public String toString() {
        return "current generation: " + current + ", wanted: " + wanted;
    }

    /** Returns the initial generation (0, 0) */
    public static Generation initial() { return new Generation(0, 0); }

}
