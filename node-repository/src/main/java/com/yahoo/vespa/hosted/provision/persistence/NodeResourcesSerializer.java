// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

import java.util.Optional;

/**
 * @author bratseth
 */
public class NodeResourcesSerializer {

    private static final String vcpuKey = "vcpu";
    private static final String memoryKey = "memory";
    private static final String diskKey = "disk";
    private static final String bandwidthKey = "bandwidth";
    private static final String diskSpeedKey = "diskSpeed";
    private static final String storageTypeKey = "storageType";
    private static final String architectureKey = "architecture";
    private static final String gpuKey = "gpu";
    private static final String gpuCountKey = "gpuCount";
    private static final String gpuMemoryKey = "gpuMemory";

    static void toSlime(NodeResources resources, Cursor resourcesObject) {
        if (resources.isUnspecified()) return;
        resourcesObject.setDouble(vcpuKey, resources.vcpu());
        resourcesObject.setDouble(memoryKey, resources.memoryGiB());
        resourcesObject.setDouble(diskKey, resources.diskGb());
        resourcesObject.setDouble(bandwidthKey, resources.bandwidthGbps());
        resourcesObject.setString(diskSpeedKey, diskSpeedToString(resources.diskSpeed()));
        resourcesObject.setString(storageTypeKey, storageTypeToString(resources.storageType()));
        resourcesObject.setString(architectureKey, architectureToString(resources.architecture()));
        if (!resources.gpuResources().isDefault()) {
            Cursor gpuObject = resourcesObject.setObject(gpuKey);
            gpuObject.setLong(gpuCountKey, resources.gpuResources().count());
            gpuObject.setDouble(gpuMemoryKey, resources.gpuResources().memoryGiB());
        }
    }

    static NodeResources resourcesFromSlime(Inspector resources) {
        if ( ! resources.field(vcpuKey).valid()) return NodeResources.unspecified();
        return new NodeResources(resources.field(vcpuKey).asDouble(),
                                 resources.field(memoryKey).asDouble(),
                                 resources.field(diskKey).asDouble(),
                                 resources.field(bandwidthKey).asDouble(),
                                 diskSpeedFromSlime(resources.field(diskSpeedKey)),
                                 storageTypeFromSlime(resources.field(storageTypeKey)),
                                 architectureFromSlime(resources.field(architectureKey)),
                                 gpuResourcesFromSlime(resources.field(gpuKey)));
    }

    static Optional<NodeResources> optionalResourcesFromSlime(Inspector resources) {
        return resources.valid() ? Optional.of(resourcesFromSlime(resources)) : Optional.empty();
    }

    private static NodeResources.DiskSpeed diskSpeedFromSlime(Inspector diskSpeed) {
        return switch (diskSpeed.asString()) {
            case "fast" -> NodeResources.DiskSpeed.fast;
            case "slow" -> NodeResources.DiskSpeed.slow;
            case "any" -> NodeResources.DiskSpeed.any;
            default -> throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed.asString() + "'");
        };
    }

    private static String diskSpeedToString(NodeResources.DiskSpeed diskSpeed) {
        return switch (diskSpeed) {
            case fast -> "fast";
            case slow -> "slow";
            case any -> "any";
        };
    }

    private static NodeResources.StorageType storageTypeFromSlime(Inspector storageType) {
        return switch (storageType.asString()) {
            case "remote" -> NodeResources.StorageType.remote;
            case "local" -> NodeResources.StorageType.local;
            case "any" -> NodeResources.StorageType.any;
            default -> throw new IllegalStateException("Illegal storage-type value '" + storageType.asString() + "'");
        };
    }

    private static String storageTypeToString(NodeResources.StorageType storageType) {
        return switch (storageType) {
            case remote -> "remote";
            case local -> "local";
            case any -> "any";
        };
    }

    private static NodeResources.Architecture architectureFromSlime(Inspector architecture) {
        if ( ! architecture.valid()) return NodeResources.Architecture.getDefault(); // TODO: Remove this line after March 2022
        return switch (architecture.asString()) {
            case "arm64" -> NodeResources.Architecture.arm64;
            case "x86_64" -> NodeResources.Architecture.x86_64;
            case "any" -> NodeResources.Architecture.any;
            default -> throw new IllegalStateException("Illegal architecture value '" + architecture.asString() + "'");
        };
    }

    private static String architectureToString(NodeResources.Architecture architecture) {
        return switch (architecture) {
            case arm64 -> "arm64";
            case x86_64 -> "x86_64";
            case any -> "any";
        };
    }

    private static NodeResources.GpuResources gpuResourcesFromSlime(Inspector gpu) {
        if (!gpu.valid()) return NodeResources.GpuResources.getDefault();
        return new NodeResources.GpuResources((int) gpu.field(gpuCountKey).asLong(),
                                              gpu.field(gpuMemoryKey).asDouble());
    }

}
