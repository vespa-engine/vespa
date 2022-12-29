package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.vespa.hosted.provision.applications.AutoscalingStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An autoscaling result.
 *
 * @author bratseth
 */
public class Autoscaling {

    private final Optional<ClusterResources> resources;
    private final AutoscalingStatus status;
    private final Instant at;

    public Autoscaling(ClusterResources resources, AutoscalingStatus status, Instant at) {
        this(Optional.of(resources), status, at);
    }

    public Autoscaling(Optional<ClusterResources> resources, AutoscalingStatus status, Instant at) {
        this.resources = resources;
        this.status = status;
        this.at = at;
    }

    /** Returns the resource target of this, or empty if non target. */
    public Optional<ClusterResources> resources() {
        return resources;
    }

    public AutoscalingStatus status() { return status; }

    /** Returns the time this target was decided. */
    public Instant at() { return at; }

    public Autoscaling with(AutoscalingStatus status) {
        return new Autoscaling(resources, status, at);
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Autoscaling other)) return false;
        if ( ! this.resources.equals(other.resources)) return false;
        if ( ! this.status.equals(other.status)) return false;
        if ( ! this.at.equals(other.at)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, status, at);
    }

    @Override
    public String toString() {
        return "autoscaling to " + resources + ", made at " + at;
    }

    public static Autoscaling empty() { return new Autoscaling(Optional.empty(), AutoscalingStatus.empty(), Instant.EPOCH); }

}
