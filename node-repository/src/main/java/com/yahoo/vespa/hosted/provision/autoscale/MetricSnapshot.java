// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import java.time.Instant;

/**
 * A single measurement of all values we measure for one node.
 *
 * @author bratseth
 */
public class MetricSnapshot implements Comparable<MetricSnapshot> {

    private final Instant at;

    private final double cpu;
    private final double memory;
    private final double disk;
    private final long generation;
    private final boolean inService;
    private final boolean stable;

    public MetricSnapshot(Instant at, double cpu, double memory, double disk, long generation,
                          boolean inService, boolean stable) {
        this.at = at;
        this.cpu = cpu;
        this.memory = memory;
        this.disk = disk;
        this.generation = generation;
        this.inService = inService;
        this.stable = stable;
    }

    public Instant at() { return at; }
    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }

    /** The configuration generation at the time of this measurement, or -1 if not known */
    public long generation() { return generation; }

    public boolean inService() { return inService; }
    public boolean stable() { return stable; }

    @Override
    public int compareTo(MetricSnapshot other) {
        return at.compareTo(other.at);
    }

    @Override
    public String toString() { return "metrics at " + at + ":" +
                                      " cpu: " + cpu +
                                      " memory: " + memory +
                                      " disk: " + disk +
                                      " generation: " + generation +
                                      " inService: " + inService +
                                      " stable: " + stable;
    }

}
