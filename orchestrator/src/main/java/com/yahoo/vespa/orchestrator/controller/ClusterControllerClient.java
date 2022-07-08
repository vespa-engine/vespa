// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.orchestrator.ApplicationStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.model.ContentService;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;

/**
 * @author bakksjo
 */
public interface ClusterControllerClient {

    /**
     * Requests that a cluster controller sets the requested node to the requested state.
     *
     * @return false is this was a probe operation, and permission would be denied.
     * @throws HostStateChangeDeniedException if operation fails, or is otherwise disallowed.
     */
    boolean trySetNodeState(OrchestratorContext context, HostName host, int storageNodeIndex,
                            ClusterControllerNodeState wantedState, ContentService contentService, boolean force)
            throws HostStateChangeDeniedException;

    /**
     * Requests that a cluster controller sets the requested node to the requested state.
     *
     * @throws HostStateChangeDeniedException if operation fails, or is disallowed.
     */
    void setNodeState(OrchestratorContext context, HostName host, int storageNodeIndex,
                      ClusterControllerNodeState wantedState, ContentService contentService, boolean force)
            throws HostStateChangeDeniedException;

    /**
     * Requests that a cluster controller sets all nodes in the cluster to the requested state.
     *
     * @throws ApplicationStateChangeDeniedException if operation fails, or is disallowed.
     */
    void setApplicationState(OrchestratorContext context, ApplicationInstanceId applicationId,
                             ClusterControllerNodeState wantedState) throws ApplicationStateChangeDeniedException;

}
