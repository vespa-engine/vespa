// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.config.provision.NodeResources;
import com.yahoo.slime.Cursor;

/**
 * @author bratseth
 */
public class NodeResourcesSerializer {

    static void toSlime(NodeResources resources, Cursor object) {
        object.setDouble("vcpu", resources.vcpu());
        object.setDouble("memoryGb", resources.memoryGb());
        object.setDouble("diskGb", resources.diskGb());
        object.setDouble("bandwidthGbps", resources.bandwidthGbps());
        object.setString("diskSpeed", toString(resources.diskSpeed()));
        object.setString("storageType", toString(resources.storageType()));
        object.setString("architecture", toString(resources.architecture()));
    }

    public static NodeResources.DiskSpeed diskSpeedFrom(String diskSpeed) {
        switch (diskSpeed) {
            case "fast": return NodeResources.DiskSpeed.fast;
            case "slow": return NodeResources.DiskSpeed.slow;
            case "any" : return NodeResources.DiskSpeed.any;
            default: throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed + "'");
        }
    }

    private static String toString(NodeResources.DiskSpeed diskSpeed) {
        switch (diskSpeed) {
            case fast : return "fast";
            case slow : return "slow";
            case any  : return "any";
            default: throw new IllegalArgumentException("Unknown disk speed '" + diskSpeed.name() + "'");
        }
    }

    public static NodeResources.StorageType storageTypeFrom(String storageType) {
        switch (storageType) {
            case "local" : return NodeResources.StorageType.local;
            case "remote": return NodeResources.StorageType.remote;
            case "any"   : return NodeResources.StorageType.any;
            default: throw new IllegalArgumentException("Unknown storage type '" + storageType + "'");
        }
    }

    private static String toString(NodeResources.StorageType storageType) {
        switch (storageType) {
            case remote : return "remote";
            case local  : return "local";
            case any    : return "any";
            default: throw new IllegalArgumentException("Unknown storage type '" + storageType.name() + "'");
        }
    }

    private static String toString(NodeResources.Architecture architecture) {
        switch (architecture) {
            case arm64 : return "arm64";
            case x86_64: return "x86_64";
            case any   : return "any";
            default: throw new IllegalArgumentException("Unknown architecture '" + architecture.name() + "'");
        }
    }

    public static NodeResources.Architecture architectureFrom(String architecture) {
        switch (architecture) {
            case "arm64" : return NodeResources.Architecture.arm64;
            case "x86_64": return NodeResources.Architecture.x86_64;
            case "any"   : return NodeResources.Architecture.any;
            default: throw new IllegalArgumentException("Unknown architecture '" + architecture + "'");
        }
    }

}
