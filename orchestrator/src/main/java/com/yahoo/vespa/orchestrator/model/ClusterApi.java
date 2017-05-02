// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.Optional;

public interface ClusterApi {
    NodeGroup getNodeGroup();

    String clusterInfo();
    ClusterId clusterId();
    ServiceType serviceType();
    boolean isStorageCluster();

    boolean noServicesInGroupIsUp();
    boolean noServicesOutsideGroupIsDown();

    int percentageOfServicesDown();
    int percentageOfServicesDownIfGroupIsAllowedToBeDown();

    Optional<StorageNode> storageNodeInGroup();
    Optional<StorageNode> upStorageNodeInGroup();

    String servicesDownAndNotInGroupDescription();
    String nodesAllowedToBeDownNotInGroupDescription();
}
