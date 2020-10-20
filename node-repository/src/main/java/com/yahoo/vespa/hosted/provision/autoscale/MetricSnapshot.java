// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Instant;

/**
 * A single measurement of all values we measure for one node.
 *
 * @author bratseth
 */
public class MetricSnapshot {

    // TODO: Order by timestamp
    /** The time of this measurement in epoch millis */
    private final long timestamp;

    private final double cpu;
    private final double memory;
    private final double disk;
    private final long generation;

    public MetricSnapshot(MetricsFetcher.NodeMetrics metrics) {
        this.timestamp = metrics.timestampSecond() * 1000;
        this.cpu = Metric.cpu.measurementFromMetric(metrics.cpuUtil());
        this.memory = Metric.memory.measurementFromMetric(metrics.totalMemUtil());
        this.disk = Metric.disk.measurementFromMetric(metrics.diskUtil());
        this.generation = (long)Metric.generation.measurementFromMetric(metrics.applicationGeneration());

    }

    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }
    public long generation() { return generation; }
    public Instant at() { return Instant.ofEpochMilli(timestamp); }

    @Override
    public String toString() { return "metrics at " + timestamp + ": " +
                                      "cpu: " + cpu +
                                      "memory: " + memory +
                                      "disk: " + disk +
                                      "generation: " + generation; }

}
