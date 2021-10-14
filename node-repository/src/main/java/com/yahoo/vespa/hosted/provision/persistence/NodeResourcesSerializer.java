// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    static void toSlime(NodeResources resources, Cursor resourcesObject) {
        resourcesObject.setDouble(vcpuKey, resources.vcpu());
        resourcesObject.setDouble(memoryKey, resources.memoryGb());
        resourcesObject.setDouble(diskKey, resources.diskGb());
        resourcesObject.setDouble(bandwidthKey, resources.bandwidthGbps());
        resourcesObject.setString(diskSpeedKey, diskSpeedToString(resources.diskSpeed()));
        resourcesObject.setString(storageTypeKey, storageTypeToString(resources.storageType()));
    }

    static NodeResources resourcesFromSlime(Inspector resources) {
        return new NodeResources(resources.field(vcpuKey).asDouble(),
                                 resources.field(memoryKey).asDouble(),
                                 resources.field(diskKey).asDouble(),
                                 resources.field(bandwidthKey).asDouble(),
                                 diskSpeedFromSlime(resources.field(diskSpeedKey)),
                                 storageTypeFromSlime(resources.field(storageTypeKey)));
    }

    static Optional<NodeResources> optionalResourcesFromSlime(Inspector resources) {
        return resources.valid() ? Optional.of(resourcesFromSlime(resources)) : Optional.empty();
    }

    private static NodeResources.DiskSpeed diskSpeedFromSlime(Inspector diskSpeed) {
        switch (diskSpeed.asString()) {
            case "fast" : return NodeResources.DiskSpeed.fast;
            case "slow" : return NodeResources.DiskSpeed.slow;
            case "any" : return NodeResources.DiskSpeed.any;
            default: throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed.asString() + "'");
        }
    }

    private static String diskSpeedToString(NodeResources.DiskSpeed diskSpeed) {
        switch (diskSpeed) {
            case fast : return "fast";
            case slow : return "slow";
            case any : return "any";
            default: throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed + "'");
        }
    }

    private static NodeResources.StorageType storageTypeFromSlime(Inspector storageType) {
        if ( ! storageType.valid()) return NodeResources.StorageType.getDefault(); // TODO: Remove this line after December 2019
        switch (storageType.asString()) {
            case "remote" : return NodeResources.StorageType.remote;
            case "local" : return NodeResources.StorageType.local;
            case "any" : return NodeResources.StorageType.any;
            default: throw new IllegalStateException("Illegal storage-type value '" + storageType.asString() + "'");
        }
    }

    private static String storageTypeToString(NodeResources.StorageType storageType) {
        switch (storageType) {
            case remote : return "remote";
            case local : return "local";
            case any : return "any";
            default: throw new IllegalStateException("Illegal storage-type value '" + storageType + "'");
        }
    }

}
