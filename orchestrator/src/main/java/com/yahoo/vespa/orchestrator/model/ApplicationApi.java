// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.List;
import java.util.function.Predicate;

/**
 * The API a Policy has access to
 */
public interface ApplicationApi {

    /**
     * Returns the 3-part application ID of the form tenant:name:instance.
     */
    ApplicationId applicationId();

    /**
     * The policy acts on some subset of nodes in the application.
     */
    NodeGroup getNodeGroup();

    List<ClusterApi> getClusters();

    ApplicationInstanceStatus getApplicationStatus();

    void setHostState(OrchestratorContext context, HostName hostName, HostStatus status);
    List<HostName> getNodesInGroupWith(Predicate<HostStatus> statusPredicate);
    default List<HostName> getNodesInGroupWithStatus(HostStatus requiredStatus) {
        return getNodesInGroupWith(status -> status == requiredStatus);
    }

    List<StorageNode> getStorageNodesInGroupInClusterOrder();
    List<StorageNode> getNoRemarksStorageNodesInGroupInClusterOrder();
    List<StorageNode> getSuspendedStorageNodesInGroupInReverseClusterOrder();
}
