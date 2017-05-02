// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.util.List;

/**
 * The API the policy has access too
 */
public interface ApplicationApi {
    String applicationInfo();
    List<ClusterApi> getClustersThatAreOnAtLeastOneNodeInGroup();

    HostStatus getHostStatus(HostName hostName);
    void setHostState(HostName hostName, HostStatus status);

    List<HostName> getUpStorageNodesInGroupInClusterOrder();
    List<HostName> getNodesInGroupWithNoRemarks();
    List<HostName> getStorageNodesWithNoRemarksInGroupInReverseOrder();

    List<HostName> getNodesInGroupNotAllowedToBeDown();


    // TODO: Remove
    ApplicationInstance<?> getApplicationInstance();

}
