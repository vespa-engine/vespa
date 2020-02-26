// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;

/**
 * Defines the resource limits for nodes in various zones
 *
 * @author bratseth
 */
public class NodeResourceLimits {

    private final Zone zone;

    public NodeResourceLimits(Zone zone) {
        this.zone = zone;
    }

    public int minMemoryGb(ClusterSpec.Type clusterType) {
        if (zone.system() == SystemName.dev) return 1; // Allow small containers in dev system
        if (clusterType == ClusterSpec.Type.admin) return 2;
        return 4;
    }

    public NodeResources enlargeToLegal(NodeResources resources, ClusterSpec.Type clusterType) {
        return resources.withMemoryGb(Math.max(minMemoryGb(clusterType), resources.memoryGb()));
    }

}
