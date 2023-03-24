// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    int percentageOfServicesDownOutsideGroup();
    int percentageOfServicesDownIfGroupIsAllowedToBeDown();

    Optional<StorageNode> storageNodeInGroup();

    String downDescription();
}
