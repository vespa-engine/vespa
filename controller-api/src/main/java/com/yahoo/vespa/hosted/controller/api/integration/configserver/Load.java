// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import java.util.Objects;

/**
 * @author bratseth
 */
public class Load {

    private final double cpu;
    private final double memory;
    private final double disk;

    public Load(double cpu, double memory, double disk) {
        this.cpu = cpu;
        this.memory = memory;
        this.disk = disk;
    }

    public double cpu() { return cpu; }
    public double memory() { return memory; }
    public double disk() { return disk; }

    @Override
    public String  toString() {
        return "load: cpu "  + cpu  + ", memory " + memory + ", disk " + disk;
    }

    @Override
    public int hashCode() { return Objects.hash(cpu, memory, disk); }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Load other)) return false;
        return cpu == other.cpu && memory == other.memory && disk == other.disk;
    }

    public static Load zero() { return new Load(0, 0, 0); }

}
