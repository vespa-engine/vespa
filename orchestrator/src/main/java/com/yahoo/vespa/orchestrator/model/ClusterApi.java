// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;

import java.util.Optional;

public interface ClusterApi {

    ApplicationApi getApplication();

    NodeGroup getNodeGroup();

    String clusterInfo();
    ClusterId clusterId();
    ServiceType serviceType();
    boolean isStorageCluster();

    /** Returns the reasons no services are up in the implied group, or empty if some services are up. */
    Optional<SuspensionReasons> reasonsForNoServicesInGroupIsUp();
    boolean noServicesOutsideGroupIsDown();

    int percentageOfServicesDown();
    int percentageOfServicesDownIfGroupIsAllowedToBeDown();

    Optional<StorageNode> storageNodeInGroup();
    Optional<StorageNode> upStorageNodeInGroup();

    String downDescription();

}
