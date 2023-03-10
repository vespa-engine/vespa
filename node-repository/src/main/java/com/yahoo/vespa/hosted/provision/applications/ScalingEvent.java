// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A recording of a change in resources for an application cluster
 *
 * @author bratseth
 */
public class ScalingEvent {

    private final ClusterResources from, to;
    private final long generation;
    private final Instant at;
    private final Optional<Instant> completion;

    /** Do not use */
    public ScalingEvent(ClusterResources from,
                        ClusterResources to,
                        long generation,
                        Instant at,
                        Optional<Instant> completion) {
        this.from = from;
        this.to = to;
        this.generation = generation;
        this.at = at;
        this.completion = completion;
    }

    /** Returns the resources we changed from */
    public ClusterResources from() { return from; }

    /** Returns the resources we changed to */
    public ClusterResources to() { return to; }

    /** Returns the application config generation resulting from this deployment */
    public long generation() { return generation; }

    /** Returns the time of this deployment */
    public Instant at() { return at; }

    /** Returns the instant this completed, or empty if it is not yet complete as far as we know */
    public Optional<Instant> completion() { return completion; }

    /** Returns the time this event took to completion, or empty if it's not yet complete */
    public Optional<Duration> duration() {
        if (completion.isEmpty()) return Optional.empty();
        return Optional.of(Duration.between(at, completion.get()));
    }

    public ScalingEvent withCompletion(Instant completion) {
        return new ScalingEvent(from, to, generation, at, Optional.of(completion));
    }

    @Override
    public int hashCode() { return Objects.hash(from, to, generation, at); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ScalingEvent other)) return true;
        if ( other.generation != this.generation) return false;
        if ( ! other.at.equals(this.at)) return false;
        if ( ! other.from.equals(this.from)) return false;
        if ( ! other.to.equals(this.to)) return false;
        if ( ! other.completion.equals(this.completion)) return false;
        return true;
    }

    @Override
    public String toString() {
        return "scaling event from " + from + " to " + to + ", generation " + generation + " at " + at +
               (completion.isPresent() ? " completed " + completion.get() : "");
    }

    public static ScalingEvent create(ClusterResources from, ClusterResources to, long generation, Instant at) {
        return new ScalingEvent(from, to, generation, at, Optional.empty());
    }

}
