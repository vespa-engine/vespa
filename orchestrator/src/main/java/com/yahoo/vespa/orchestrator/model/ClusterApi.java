// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;

import java.util.Optional;

public interface ClusterApi {

    ApplicationApi getApplication();

    NodeGroup getNodeGroup();

    String clusterInfo();
    ClusterId clusterId();
    ServiceType serviceType();

    /** Some human-readable string naming the service(s) to a human reader. */
    String serviceDescription(boolean plural);

    boolean isStorageCluster();

    boolean isConfigServerLike();

    /** Returns the non-empty reasons for why all services are down, or otherwise empty. */
    Optional<SuspensionReasons> allServicesDown();

    boolean noServicesOutsideGroupIsDown() throws HostStateChangeDeniedException;

    /** Returns the number of services currently in the cluster, plus the number of missing services. */
    int size();

    int servicesDownOutsideGroup();
    default int percentageOfServicesDownOutsideGroup() { return sizePercentageOf(servicesDownOutsideGroup()); }
    int servicesDownIfGroupIsAllowedToBeDown();
    default int percentageOfServicesDownIfGroupIsAllowedToBeDown() { return sizePercentageOf(servicesDownIfGroupIsAllowedToBeDown()); }

    ClusterPolicyOverride clusterPolicyOverride();

    Optional<StorageNode> storageNodeInGroup();

    String downDescription();

    private int sizePercentageOf(int count) { return (int) Math.round(100.0 * count / size()); }
}
