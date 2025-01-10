// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Objects;
import java.util.Optional;

/**
 * The node resources required by an application cluster
 *
 * @author bratseth
 */
public class NodeResources {

    // Standard unit cost in dollars per hour
    private static final double cpuUnitCost =    0.11;
    private static final double memoryUnitCost = 0.011;
    private static final double diskUnitCost =   0.0004;
    private static final double gpuUnitCost =    0.075;

    private static final NodeResources zero = new NodeResources(0, 0, 0, 0);
    private static final NodeResources unspecified = new NodeResources(0, 0, 0, 0);

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

    public enum Architecture {

        x86_64,
        arm64,
        any;

        public static int compare(Architecture a, Architecture b) {
            if (a == any) a = x86_64;
            if (b == any) b = x86_64;

            if (a == x86_64 && b == arm64) return -1;
            if (a == arm64 && b == x86_64) return 1;
            return 0;
        }

        public boolean compatibleWith(Architecture other) {
            return this == any || other == any || other == this;
        }

        private Architecture combineWith(Architecture other) {
            if (this == any) return other;
            if (other == any) return this;
            if (this == other) return this;
            throw new IllegalArgumentException(this + " cannot be combined with " + other);
        }

        public boolean isDefault() { return this == getDefault(); }
        public static Architecture getDefault() { return any; }

    }

    public record GpuResources(int count, double memoryGiB) {

        private static final GpuResources zero = new GpuResources(0, 0);

        public GpuResources {
            validate(memoryGiB, "memory");
        }

        private boolean lessThan(GpuResources other) {
            return this.count < other.count ||
                   this.memoryGiB < other.memoryGiB;
        }

        public boolean isZero() { return this.equals(zero); }

        public static GpuResources zero() { return zero; }

        public boolean isDefault() { return this.equals(getDefault()); }

        /** Returns zero gpu resources. */
        public static GpuResources getDefault() { return zero; }

        public GpuResources plus(GpuResources other) {
            if (other.isZero()) return this;
            var thisMem = this.count() * this.memoryGiB();
            var otherMem = other.count() * other.memoryGiB();
            return new NodeResources.GpuResources(1, thisMem + otherMem);
        }

        public GpuResources minus(GpuResources other) {
            if (other.isZero()) return this;
            var thisMem = this.count() * this.memoryGiB();
            var otherMem = other.count() * other.memoryGiB();
            return new NodeResources.GpuResources(1, thisMem - otherMem);
        }

        public GpuResources multipliedBy(double factor) {
            return new GpuResources(this.count, this.memoryGiB * factor);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GpuResources that = (GpuResources) o;
            return count == that.count && equal(this.memoryGiB, that.memoryGiB);
        }

        @Override
        public int hashCode() {
            return Objects.hash(count, memoryGiB);
        }
    }

    private final double vcpu;
    private final double memoryGiB;
    private final double diskGb;
    private final double bandwidthGbps;
    private final GpuResources gpuResources;
    private final DiskSpeed diskSpeed;
    private final StorageType storageType;
    private final Architecture architecture;

    public NodeResources(double vcpu, double memoryGiB, double diskGb, double bandwidthGbps) {
        this(vcpu, memoryGiB, diskGb, bandwidthGbps, DiskSpeed.getDefault());
    }

    public NodeResources(double vcpu, double memoryGiB, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed) {
        this(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, StorageType.getDefault(), Architecture.getDefault(), GpuResources.getDefault());
    }

    public NodeResources(double vcpu, double memoryGiB, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed, StorageType storageType) {
        this(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, Architecture.getDefault(), GpuResources.getDefault());
    }

    public NodeResources(double vcpu, double memoryGiB, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed, StorageType storageType, Architecture architecture) {
        this(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, GpuResources.getDefault());
    }

    public NodeResources(double vcpu, double memoryGiB, double diskGb, double bandwidthGbps, DiskSpeed diskSpeed, StorageType storageType, Architecture architecture, GpuResources gpuResources) {
        this.vcpu = validate(vcpu, "vcpu");
        this.memoryGiB = validate(memoryGiB, "memory");
        this.diskGb = validate(diskGb, "disk");
        this.bandwidthGbps = validate(bandwidthGbps, "bandwidth");
        this.gpuResources = gpuResources;
        this.diskSpeed = diskSpeed;
        this.storageType = storageType;
        this.architecture = architecture;
    }

    public double vcpu() { return vcpu; }
    public double memoryGiB() { return memoryGiB; }
    public double diskGb() { return diskGb; }
    public double bandwidthGbps() { return bandwidthGbps; }
    public DiskSpeed diskSpeed() { return diskSpeed; }
    public StorageType storageType() { return storageType; }
    public Architecture architecture() { return architecture; }
    public GpuResources gpuResources() { return gpuResources; }

    public boolean vcpuIsUnspecified() { return vcpu == 0; }
    public boolean memoryIsUnspecified() { return memoryGiB == 0; }
    public boolean diskIsUnspecified() { return diskGb == 0; }
    public boolean bandwidthGbpsIsUnspecified() { return bandwidthGbps == 0; }

    /** Returns the standard cost of these resources, in dollars per hour */
    public double cost() {
        return (vcpu * cpuUnitCost) +
               (memoryGiB * memoryUnitCost) +
               (diskGb * diskUnitCost) +
               (gpuResources.count * gpuResources.memoryGiB * gpuUnitCost);
    }

    public NodeResources withVcpu(double vcpu) {
        if (vcpu == this.vcpu) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources withMemoryGiB(double memoryGiB) {
        if (memoryGiB == this.memoryGiB) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources withDiskGb(double diskGb) {
        if (diskGb == this.diskGb) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources withBandwidthGbps(double bandwidthGbps) {
        ensureSpecified();
        if (bandwidthGbps == this.bandwidthGbps) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources with(DiskSpeed diskSpeed) {
        ensureSpecified();
        if (diskSpeed == this.diskSpeed) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources with(StorageType storageType) {
        ensureSpecified();
        if (storageType == this.storageType) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources with(Architecture architecture) {
        ensureSpecified();
        if (architecture == this.architecture) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources with(GpuResources gpuResources) {
        ensureSpecified();
        if (this.gpuResources.equals(gpuResources)) return this;
        return new NodeResources(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture, gpuResources);
    }

    public NodeResources withUnspecifiedFieldsFrom(NodeResources fullySpecified) {
        var resources = this;
        if (resources.vcpuIsUnspecified())
            resources = resources.withVcpu(fullySpecified.vcpu());
        if (resources.memoryIsUnspecified())
            resources = resources.withMemoryGiB(fullySpecified.memoryGiB());
        if (resources.diskIsUnspecified())
            resources = resources.withDiskGb(fullySpecified.diskGb());
        if (resources.bandwidthGbpsIsUnspecified())
            resources = resources.withBandwidthGbps(fullySpecified.bandwidthGbps());
        if (resources.diskSpeed() == DiskSpeed.any)
            resources = resources.with(fullySpecified.diskSpeed());
        if (resources.storageType() == StorageType.any)
            resources = resources.with(fullySpecified.storageType());
        if (resources.architecture() == Architecture.any)
            resources = resources.with(fullySpecified.architecture());
        assert fullySpecified.gpuResources() == GpuResources.zero : "Not handled";
        return resources;
    }

    /** Returns this with disk speed, storage type and architecture set to any */
    public NodeResources justNumbers() {
        if (isUnspecified()) return unspecified();
        return with(NodeResources.DiskSpeed.any).with(StorageType.any).with(Architecture.any);
    }

    /** Returns this with all numbers set to 0 */
    public NodeResources justNonNumbers() {
        if (isUnspecified()) return unspecified();
        return withVcpu(0).withMemoryGiB(0).withDiskGb(0).withBandwidthGbps(0).with(GpuResources.zero());
    }

    public NodeResources subtract(NodeResources other) {
        ensureSpecified();
        other.ensureSpecified();
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu - other.vcpu,
                                 memoryGiB - other.memoryGiB,
                                 diskGb - other.diskGb,
                                 bandwidthGbps - other.bandwidthGbps,
                                 this.diskSpeed.combineWith(other.diskSpeed),
                                 this.storageType.combineWith(other.storageType),
                                 this.architecture.combineWith(other.architecture),
                                 this.gpuResources.minus(other.gpuResources));
    }

    public NodeResources add(NodeResources other) {
        ensureSpecified();
        if ( ! this.isInterchangeableWith(other))
            throw new IllegalArgumentException(this + " and " + other + " are not interchangeable");
        return new NodeResources(vcpu + other.vcpu,
                                 memoryGiB + other.memoryGiB,
                                 diskGb + other.diskGb,
                                 bandwidthGbps + other.bandwidthGbps,
                                 this.diskSpeed.combineWith(other.diskSpeed),
                                 this.storageType.combineWith(other.storageType),
                                 this.architecture.combineWith(other.architecture),
                                 this.gpuResources.plus(other.gpuResources));
    }

    public NodeResources multipliedBy(double factor) {
        if (isUnspecified()) return this;
        return this.withVcpu(vcpu * factor)
                   .withMemoryGiB(memoryGiB * factor)
                   .withDiskGb(diskGb * factor)
                   .withBandwidthGbps(bandwidthGbps * factor)
                   .with(gpuResources.multipliedBy(factor));
    }

    private boolean isInterchangeableWith(NodeResources other) {
        ensureSpecified();
        other.ensureSpecified();
        if (this.diskSpeed != DiskSpeed.any && other.diskSpeed != DiskSpeed.any && this.diskSpeed != other.diskSpeed)
            return false;
        if (this.storageType != StorageType.any && other.storageType != StorageType.any && this.storageType != other.storageType)
            return false;
        if (this.architecture != Architecture.any && other.architecture != Architecture.any && this.architecture != other.architecture)
            return false;
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof NodeResources other)) return false;
        if ( ! equal(this.vcpu, other.vcpu)) return false;
        if ( ! equal(this.memoryGiB, other.memoryGiB)) return false;
        if ( ! equal(this.diskGb, other.diskGb)) return false;
        if ( ! equal(this.bandwidthGbps, other.bandwidthGbps)) return false;
        if ( ! this.gpuResources.equals(other.gpuResources)) return false;
        if (this.diskSpeed != other.diskSpeed) return false;
        if (this.storageType != other.storageType) return false;
        if (this.architecture != other.architecture) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(vcpu, memoryGiB, diskGb, bandwidthGbps, diskSpeed, storageType, architecture);
    }

    private static StringBuilder appendDouble(StringBuilder sb, double d) {
        long x10 = Math.round(d * 10);
        sb.append(x10 / 10).append('.').append(x10 % 10);
        return sb;
    }

    @Override
    public String toString() {
        if (isUnspecified())
            return "unspecified resources";

        StringBuilder sb = new StringBuilder("[vcpu: ");
        appendDouble(sb, vcpu);
        sb.append(", memory: ");
        appendDouble(sb, memoryGiB);
        sb.append(" Gb, disk: ");
        appendDouble(sb, diskGb);
        sb.append(" Gb");
        if (bandwidthGbps > 0) {
            sb.append(", bandwidth: ");
            appendDouble(sb, bandwidthGbps);
            sb.append(" Gbps");
        }
        if ( !diskSpeed.isDefault()) {
            sb.append(", disk speed: ").append(diskSpeed);
        }
        if ( !storageType.isDefault()) {
            sb.append(", storage type: ").append(storageType);
        }
        sb.append(", architecture: ").append(architecture);
        if ( !gpuResources.isDefault()) {
            sb.append(", gpu count: ").append(gpuResources.count());
            sb.append(", gpu memory: ");
            appendDouble(sb, gpuResources.memoryGiB());
            sb.append(" Gb");
        }
        sb.append(']');
        return sb.toString();
    }

    /** Returns true if all the resources of this are the same or larger than the given resources */
    public boolean satisfies(NodeResources other) {
        ensureSpecified();
        other.ensureSpecified();
        if (this.vcpu < other.vcpu) return false;
        if (this.memoryGiB < other.memoryGiB) return false;
        if (this.diskGb < other.diskGb) return false;
        if (this.bandwidthGbps < other.bandwidthGbps) return false;
        if (this.gpuResources.lessThan(other.gpuResources)) return false;

        // Why doesn't a fast disk satisfy a slow disk? Because if slow disk is explicitly specified
        // (i.e not "any"), you should not randomly, sometimes get a faster disk as that means you may
        // draw conclusions about performance on the basis of better resources than you think you have
        if (other.diskSpeed != DiskSpeed.any && other.diskSpeed != this.diskSpeed) return false;

        // Same reasoning as the above
        if (other.storageType != StorageType.any && other.storageType != this.storageType) return false;

        // Same reasoning as the above
        if (other.architecture != Architecture.any && other.architecture != this.architecture) return false;

        return true;
    }

    /**
     * Returns true if all the resources of this are the same as or compatible with the requested resources:
     * - Equal numbers only where request implies it (i.e not for disk if storage is any/remote, and not for bandwidth
     *   where we don't enforce constraints),
     * - Compatible non-numbers.
     */
    public boolean compatibleWith(NodeResources requested) {
        if ( ! equal(this.vcpu, requested.vcpu)) return false;
        if ( ! equal(this.memoryGiB, requested.memoryGiB)) return false;
        if (this.storageType == StorageType.local || requested.storageType == StorageType.local) {
            if ( ! equal(this.diskGb, requested.diskGb)) return false;
        }
        else {
            if (this.diskGb < requested.diskGb) return false;
        }
        if ( ! this.gpuResources.equals(requested.gpuResources)) return false;
        if ( ! this.diskSpeed.compatibleWith(requested.diskSpeed)) return false;
        if ( ! this.storageType.compatibleWith(requested.storageType)) return false;
        if ( ! this.architecture.compatibleWith(requested.architecture)) return false;

        return true;
    }

    /**
     * Returns true if all the resources of this are the same as or compatible with the given resources:
     * - Equal numbers.
     * - Compatible non-numbers.
     */
    public boolean equalsWhereSpecified(NodeResources other) {
        if ( ! equal(this.vcpu, other.vcpu)) return false;
        if ( ! equal(this.memoryGiB, other.memoryGiB)) return false;
        if ( ! equal(this.diskGb, other.diskGb)) return false;
        if ( ! equal(this.bandwidthGbps, other.bandwidthGbps)) return false;
        if ( ! this.gpuResources.equals(other.gpuResources)) return false;
        if ( ! this.diskSpeed.compatibleWith(other.diskSpeed)) return false;
        if ( ! this.storageType.compatibleWith(other.storageType)) return false;
        if ( ! this.architecture.compatibleWith(other.architecture)) return false;

        return true;
    }

    public static NodeResources unspecified() { return unspecified; }

    public boolean isUnspecified() { return this == unspecified; }

    private void ensureSpecified() {
        if (isUnspecified())
            throw new IllegalStateException("Cannot perform this on unspecified resources");
    }

    // Returns squared Euclidean distance of the relevant numerical values of two node resources
    public double distanceTo(NodeResources other) {
        if ( ! this.diskSpeed().compatibleWith(other.diskSpeed())) return Double.MAX_VALUE;
        if ( ! this.storageType().compatibleWith(other.storageType())) return Double.MAX_VALUE;

        double distance =  Math.pow(this.vcpu() - other.vcpu(), 2) + Math.pow(this.memoryGiB() - other.memoryGiB(), 2);
        if (this.storageType() == StorageType.local || other.storageType() == StorageType.local)
            distance += Math.pow(this.diskGb() - other.diskGb(), 2);
        return distance;
    }

    /** Returns this.isUnspecified() ? Optional.empty() : Optional.of(this) */
    public Optional<NodeResources> asOptional() {
        return this.isUnspecified() ? Optional.empty() : Optional.of(this);
    }

    private static boolean equal(double a, double b) {
        return Math.abs(a - b) < 0.00000001;
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
        return new NodeResources(cpu, mem, dsk, 0.3, DiskSpeed.getDefault(), StorageType.getDefault(), Architecture.any);
    }

    private static double validate(double value, String valueName) {
        if (Double.isNaN(value)) throw new IllegalArgumentException(valueName + " cannot be NaN");
        if (Double.isInfinite(value)) throw new IllegalArgumentException(valueName + " cannot be infinite");
        return value;
    }

    public boolean isZero() {
        return this.equals(zero);
    }

    public static NodeResources zero() { return zero; }

}
