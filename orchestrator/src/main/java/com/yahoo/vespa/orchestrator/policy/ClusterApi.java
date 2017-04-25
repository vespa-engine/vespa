// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.NodeGroup;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

interface ClusterApi {
    NodeGroup getNodeGroup();

    ClusterId clusterId();
    ServiceType serviceType();
    boolean isStorageCluster();

    boolean noServicesInGroupIsUp();

    boolean noServicesOutsideGroupIsDown();

    int percentageOfServicesDown();
    int percentageOfServicesDownIfGroupIsAllowedToBeDown();

    Set<ServiceInstance<ServiceMonitorStatus>> servicesDownAndNotInGroup();

    List<HostName> nodesAllowedToBeDownNotInGroup();

    Optional<HostName> storageNodeInGroup();
    Optional<HostName> upStorageNodeInGroup();

    String clusterInfo();
}
