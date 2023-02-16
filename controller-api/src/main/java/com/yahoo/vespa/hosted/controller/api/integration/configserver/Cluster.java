// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.IntRange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author bratseth
 */
public class Cluster {

    private final ClusterSpec.Id id;
    private final ClusterSpec.Type type;
    private final ClusterResources min;
    private final ClusterResources max;
    private final IntRange groupSize;
    private final ClusterResources current;
    private final Autoscaling target;
    private final Autoscaling suggested;
    private final List<ScalingEvent> scalingEvents;
    private final Duration scalingDuration;

    public Cluster(ClusterSpec.Id id,
                   ClusterSpec.Type type,
                   ClusterResources min,
                   ClusterResources max,
                   IntRange groupSize,
                   ClusterResources current,
                   Autoscaling target,
                   Autoscaling suggested,
                   List<ScalingEvent> scalingEvents,
                   Duration scalingDuration) {
        this.id = id;
        this.type = type;
        this.min = min;
        this.max = max;
        this.groupSize = groupSize;
        this.current = current;
        this.target = target;
        this.suggested = suggested;
        this.scalingEvents = scalingEvents;
        this.scalingDuration = scalingDuration;
    }

    public ClusterSpec.Id id() { return id; }

    public ClusterSpec.Type type() { return type; }

    public ClusterResources min() { return min; }

    public ClusterResources max() { return max; }

    public IntRange groupSize() { return groupSize; }

    public ClusterResources current() { return current; }

    public Autoscaling target() { return target; }

    public Autoscaling suggested() { return suggested; }

    public List<ScalingEvent> scalingEvents() { return scalingEvents; }

    public Duration scalingDuration() { return scalingDuration; }

    @Override
    public String toString() {
        return id.toString();
    }

    public static class ScalingEvent {

        private final ClusterResources from, to;
        private final Instant at;
        private final Optional<Instant> completion;

        public ScalingEvent(ClusterResources from, ClusterResources to, Instant at, Optional<Instant> completion) {
            this.from = from;
            this.to = to;
            this.at = at;
            this.completion = completion;
        }

        public ClusterResources from() {return from;}

        public ClusterResources to() {return to;}

        public Instant at() {return at;}

        public Optional<Instant> completion() {return completion;}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ScalingEvent that = (ScalingEvent) o;
            return Objects.equals(from, that.from) && Objects.equals(to, that.to) && Objects.equals(at, that.at) && Objects.equals(completion, that.completion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, at, completion);
        }

        @Override
        public String toString() {
            return "ScalingEvent{" +
                   "from=" + from +
                   ", to=" + to +
                   ", at=" + at +
                   ", completion=" + completion +
                   '}';
        }
    }

    public static class Autoscaling {

        private final String status;
        private final String description;
        private final Optional<ClusterResources> resources;
        private final Instant at;
        private final Load peak;
        private final Load ideal;
        private final Metrics metrics;

        public Autoscaling(String status, String description, Optional<ClusterResources> resources, Instant at,
                           Load peak, Load ideal, Metrics metrics) {
            this.status = status;
            this.description = description;
            this.resources = resources;
            this.at = at;
            this.peak = peak;
            this.ideal = ideal;
            this.metrics = metrics;
        }

        public String status() {return status;}
        public String description() {return description;}
        public Optional<ClusterResources> resources() {
            return resources;
        }
        public Instant at() {return at;}
        public Load peak() {return peak;}
        public Load ideal() {return ideal;}
        public Metrics metrics() { return metrics; }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Autoscaling other)) return false;
            if (!this.status.equals(other.status)) return false;
            if (!this.description.equals(other.description)) return false;
            if (!this.resources.equals(other.resources)) return false;
            if (!this.at.equals(other.at)) return false;
            if (!this.peak.equals(other.peak)) return false;
            if (!this.ideal.equals(other.ideal)) return false;
            if (!this.metrics.equals(other.metrics)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, description, at, peak, ideal);
        }

        @Override
        public String toString() {
            return (resources.isPresent() ? "Autoscaling to " + resources : "Don't autoscale") +
                   (description.isEmpty() ? "" : ": " + description);
        }

        public static Autoscaling empty() {
            return new Autoscaling("unavailable",
                                   "",
                                   Optional.empty(),
                                   Instant.EPOCH,
                                   Load.zero(),
                                   Load.zero(),
                                   Metrics.zero());
        }

        // Used to create BcpGroupInfo
        public record Metrics(double queryRate, double growthRateHeadroom, double cpuCostPerQuery) {

            public static Metrics zero() {
                return new Metrics(0, 0, 0);
            }

        }

    }

}
