// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;
import java.util.Optional;

/**
 * The node resources required by an application cluster
 *
 * @author bratseth
 */
public class NodeResources {

    public enum DiskSpeed {
        fast, // SSD disk or similar speed is needed
        slow, // This is tuned to work with the speed of spinning disks
        any // The performance of the cluster using this does not depend on disk speed
    }

    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;
    private final DiskSpeed diskSpeed;

    /** The legacy (flavor) name of this, or null if none */
    private final String legacyName;

    /** Create node resources requiring fast disk */
    public NodeResources(double vcpu, double memoryGb, double diskGb) {
        this(vcpu, memoryGb, diskGb, DiskSpeed.fast);
    }

    public NodeResources(double vcpu, double memoryGb, double diskGb, DiskSpeed diskSpeed) {
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.diskSpeed = diskSpeed;
        this.legacyName = null;
    }

    private NodeResources(double vcpu, double memoryGb, double diskGb, DiskSpeed diskSpeed, String legacyName) {
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.diskSpeed = diskSpeed;
        this.legacyName = legacyName;
    }

    public double vcpu() { return vcpu; }
    public double memoryGb() { return memoryGb; }
    public double diskGb() { return diskGb; }
    public DiskSpeed diskSpeed() { return diskSpeed; }

    public NodeResources withDiskSpeed(DiskSpeed speed) {
        return new NodeResources(vcpu, memoryGb, diskGb, speed);
    }

    public NodeResources withVcpu(double vcpu) {
        return new NodeResources(vcpu, memoryGb, diskGb, diskSpeed);
    }

    public NodeResources subtract(NodeResources other) {
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu - other.vcpu,
                                 memoryGb - other.memoryGb,
                                 diskGb - other.diskGb,
                                 combine(this.diskSpeed, other.diskSpeed));
    }

    public NodeResources add(NodeResources other) {
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu + other.vcpu,
                                 memoryGb + other.memoryGb,
                                 diskGb + other.diskGb,
                                 combine(this.diskSpeed, other.diskSpeed));
    }

    /** Returns the legacy name of this, or empty if none. */
    public Optional<String> legacyName() {
        return Optional.ofNullable(legacyName);
    }

    private boolean isInterchangeableWith(NodeResources other) {
        if (this.diskSpeed != DiskSpeed.any && other.diskSpeed != DiskSpeed.any && this.diskSpeed != other.diskSpeed)
            return false;
        return true;
    }

    private DiskSpeed combine(DiskSpeed a, DiskSpeed b) {
        if (a == DiskSpeed.any) return b;
        if (b == DiskSpeed.any) return a;
        if (a == b) return a;
        throw new IllegalArgumentException(a + " cannot be combined with " + b);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof NodeResources)) return false;
        NodeResources other = (NodeResources)o;
        if (this.vcpu != other.vcpu) return false;
        if (this.memoryGb != other.memoryGb) return false;
        if (this.diskGb != other.diskGb) return false;
        if (this.diskSpeed != other.diskSpeed) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return (int)(2503 * vcpu + 22123 * memoryGb + 26987 * diskGb + diskSpeed.hashCode());
    }

    @Override
    public String toString() {
        return "[vcpu: " + vcpu + ", memory: " + memoryGb + " Gb, disk " + diskGb + " Gb" +
               (diskSpeed != DiskSpeed.fast ? ", disk speed: " + diskSpeed : "") + "]";
    }

    /** Returns true if all the resources of this are the same or larger than the given resources */
    public boolean satisfies(NodeResources other) {
        if (this.vcpu < other.vcpu) return false;
        if (this.memoryGb < other.memoryGb) return false;
        if (this.diskGb < other.diskGb) return false;

        // Why doesn't a fast disk satisfy a slow disk? Because if slow disk is explicitly specified
        // (i.e not "any"), you should not randomly, sometimes get a faster disk as that means you may
        // draw conclusions about performance on the basis of better resources than you think you have
        if (other.diskSpeed != DiskSpeed.any && other.diskSpeed != this.diskSpeed) return false;

        return true;
    }

    /** Returns true if all the resources of this are the same as or compatible with the given resources */
    public boolean compatibleWith(NodeResources other) {
        if (this.vcpu != other.vcpu) return false;
        if (this.memoryGb != other.memoryGb) return false;
        if (this.diskGb != other.diskGb) return false;
        if (other.diskSpeed != DiskSpeed.any && other.diskSpeed != this.diskSpeed) return false;

        return true;
    }

    /**
     * Create this from serial form.
     *
     * @throws IllegalArgumentException if the given string cannot be parsed as a serial form of this
     */
    public static NodeResources fromLegacyName(String string) {
        if ( ! string.startsWith("d-"))
            throw new IllegalArgumentException("A node specification string must start by 'd-' but was '" + string + "'");
        String[] parts = string.split("-");
        if (parts.length != 4)
            throw new IllegalArgumentException("A node specification string must contain three numbers separated by '-' but was '" + string + "'");

        double cpu = Integer.parseInt(parts[1]);
        double mem = Integer.parseInt(parts[2]);
        double dsk = Integer.parseInt(parts[3]);
        if (cpu == 0) cpu = 0.5;
        if (cpu == 2 && mem == 8 ) cpu = 1.5;
        if (cpu == 2 && mem == 12 ) cpu = 2.3;
        return new NodeResources(cpu, mem, dsk, DiskSpeed.fast, string);
    }

}
