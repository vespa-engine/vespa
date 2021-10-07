// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.NodeResources;

/**
 * The load of a node or system, measured as fractions of max (1.0) in three dimensions.
 *
 * @author bratseth
 */
public class Load {

    private final double cpu, memory, disk;

    public Load(double cpu, double memory, double disk) {
        this.cpu = requireNormalized(cpu, "cpu");
        this.memory = requireNormalized(memory, "memory");
        this.disk = requireNormalized(disk, "disk");
    }

    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }

    public Load add(Load other) {
        return new Load(cpu + other.cpu(), memory + other.memory(), disk + other.disk());
    }

    public Load multiply(NodeResources resources) {
        return new Load(cpu * resources.vcpu(), memory * resources.memoryGb(), disk * resources.diskGb());
    }

    public Load multiply(double factor) {
        return new Load(cpu * factor, memory * factor, disk * factor);
    }

    public Load divide(NodeResources resources) {
        return new Load(divide(cpu, resources.vcpu()), divide(memory, resources.memoryGb()), divide(disk, resources.diskGb()));
    }

    public Load divide(Load divisor) {
        return new Load(divide(cpu, divisor.cpu()), divide(memory, divisor.memory()), divide(disk, divisor.disk()));
    }

    public Load divide(double divisor) {
        return new Load(divide(cpu, divisor), divide(memory, divisor), divide(disk, divisor));
    }

    public NodeResources scaled(NodeResources resources) {
        return resources.withVcpu(cpu * resources.vcpu())
                        .withMemoryGb(memory * resources.memoryGb())
                        .withDiskGb(disk * resources.diskGb());
    }

    private double requireNormalized(double value, String name) {
        if (Double.isNaN(value))
            throw new IllegalArgumentException(name + " must be a number but is NaN");
        if (value < 0)
            throw new IllegalArgumentException(name + " must be zero or lager, but is " + value);
        return value;
    }

    @Override
    public String toString() {
        return "load: " + cpu + " cpu, " + memory + " memory, " + disk + " disk";
    }

    public static Load zero() { return new Load(0, 0, 0); }

    private static double divide(double a, double b) {
        if (a == 0 && b == 0) return 0;
        return a / b;
    }

    public static Load byDividing(NodeResources a, NodeResources b) {
        return new Load(divide(a.vcpu(), b.vcpu()), divide(a.memoryGb(), b.memoryGb()), divide(a.diskGb(), b.diskGb()));
    }

}
