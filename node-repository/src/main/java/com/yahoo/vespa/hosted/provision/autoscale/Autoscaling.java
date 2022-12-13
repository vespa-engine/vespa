package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * An autoscaling result.
 *
 * @author bratseth
 */
public class Autoscaling {

    private final Optional<ClusterResources> resources;
    private final Instant at;

    public Autoscaling(ClusterResources resources, Instant at) {
        this(Optional.of(resources), at);
    }

    public Autoscaling(Optional<ClusterResources> resources, Instant at) {
        this.resources = resources;
        this.at = at;
    }

    /** Returns the resource target of this, or empty if non target. */
    public Optional<ClusterResources> resources() {
        return resources;
    }

    /** Returns the time this target was decided. */
    public Instant at() { return at; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Autoscaling other)) return false;
        if ( ! this.at.equals(other.at)) return false;
        if ( ! this.resources.equals(other.resources)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, at);
    }

    @Override
    public String toString() {
        return "autoscaling to " + resources + ", made at " + at;
    }

    public static Autoscaling empty() { return new Autoscaling(Optional.empty(), Instant.EPOCH); }

}
