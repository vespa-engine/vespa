// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

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

    private double requireNormalized(double value, String name) {
        if (Double.isNaN(value))
            throw new IllegalArgumentException(name + " must be a number between 0 and 1, but is NaN");
        if (value < 0 || value > 1)
            throw new IllegalArgumentException(name + " must be between 0 and 1, but is " + value);
        return value;
    }

    @Override
    public String toString() {
        return "load: " + cpu + " cpu, " + memory + " memory, " + disk + " disk";
    }

    public static Load zero() { return new Load(0, 0, 0); }

}
