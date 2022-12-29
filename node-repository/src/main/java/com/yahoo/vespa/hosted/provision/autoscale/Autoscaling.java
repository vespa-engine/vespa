package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An autoscaling result.
 *
 * @author bratseth
 */
public class Autoscaling {

    private final Status status;
    private final String description;
    private final Optional<ClusterResources> resources;
    private final Instant at;

    public Autoscaling(Status status, String description, ClusterResources resources, Instant at) {
        this(status, description, Optional.of(resources), at);
    }

    public Autoscaling(Status status, String description, Optional<ClusterResources> resources, Instant at) {
        this.status = status;
        this.description = description;
        this.resources = resources;
        this.at = at;
    }

    /** Returns the resource target of this, or empty if non target. */
    public Optional<ClusterResources> resources() {
        return resources;
    }

    public Status status() { return status; }

    public String description() { return description; }

    /** Returns the time this target was decided. */
    public Instant at() { return at; }

    public Autoscaling with(Status status, String description) {
        return new Autoscaling(status, description, resources, at);
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Autoscaling other)) return false;
        if ( ! this.status.equals(other.status)) return false;
        if ( ! this.description.equals(other.description)) return false;
        if ( ! this.resources.equals(other.resources)) return false;
        if ( ! this.at.equals(other.at)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, description, at);
    }

    @Override
    public String toString() {
        return "autoscaling to " + resources + ", made at " + at;
    }

    public static Autoscaling empty() { return new Autoscaling(Status.unavailable, "", Optional.empty(), Instant.EPOCH); }

    public static Autoscaling dontScale(Status status, String description, Instant at) {
        return new Autoscaling(status, description, Optional.empty(), at);
    }

    public static Autoscaling ideal(Instant at) {
        return new Autoscaling(Status.ideal, "Cluster is ideally scaled within configured limits",
                               Optional.empty(), at);
    }

    public static Autoscaling scaleTo(ClusterResources target, Instant at) {
        return new Autoscaling(Status.rescaling,
                               "Rescaling initiated due to load changes",
                               Optional.of(target),
                               at);
    }

    public enum Status {

        /** No status is available: Aautoscaling is disabled, or a brand new application. */
        unavailable,

        /** Autoscaling is not taking any action at the moment due to recent changes or a lack of data */
        waiting,

        /** The cluster is ideally scaled to the current load */
        ideal,

        /** The cluster should be rescaled further, but no better configuration is allowed by the current limits */
        insufficient,

        /** Rescaling of this cluster has been scheduled */
        rescaling

    };

}
