package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ClusterResources;
import io.questdb.Metrics;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An autoscaling conclusion and the context that led to it.
 *
 * @author bratseth
 */
public class Autoscaling {

    private final Status status;
    private final String description;
    private final Optional<ClusterResources> resources;
    private final Instant at;
    private final Load peak;
    private final Load ideal;
    private final Metrics metrics;

    public Autoscaling(Status status, String description, Optional<ClusterResources> resources, Instant at,
                       Load peak, Load ideal, Metrics metrics) {
        this.status = status;
        this.description = description;
        this.resources = resources;
        this.at = at;
        this.peak = peak;
        this.ideal = ideal;
        this.metrics = metrics;
    }

    public Status status() { return status; }

    public String description() { return description; }

    /** Returns the resource target of this, or empty if none (meaning keep the current allocation). */
    public Optional<ClusterResources> resources() {
        return resources;
    }

    /** Returns the time this was decided. */
    public Instant at() { return at; }

    /** Returns the peak load seen in the period considered in this. */
    public Load peak() { return peak; }

    /** Returns the ideal load the cluster in question should have. */
    public Load ideal() { return ideal; }

    public Metrics metrics() { return metrics; }

    public Autoscaling with(Status status, String description) {
        return new Autoscaling(status, description, resources, at, peak, ideal, metrics);
    }

    public Autoscaling withResources(Optional<ClusterResources> resources) {
        return new Autoscaling(status, description, resources, at, peak, ideal, metrics);
    }

    /** Converts this autoscaling into an ideal one at the completion of it. */
    public Autoscaling asIdeal(Instant at) {
        return new Autoscaling(Status.ideal,
                               "Cluster is ideally scaled within configured limits",
                               Optional.empty(),
                               at,
                               peak,
                               ideal,
                               metrics);
    }

    /** Returns true if no measurements was used in creating this instance. */
    public boolean isEmpty() {
        return peak.equals(Load.zero());
    }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Autoscaling other)) return false;
        if ( ! this.status.equals(other.status)) return false;
        if ( ! this.description.equals(other.description)) return false;
        if ( ! this.resources.equals(other.resources)) return false;
        if ( ! this.at.equals(other.at)) return false;
        if ( ! this.peak.equals(other.peak)) return false;
        if ( ! this.ideal.equals(other.ideal)) return false;
        if ( ! this.metrics.equals(other.metrics)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, description, at, peak, ideal, metrics);
    }

    @Override
    public String toString() {
        return (resources.isPresent() ? "Autoscaling to " + resources : "Don't autoscale") +
               (description.isEmpty() ? "" : ": " + description);
    }

    public static Autoscaling empty() {
        return new Autoscaling(Status.unavailable,
                               "",
                               Optional.empty(),
                               Instant.EPOCH,
                               Load.zero(),
                               Load.zero(),
                               Metrics.zero());
    }

    /** Creates an autoscaling conclusion which does not change the current allocation for a specified reason. */
    public static Autoscaling dontScale(Status status, String description, ClusterModel clusterModel) {
        return new Autoscaling(status,
                               description,
                               Optional.empty(),
                               clusterModel.at(),
                               clusterModel.peakLoad(),
                               clusterModel.idealLoad(),
                               clusterModel.metrics());
    }

    /** Creates an autoscaling conclusion to scale. */
    public static Autoscaling scaleTo(ClusterResources target, ClusterModel clusterModel) {
        return new Autoscaling(Status.rescaling,
                               "Rescaling initiated due to load changes",
                               Optional.of(target),
                               clusterModel.at(),
                               clusterModel.peakLoad(),
                               clusterModel.idealLoad(),
                               clusterModel.metrics());
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

    }

    // Used to create BcpGroupInfo
    public record Metrics(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {

        public static Metrics zero() {
            return new Metrics(0, 0, 0);
        }

    }

}
