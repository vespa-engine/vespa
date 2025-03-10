// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;

/**
 * @author bratseth
 */
public class NodeResourcesSerializer {

    static void toSlime(NodeResources resources, Cursor object) {
        object.setDouble("vcpu", resources.vcpu());
        object.setDouble("memoryGb", resources.memoryGiB());
        object.setDouble("diskGb", resources.diskGb());
        object.setDouble("bandwidthGbps", resources.bandwidthGbps());
        object.setString("diskSpeed", toString(resources.diskSpeed()));
        object.setString("storageType", toString(resources.storageType()));
        object.setString("architecture", toString(resources.architecture()));
        if (!resources.gpuResources().isDefault()) {
            object.setLong("gpuCount", resources.gpuResources().count());
            object.setDouble("gpuMemoryGb", resources.gpuResources().memoryGiB());
        }
    }

    public static NodeResources.DiskSpeed diskSpeedFrom(String diskSpeed) {
        return switch (diskSpeed) {
            case "fast" -> NodeResources.DiskSpeed.fast;
            case "slow" -> NodeResources.DiskSpeed.slow;
            case "any" -> NodeResources.DiskSpeed.any;
            default -> throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed + "'");
        };
    }

    private static String toString(NodeResources.DiskSpeed diskSpeed) {
        return switch (diskSpeed) {
            case fast -> "fast";
            case slow -> "slow";
            case any -> "any";
        };
    }

    public static NodeResources.StorageType storageTypeFrom(String storageType) {
        return switch (storageType) {
            case "local" -> NodeResources.StorageType.local;
            case "remote" -> NodeResources.StorageType.remote;
            case "any" -> NodeResources.StorageType.any;
            default -> throw new IllegalArgumentException("Unknown storage type '" + storageType + "'");
        };
    }

    private static String toString(NodeResources.StorageType storageType) {
        return switch (storageType) {
            case remote -> "remote";
            case local -> "local";
            case any -> "any";
        };
    }

    private static String toString(NodeResources.Architecture architecture) {
        return switch (architecture) {
            case arm64 -> "arm64";
            case x86_64 -> "x86_64";
            case any -> "any";
        };
    }

    public static NodeResources.Architecture architectureFrom(String architecture) {
        return switch (architecture) {
            case "arm64" -> NodeResources.Architecture.arm64;
            case "x86_64" -> NodeResources.Architecture.x86_64;
            case "any" -> NodeResources.Architecture.any;
            default -> throw new IllegalArgumentException("Unknown architecture '" + architecture + "'");
        };
    }

    public static NodeResources.GpuResources gpuResourcesFromSlime(Inspector gpuObject) {
        if (!gpuObject.valid()) return NodeResources.GpuResources.getDefault();
        return new NodeResources.GpuResources((int) gpuObject.field("gpuCount").asLong(),
                                              gpuObject.field("gpuMemory").asDouble());
    }

}
