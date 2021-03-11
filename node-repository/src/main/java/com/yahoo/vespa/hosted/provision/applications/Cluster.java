// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The node repo's view of a cluster in an application deployment.
 *
 * This is immutable, and must be locked with the application lock on read-modify-write.
 *
 * @author bratseth
 */
public class Cluster {

    public static final int maxScalingEvents = 15;

    private final ClusterSpec.Id id;
    private final boolean exclusive;
    private final ClusterResources min, max;
    private final Optional<Suggestion> suggested;
    private final Optional<ClusterResources> target;

    /** The maxScalingEvents last scaling events of this, sorted by increasing time (newest last) */
    private final List<ScalingEvent> scalingEvents;
    private final String autoscalingStatus;

    public Cluster(ClusterSpec.Id id,
                   boolean exclusive,
                   ClusterResources minResources,
                   ClusterResources maxResources,
                   Optional<Suggestion> suggestedResources,
                   Optional<ClusterResources> targetResources,
                   List<ScalingEvent> scalingEvents,
                   String autoscalingStatus) {
        this.id = Objects.requireNonNull(id);
        this.exclusive = exclusive;
        this.min = Objects.requireNonNull(minResources);
        this.max = Objects.requireNonNull(maxResources);
        this.suggested = Objects.requireNonNull(suggestedResources);
        Objects.requireNonNull(targetResources);
        if (targetResources.isPresent() && ! targetResources.get().isWithin(minResources, maxResources))
            this.target = Optional.empty();
        else
            this.target = targetResources;
        this.scalingEvents = List.copyOf(scalingEvents);
        this.autoscalingStatus = autoscalingStatus;
    }

    public ClusterSpec.Id id() { return id; }

    /** Returns the configured minimal resources in this cluster */
    public ClusterResources minResources() { return min; }

    /** Returns the configured maximal resources in this cluster */
    public ClusterResources maxResources() { return max; }

    /** Returns whether the nodes allocated to this cluster must be on host exclusively dedicated to this application */
    public boolean exclusive() { return exclusive; }

    /**
     * Returns the computed resources (between min and max, inclusive) this cluster should
     * have allocated at the moment (whether or not it actually has it),
     * or empty if the system currently has no target.
     */
    public Optional<ClusterResources> targetResources() { return target; }

    /**
     * The suggested size of this cluster, which may or may not be within the min and max limits,
     * or empty if there is currently no recorded suggestion.
     */
    public Optional<Suggestion> suggestedResources() { return suggested; }

    /** Returns true if there is a current suggestion and we should actually make this suggestion to users. */
    public boolean shouldSuggestResources(ClusterResources currentResources) {
        if (suggested.isEmpty()) return false;
        if (suggested.get().resources().isWithin(min, max)) return false;
        if (Autoscaler.similar(suggested.get().resources(), currentResources)) return false;
        return true;
    }

    /** Returns the recent scaling events in this cluster */
    public List<ScalingEvent> scalingEvents() { return scalingEvents; }

    public Optional<ScalingEvent> lastScalingEvent() {
        if (scalingEvents.isEmpty()) return Optional.empty();
        return Optional.of(scalingEvents.get(scalingEvents.size() - 1));
    }

    /** The latest autoscaling status of this cluster, or empty (never null) if none */
    public String autoscalingStatus() { return autoscalingStatus; }

    public Cluster withConfiguration(boolean exclusive, ClusterResources min, ClusterResources max) {
        return new Cluster(id, exclusive, min, max, suggested, target, scalingEvents, autoscalingStatus);
    }

    public Cluster withSuggested(Optional<Suggestion> suggested) {
        return new Cluster(id, exclusive, min, max, suggested, target, scalingEvents, autoscalingStatus);
    }

    public Cluster withTarget(Optional<ClusterResources> target) {
        return new Cluster(id, exclusive, min, max, suggested, target, scalingEvents, autoscalingStatus);
    }

    /** Add or update (based on "at" time) a scaling event */
    public Cluster with(ScalingEvent scalingEvent) {
        List<ScalingEvent> scalingEvents = new ArrayList<>(this.scalingEvents);

        int existingIndex = eventIndexAt(scalingEvent.at());
        if (existingIndex >= 0)
            scalingEvents.set(existingIndex, scalingEvent);
        else
            scalingEvents.add(scalingEvent);

        prune(scalingEvents);
        return new Cluster(id, exclusive, min, max, suggested, target, scalingEvents, autoscalingStatus);
    }

    public Cluster withAutoscalingStatus(String autoscalingStatus) {
        if (autoscalingStatus.equals(this.autoscalingStatus)) return this;
        return new Cluster(id, exclusive, min, max, suggested, target, scalingEvents, autoscalingStatus);
    }

    /** The predicted duration of a rescaling of this cluster */
    public Duration scalingDuration(ClusterSpec clusterSpec) {
        int completedEventCount = 0;
        Duration totalDuration = Duration.ZERO;
        for (ScalingEvent event : scalingEvents()) {
            if (event.duration().isEmpty()) continue;
            completedEventCount++;
            totalDuration = totalDuration.plus(event.duration().get());
        }

        if (completedEventCount == 0) { // Use defaults
            if (clusterSpec.isStateful()) return Duration.ofHours(12);
            return Duration.ofMinutes(10);
        }
        else {
            Duration predictedDuration = totalDuration.dividedBy(completedEventCount);

            // TODO: Remove when we have reliable completion for content clusters
            if (clusterSpec.isStateful() && predictedDuration.minus(Duration.ofHours(12)).isNegative())
                return Duration.ofHours(12);

            if (predictedDuration.minus(Duration.ofMinutes(5)).isNegative()) return Duration.ofMinutes(5); // minimum
            return predictedDuration;
        }
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if ( ! (other instanceof Cluster)) return false;
        return ((Cluster)other).id().equals(this.id);
    }

    @Override
    public String toString() {
        return "cluster '" + id + "'";
    }

    private void prune(List<ScalingEvent> scalingEvents) {
        while (scalingEvents.size() > maxScalingEvents)
            scalingEvents.remove(0);
    }

    private int eventIndexAt(Instant at) {
        for (int i = 0; i < scalingEvents.size(); i++) {
            if (scalingEvents.get(i).at().equals(at))
                return i;
        }
        return -1;
    }

    public static class Suggestion {

        private final ClusterResources resources;
        private final Instant at;

        public Suggestion(ClusterResources resources, Instant at) {
            this.resources = resources;
            this.at = at;
        }

        /** Returns the suggested resources */
        public ClusterResources resources() { return resources; }

        /** Returns the instant this suggestion was made */
        public Instant at() { return at; }

        @Override
        public String toString() {
            return "suggestion made at " + at + ": " + resources;
        }

        @Override
        public int hashCode() {
            return Objects.hash(resources, at);
        }

        @Override
        public boolean equals(Object o) {
            if ( ! (o instanceof Suggestion)) return false;
            Suggestion other = (Suggestion)o;
            if ( ! this.at.equals(other.at)) return false;
            if ( ! this.resources.equals(other.resources)) return false;
            return true;
        }

    }

}
