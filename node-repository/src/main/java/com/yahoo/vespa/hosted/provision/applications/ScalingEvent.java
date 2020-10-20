// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;

import java.time.Instant;
import java.util.Objects;

/**
 * A recording of a change in resources for an application cluster
 *
 * @author bratseth
 */
public class ScalingEvent {

    private final ClusterResources from, to;
    private final long generation;
    private final Instant at;

    public ScalingEvent(ClusterResources from, ClusterResources to, long generation, Instant at) {
        this.from = from;
        this.to = to;
        this.generation = generation;
        this.at = at;
    }

    /** Returns the resources we changed from */
    public ClusterResources from() { return from; }

    /** Returns the resources we changed to */
    public ClusterResources to() { return to; }

    /** Returns the application config generation resulting from this deployment */
    public long generation() { return generation; }

    /** Returns the time of this deployment */
    public Instant at() { return at; }

    @Override
    public int hashCode() { return Objects.hash(from, to, generation, at); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ScalingEvent)) return true;
        ScalingEvent other = (ScalingEvent)o;
        if ( other.generation != this.generation) return false;
        if ( ! other.at.equals(this.at)) return false;
        if ( ! other.from.equals(this.from)) return false;
        if ( ! other.to.equals(this.to)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "scaling event from " + from + " to " + to + ", generation " + generation + " at " + at;
    }

}
