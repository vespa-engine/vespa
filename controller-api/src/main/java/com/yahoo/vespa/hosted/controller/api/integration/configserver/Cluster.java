// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @author bratseth
 */
public class Cluster {

    private final ClusterSpec.Id id;
    private final ClusterSpec.Type type;
    private final ClusterResources min;
    private final ClusterResources max;
    private final ClusterResources current;
    private final Optional<ClusterResources> target;
    private final Optional<ClusterResources> suggested;
    private final Utilization utilization;
    private final List<ScalingEvent> scalingEvents;
    private final String autoscalingStatus;
    private final Duration scalingDuration;
    private final double maxQueryGrowthRate;
    private final double currentQueryFractionOfMax;


    public Cluster(ClusterSpec.Id id,
                   ClusterSpec.Type type,
                   ClusterResources min,
                   ClusterResources max,
                   ClusterResources current,
                   Optional<ClusterResources> target,
                   Optional<ClusterResources> suggested,
                   Utilization utilization,
                   List<ScalingEvent> scalingEvents,
                   String autoscalingStatus,
                   Duration scalingDuration,
                   double maxQueryGrowthRate,
                   double currentQueryFractionOfMax) {
        this.id = id;
        this.type = type;
        this.min = min;
        this.max = max;
        this.current = current;
        this.target = target;
        this.suggested = suggested;
        this.utilization = utilization;
        this.scalingEvents = scalingEvents;
        this.autoscalingStatus = autoscalingStatus;
        this.scalingDuration = scalingDuration;
        this.maxQueryGrowthRate = maxQueryGrowthRate;
        this.currentQueryFractionOfMax = currentQueryFractionOfMax;
    }

    public ClusterSpec.Id id() { return id; }
    public ClusterSpec.Type type() { return type; }
    public ClusterResources min() { return min; }
    public ClusterResources max() { return max; }
    public ClusterResources current() { return current; }
    public Optional<ClusterResources> target() { return target; }
    public Optional<ClusterResources> suggested() { return suggested; }
    public Utilization utilization() { return utilization; }
    public List<ScalingEvent> scalingEvents() { return scalingEvents; }
    public String autoscalingStatus() { return autoscalingStatus; }
    public Duration scalingDuration() { return scalingDuration; }
    public double maxQueryGrowthRate() { return maxQueryGrowthRate; }
    public double currentQueryFractionOfMax() { return currentQueryFractionOfMax; }

    @Override
    public String toString() {
        return "cluster '" + id + "'";
    }

    public static class Utilization {

        private final double cpu, idealCpu, memory, idealMemory, disk, idealDisk;

        public Utilization(double cpu, double idealCpu,
                           double memory, double idealMemory,
                           double disk, double idealDisk) {
            this.cpu = cpu;
            this.idealCpu = idealCpu;
            this.memory = memory;
            this.idealMemory = idealMemory;
            this.disk = disk;
            this.idealDisk = idealDisk;
        }

        public double cpu() { return cpu; }
        public double idealCpu() { return idealCpu; }

        public double memory() { return memory; }
        public double idealMemory() { return idealMemory; }

        public double disk() { return disk; }
        public double idealDisk() { return idealDisk; }

        public static Utilization empty() { return new Utilization(0, 0, 0, 0, 0, 0); }

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
