// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import java.util.Objects;

/**
 * The current autoscaling status of a cluster.
 * A value object.
 *
 * @author bratseth
 */
public class AutoscalingStatus {

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

    private final Status status;
    private final String description;

    public AutoscalingStatus(Status status, String description) {
        this.status = status;
        this.description = description;
    }

    public Status status() { return status; }
    public String description() { return description; }

    public static AutoscalingStatus empty() { return new AutoscalingStatus(Status.unavailable, ""); }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! ( o instanceof AutoscalingStatus other)) return false;

        if ( other.status != this.status ) return false;
        if ( ! other.description.equals(this.description) ) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, description);
    }

    @Override
    public String toString() {
        return "autoscaling status: " + status +
               ( description.isEmpty() ? "" : " (" + description + ")");
    }

}
