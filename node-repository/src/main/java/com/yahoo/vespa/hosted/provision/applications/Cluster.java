// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.applications;

import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;

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
    private final IntRange groupSize;
    private final boolean required;
    private final Autoscaling suggested;
    private final Autoscaling target;
    private final BcpGroupInfo bcpGroupInfo;

    /** The maxScalingEvents last scaling events of this, sorted by increasing time (newest last) */
    private final List<ScalingEvent> scalingEvents;

    public Cluster(ClusterSpec.Id id,
                   boolean exclusive,
                   ClusterResources minResources,
                   ClusterResources maxResources,
                   IntRange groupSize,
                   boolean required,
                   Autoscaling suggested,
                   Autoscaling target,
                   BcpGroupInfo bcpGroupInfo,
                   List<ScalingEvent> scalingEvents) {
        this.id = Objects.requireNonNull(id);
        this.exclusive = exclusive;
        this.min = Objects.requireNonNull(minResources);
        this.max = Objects.requireNonNull(maxResources);
        this.groupSize = Objects.requireNonNull(groupSize);
        this.required = required;
        this.suggested = Objects.requireNonNull(suggested);
        Objects.requireNonNull(target);
        if (target.resources().isPresent() && ! target.resources().get().isWithin(minResources, maxResources))
            this.target = target.withResources(Optional.empty()); // Delete illegal target
        else
            this.target = target;
        this.bcpGroupInfo = Objects.requireNonNull(bcpGroupInfo);
        this.scalingEvents = List.copyOf(scalingEvents);
    }

    public ClusterSpec.Id id() { return id; }

    /** Returns whether the nodes allocated to this cluster must be on host exclusively dedicated to this application */
    public boolean exclusive() { return exclusive; }

    /** Returns the configured minimal resources in this cluster */
    public ClusterResources minResources() { return min; }

    /** Returns the configured maximal resources in this cluster */
    public ClusterResources maxResources() { return max; }

    /** Returns the configured group size range in this cluster */
    public IntRange groupSize() { return groupSize; }

    /**
     * Returns whether the resources of this cluster are required to be within the specified min and max.
     * Otherwise, they may be adjusted by capacity policies.
     */
    public boolean required() { return required; }

    /**
     * Returns the computed resources (between min and max, inclusive) this cluster should
     * have allocated at the moment (whether or not it actually has it),
     * or empty if the system currently has no target.
     */
    public Autoscaling target() { return target; }

    /**
     * The suggested resources, which may or may not be within the min and max limits,
     * or empty if there is currently no recorded suggestion.
     */
    public Autoscaling suggested() { return suggested; }

    /** Returns true if there is a current suggestion and we should actually make this suggestion to users. */
    public boolean shouldSuggestResources(ClusterResources currentResources) {
        if (suggested.resources().isEmpty()) return false;
        if (suggested.resources().get().isWithin(min, max)) return false;
        if ( ! Autoscaler.worthRescaling(currentResources, suggested.resources().get())) return false;
        return true;
    }

    /** Returns info about the BCP group of clusters this belongs to. */
    public BcpGroupInfo bcpGroupInfo() { return bcpGroupInfo; }

    /** Returns the recent scaling events in this cluster */
    public List<ScalingEvent> scalingEvents() { return scalingEvents; }

    public Optional<ScalingEvent> lastScalingEvent() {
        if (scalingEvents.isEmpty()) return Optional.empty();
        return Optional.of(scalingEvents.get(scalingEvents.size() - 1));
    }

    public Cluster withConfiguration(boolean exclusive, Capacity capacity) {
        return new Cluster(id, exclusive,
                           capacity.minResources(), capacity.maxResources(), capacity.groupSize(), capacity.isRequired(),
                           suggested, target, bcpGroupInfo, scalingEvents);
    }

    public Cluster withSuggested(Autoscaling suggested) {
        return new Cluster(id, exclusive, min, max, groupSize, required, suggested, target, bcpGroupInfo, scalingEvents);
    }

    public Cluster withTarget(Autoscaling target) {
        return new Cluster(id, exclusive, min, max, groupSize, required, suggested, target, bcpGroupInfo, scalingEvents);
    }

    public Cluster with(BcpGroupInfo bcpGroupInfo) {
        return new Cluster(id, exclusive, min, max, groupSize, required, suggested, target, bcpGroupInfo, scalingEvents);
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
        return new Cluster(id, exclusive, min, max, groupSize, required, suggested, target, bcpGroupInfo, scalingEvents);
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
    public String toString() { return id.toString(); }

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

    public static Cluster create(ClusterSpec.Id id, boolean exclusive, Capacity requested) {
        return new Cluster(id, exclusive,
                           requested.minResources(), requested.maxResources(), requested.groupSize(), requested.isRequired(),
                           Autoscaling.empty(), Autoscaling.empty(), BcpGroupInfo.empty(), List.of());
    }

    /** The predicted time it will take to rescale this cluster. */
    public Duration scalingDuration(ClusterSpec clusterSpec) {
        int completedEventCount = 0;
        Duration totalDuration = Duration.ZERO;
        for (ScalingEvent event : scalingEvents()) {
            if (event.duration().isEmpty()) continue;
            completedEventCount++;
            // Assume we have missed timely recording completion if it is longer than 4 days
            totalDuration = totalDuration.plus(maximum(Duration.ofDays(4), event.duration().get()));
        }
        if (completedEventCount == 0) { // Use defaults
            if (clusterSpec.isStateful()) return Duration.ofHours(12);
            return Duration.ofMinutes(10);
        }
        else {
            Duration predictedDuration = totalDuration.dividedBy(completedEventCount);

            if ( clusterSpec.isStateful() ) // TODO: Remove when we have reliable completion for content clusters
                predictedDuration = minimum(Duration.ofHours(12), predictedDuration);

            predictedDuration = minimum(Duration.ofMinutes(5), predictedDuration);

            return predictedDuration;
        }
    }

    private static Duration minimum(Duration smallestAllowed, Duration duration) {
        if (duration.minus(smallestAllowed).isNegative())
            return smallestAllowed;
        return duration;
    }

    private static Duration maximum(Duration largestAllowed, Duration duration) {
        if ( ! duration.minus(largestAllowed).isNegative())
            return largestAllowed;
        return duration;
    }

}
