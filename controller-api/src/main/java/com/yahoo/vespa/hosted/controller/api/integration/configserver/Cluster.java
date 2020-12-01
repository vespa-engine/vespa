// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class Cluster {

    private final ClusterSpec.Id id;
    private final ClusterResources min;
    private final ClusterResources max;
    private final ClusterResources current;
    private final Optional<ClusterResources> target;
    private final Optional<ClusterResources> suggested;
    private final List<ScalingEvent> scalingEvents;
    private final String autoscalingStatus;

    public Cluster(ClusterSpec.Id id,
                   ClusterResources min,
                   ClusterResources max,
                   ClusterResources current,
                   Optional<ClusterResources> target,
                   Optional<ClusterResources> suggested,
                   List<ScalingEvent> scalingEvents,
                   String autoscalingStatus) {
        this.id = id;
        this.min = min;
        this.max = max;
        this.current = current;
        this.target = target;
        this.suggested = suggested;
        this.scalingEvents = scalingEvents;
        this.autoscalingStatus = autoscalingStatus;
    }

    public ClusterSpec.Id id() { return id; }
    public ClusterResources min() { return min; }
    public ClusterResources max() { return max; }
    public ClusterResources current() { return current; }
    public Optional<ClusterResources> target() { return target; }
    public Optional<ClusterResources> suggested() { return suggested; }
    public List<ScalingEvent> scalingEvents() { return scalingEvents; }
    public String autoscalingStatus() { return autoscalingStatus; }

    @Override
    public String toString() {
        return "cluster '" + id + "'";
    }

    public static class ScalingEvent {

        private final ClusterResources from, to;
        private final Instant at;

        public ScalingEvent(ClusterResources from, ClusterResources to, Instant at) {
            this.from = from;
            this.to = to;
            this.at = at;
        }

        public ClusterResources from() { return from; }
        public ClusterResources to() { return to; }
        public Instant at() { return at; }

    }

}
