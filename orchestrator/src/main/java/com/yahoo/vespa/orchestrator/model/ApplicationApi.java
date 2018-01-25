// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.List;

/**
 * The API a Policy has access to
 */
public interface ApplicationApi {
    /**
     * @return The 3-part application ID of the form tenant:name:instance.
     */
    ApplicationId applicationId();

    /**
     * The policy acts on some subset of nodes in the application.
     */
    NodeGroup getNodeGroup();

    List<ClusterApi> getClusters();

    ApplicationInstanceStatus getApplicationStatus();

    void setHostState(HostName hostName, HostStatus status);
    List<HostName> getNodesInGroupWithStatus(HostStatus status);

    List<StorageNode> getStorageNodesInGroupInClusterOrder();
    List<StorageNode> getUpStorageNodesInGroupInClusterOrder();
    List<StorageNode> getStorageNodesAllowedToBeDownInGroupInReverseClusterOrder();
}
