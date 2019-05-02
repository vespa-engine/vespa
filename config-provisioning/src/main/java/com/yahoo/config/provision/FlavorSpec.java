// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * The node capacity specified by an application, which is matched to an actual flavor during provisioning.
 *
 * @author bratseth
 */
public class FlavorSpec {

    private final double cpuCores;
    private final double memoryGb;
    private final double diskGb;

    private final boolean allocateByLegacyName;
    private final String legacyFlavorName;

    public FlavorSpec(double cpuCores, double memoryGb, double diskGb) {
        this.cpuCores = cpuCores;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.allocateByLegacyName = false;
        this.legacyFlavorName = null;
    }

    private FlavorSpec(double cpuCores, double memoryGb, double diskGb, boolean allocateByLegacyName, String legacyFlavorName) {
        this.cpuCores = cpuCores;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.allocateByLegacyName = allocateByLegacyName;
        this.legacyFlavorName = legacyFlavorName;
    }

    public double cpuCores() { return cpuCores; }
    public double memoryGb() { return memoryGb; }
    public double diskGb() { return diskGb; }

    /**
     * If this is true, a non-docker legacy name was used to specify this and we'll respect that by mapping directly.
     * The other getters of this will return 0.
     */
    public boolean allocateByLegacyName() { return allocateByLegacyName; }

    /** Returns the legacy flavor string of this. This is never null. */
    public String legacyFlavorName() {
        if (legacyFlavorName != null)
            return legacyFlavorName;
        else
            return "d-" + (int)Math.ceil(cpuCores) + "-" + (int)Math.ceil(memoryGb) + "-" + (int)Math.ceil(diskGb);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof FlavorSpec)) return false;
        FlavorSpec other = (FlavorSpec)o;
        if (allocateByLegacyName) {
            return this.legacyFlavorName.equals(other.legacyFlavorName);
        }
        else {
            if (this.cpuCores != other.cpuCores) return false;
            if (this.memoryGb != other.memoryGb) return false;
            if (this.diskGb != other.diskGb) return false;
            return true;
        }
    }

    @Override
    public int hashCode() {
        if (allocateByLegacyName)
            return legacyFlavorName.hashCode();
        else
            return (int)(2503 * cpuCores + 22123 * memoryGb + 26987 * diskGb);
    }

    @Override
    public String toString() {
        if (allocateByLegacyName)
            return "flavor '" + legacyFlavorName + "'";
        else
            return "cpu cores: " + cpuCores + ", memory: " + memoryGb + " Gb, disk " + diskGb + " Gb";
    }

    /**
     * Create this from a legacy flavor string.
     *
     * @throws IllegalArgumentException if the given string does not map to a legacy flavor
     */
    public static FlavorSpec fromLegacyFlavorName(String flavorString) {
        if (flavorString.startsWith("d-")) { // A docker flavor
            String[] parts = flavorString.split("-");
            double cpu = Integer.parseInt(parts[1]);
            double mem = Integer.parseInt(parts[2]);
            double dsk = Integer.parseInt(parts[3]);
            if (cpu == 0) cpu = 0.5;
            if (cpu == 2 && mem == 8 ) cpu = 1.5;
            if (cpu == 2 && mem == 12 ) cpu = 2.3;
            return new FlavorSpec(cpu, mem, dsk, false, flavorString);
        }
        else {
            return new FlavorSpec(0, 0, 0, true, flavorString);
        }
    }

}
