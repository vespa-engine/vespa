// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;

/**
 * The node resources required by an application cluster
 *
 * @author bratseth
 */
public class NodeResources {

    public enum DiskSpeed {

        fast, // Has/requires SSD disk or similar speed
        slow, // Has spinning disk/Is tuned to work with the speed of spinning disks
        any; // In requests only: The performance of the cluster using this does not depend on disk speed

        /**
         * Compares disk speeds by cost: Slower is cheaper, and therefore before.
         * Any can be slow and therefore costs the same as slow.
         */
        public static int compare(DiskSpeed a, DiskSpeed b) {
            if (a == any) a = slow;
            if (b == any) b = slow;

            if (a == slow && b == fast) return -1;
            if (a == fast && b == slow) return 1;
            return 0;
        }

        public boolean compatibleWith(DiskSpeed other) {
            return this == any || other == any || other == this;
        }

        private DiskSpeed combineWith(DiskSpeed other) {
            if (this == any) return other;
            if (other == any) return this;
            if (this == other) return this;
            throw new IllegalArgumentException(this + " cannot be combined with " + other);
        }

        public boolean isDefault() { return this == getDefault(); }
        public static DiskSpeed getDefault() { return fast; }

    }

    public enum StorageType {

        remote, // Has remote (network) storage/Is tuned to work with network storage
        local, // Storage is/must be attached to the local host
        any; // In requests only: Can use both local and remote storage

        /**
         * Compares storage type by cost: Remote is cheaper, and therefore before.
         * Any can be remote and therefore costs the same as remote.
         */
        public static int compare(StorageType a, StorageType b) {
            if (a == any) a = remote;
            if (b == any) b = remote;

            if (a == remote && b == local) return -1;
            if (a == local && b == remote) return 1;
            return 0;
        }

        public boolean compatibleWith(StorageType other) {
            return this == any || other == any || other == this;
        }

        private StorageType combineWith(StorageType other) {
            if (this == any) return other;
            if (other == any) return this;
            if (this == other) return this;
            throw new IllegalArgumentException(this + " cannot be combined with " + other);
        }

        public boolean isDefault() { return this == getDefault(); }
        public static StorageType getDefault() { return any; }

    }

    private final double vcpu;
    private final double memoryGb;
    private final double diskGb;
    private final double bandwidthGbps;
    private final DiskSpeed diskSpeed;
    private final StorageType storageType;

    public NodeResources(double vcpu, double memoryGb, double diskGb, double bandwidthGbps) {
        this(vcpu, memoryGb, diskGb, bandwidthGbps, DiskSpeed.getDefault());
    }

    public NodeResources(double vcpu, double memoryGb, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed) {
        this(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, StorageType.getDefault());
    }

    public NodeResources(double vcpu, double memoryGb, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed, StorageType storageType) {
        this.vcpu = vcpu;
        this.memoryGb = memoryGb;
        this.diskGb = diskGb;
        this.bandwidthGbps = bandwidthGbps;
        this.diskSpeed = diskSpeed;
        this.storageType = storageType;
    }

    public double vcpu() { return vcpu; }
    public double memoryGb() { return memoryGb; }
    public double diskGb() { return diskGb; }
    public double bandwidthGbps() { return bandwidthGbps; }
    public DiskSpeed diskSpeed() { return diskSpeed; }
    public StorageType storageType() { return storageType; }

    public NodeResources withVcpu(double vcpu) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    public NodeResources withMemoryGb(double memoryGb) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    public NodeResources withDiskGb(double diskGb) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    public NodeResources withBandwidthGbps(double bandwidthGbps) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    // TODO: Remove after November 2019
    public NodeResources withDiskSpeed(DiskSpeed speed) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, speed, storageType);
    }

    public NodeResources with(DiskSpeed speed) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, speed, storageType);
    }

    public NodeResources with(StorageType storageType) {
        return new NodeResources(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    /** Returns this with disk speed and storage type set to any */
    public NodeResources justNumbers() {
        return with(NodeResources.DiskSpeed.any).with(StorageType.any);
    }

    public NodeResources subtract(NodeResources other) {
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu - other.vcpu,
                                 memoryGb - other.memoryGb,
                                 diskGb - other.diskGb,
                                 bandwidthGbps - other.bandwidthGbps,
                                 this.diskSpeed.combineWith(other.diskSpeed),
                                 this.storageType.combineWith(other.storageType));
    }

    public NodeResources add(NodeResources other) {
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu + other.vcpu,
                                 memoryGb + other.memoryGb,
                                 diskGb + other.diskGb,
                                 bandwidthGbps + other.bandwidthGbps,
                                 this.diskSpeed.combineWith(other.diskSpeed),
                                 this.storageType.combineWith(other.storageType));
    }

    private boolean isInterchangeableWith(NodeResources other) {
        if (this.diskSpeed != DiskSpeed.any && other.diskSpeed != DiskSpeed.any && this.diskSpeed != other.diskSpeed)
            return false;
        if (this.storageType != StorageType.any && other.storageType != StorageType.any && this.storageType != other.storageType)
            return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof NodeResources)) return false;
        NodeResources other = (NodeResources)o;
        if (this.vcpu != other.vcpu) return false;
        if (this.memoryGb != other.memoryGb) return false;
        if (this.diskGb != other.diskGb) return false;
        if (this.bandwidthGbps != other.bandwidthGbps) return false;
        if (this.diskSpeed != other.diskSpeed) return false;
        if (this.storageType != other.storageType) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryGb, diskGb, bandwidthGbps, diskSpeed, storageType);
    }

    @Override
    public String toString() {
        return "[vcpu: " + vcpu + ", memory: " + memoryGb + " Gb, disk " + diskGb + " Gb" +
               (bandwidthGbps > 0 ? ", bandwidth: " + bandwidthGbps + " Gbps" : "") +
               ( ! diskSpeed.isDefault() ? ", disk speed: " + diskSpeed : "") +
               ( ! storageType.isDefault() ? ", storage type: " + storageType : "") + "]";
    }

    /** Returns true if all the resources of this are the same or larger than the given resources */
    public boolean satisfies(NodeResources other) {
        if (this.vcpu < other.vcpu) return false;
        if (this.memoryGb < other.memoryGb) return false;
        if (this.diskGb < other.diskGb) return false;
        if (this.bandwidthGbps < other.bandwidthGbps) return false;

        // Why doesn't a fast disk satisfy a slow disk? Because if slow disk is explicitly specified
        // (i.e not "any"), you should not randomly, sometimes get a faster disk as that means you may
        // draw conclusions about performance on the basis of better resources than you think you have
        if (other.diskSpeed != DiskSpeed.any && other.diskSpeed != this.diskSpeed) return false;

        // Same reasoning as the above
        if (other.storageType != StorageType.any && other.storageType != this.storageType) return false;

        return true;
    }

    /** Returns true if all the resources of this are the same as or compatible with the given resources */
    public boolean compatibleWith(NodeResources other) {
        if (this.vcpu != other.vcpu) return false;
        if (this.memoryGb != other.memoryGb) return false;
        if (this.diskGb != other.diskGb) return false;
        if (this.bandwidthGbps != other.bandwidthGbps) return false;
        if ( ! this.diskSpeed.compatibleWith(other.diskSpeed)) return false;
        if ( ! this.storageType.compatibleWith(other.storageType)) return false;

        return true;
    }

    /**
     * Create this from serial form.
     *
     * @throws IllegalArgumentException if the given string cannot be parsed as a serial form of this
     */
    public static NodeResources fromLegacyName(String name) {
        if ( ! name.startsWith("d-"))
            throw new IllegalArgumentException("A node specification string must start by 'd-' but was '" + name + "'");
        String[] parts = name.split("-");
        if (parts.length != 4)
            throw new IllegalArgumentException("A node specification string must contain three numbers separated by '-' but was '" + name + "'");

        double cpu = Integer.parseInt(parts[1]);
        double mem = Integer.parseInt(parts[2]);
        double dsk = Integer.parseInt(parts[3]);
        if (cpu == 0) cpu = 0.5;
        if (cpu == 2 && mem == 8 ) cpu = 1.5;
        if (cpu == 2 && mem == 12 ) cpu = 2.3;
        return new NodeResources(cpu, mem, dsk, 0.3, DiskSpeed.getDefault(), StorageType.getDefault());
    }

}
