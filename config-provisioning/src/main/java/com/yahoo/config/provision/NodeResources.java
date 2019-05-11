// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Optional;

/**
 * The node resources required by an application cluster
 *
 * @author bratseth
 */
public class NodeResources {

    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;

    private final boolean allocateByLegacyName;

    /** The legacy (flavor) name of this, or null if none */
    private final String legacyName;

    public NodeResources(double vcpu, double memoryGb, double diskGb) {
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.allocateByLegacyName = false;
        this.legacyName = null;
    }

    private NodeResources(double vcpu, double memoryGb, double diskGb, boolean allocateByLegacyName, String legacyName) {
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.allocateByLegacyName = allocateByLegacyName;
        this.legacyName = legacyName;
    }

    public double vcpu() { return vcpu; }
    public double memoryGb() { return memoryGb; }
    public double diskGb() { return diskGb; }

    /**
     * If this is true, a non-docker legacy name was used to specify this and we'll respect that by mapping directly.
     * The other getters of this will return 0.
     */
    public boolean allocateByLegacyName() { return allocateByLegacyName; }

    /** Returns the legacy name of this, or empty if none. */
    public Optional<String> legacyName() {
        return Optional.ofNullable(legacyName);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof NodeResources)) return false;
        NodeResources other = (NodeResources)o;
        if (allocateByLegacyName) {
            return this.legacyName.equals(other.legacyName);
        }
        else {
            if (this.vcpu != other.vcpu) return false;
            if (this.memoryGb != other.memoryGb) return false;
            if (this.diskGb != other.diskGb) return false;
            return true;
        }
    }

    @Override
    public int hashCode() {
        if (allocateByLegacyName)
            return legacyName.hashCode();
        else
            return (int)(2503 * vcpu + 22123 * memoryGb + 26987 * diskGb);
    }

    @Override
    public String toString() {
        if (allocateByLegacyName)
            return "flavor '" + legacyName + "'";
        else
            return "[vcpu: " + vcpu + ", memory: " + memoryGb + " Gb, disk " + diskGb + " Gb]";
    }

    /**
     * Create this from serial form.
     *
     * @throws IllegalArgumentException if the given string cannot be parsed as a serial form of this
     */
    public static NodeResources fromLegacyName(String flavorString) {
        if (flavorString.startsWith("d-")) { // A legacy docker flavor: We still allocate by numbers
            String[] parts = flavorString.split("-");
            double cpu = Integer.parseInt(parts[1]);
            double mem = Integer.parseInt(parts[2]);
            double dsk = Integer.parseInt(parts[3]);
            if (cpu == 0) cpu = 0.5;
            if (cpu == 2 && mem == 8 ) cpu = 1.5;
            if (cpu == 2 && mem == 12 ) cpu = 2.3;
            return new NodeResources(cpu, mem, dsk, false, flavorString);
        }
        else { // Another legacy flavor: Allocate by direct matching
            return new NodeResources(0, 0, 0, true, flavorString);
        }
    }

}
