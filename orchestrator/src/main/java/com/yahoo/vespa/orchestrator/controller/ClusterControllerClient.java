// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.vespa.orchestrator.OrchestratorContext;

import java.io.IOException;

/**
 * @author bakksjo
 */
public interface ClusterControllerClient {

    /**
     * Requests that a cluster controller sets the requested node to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     * @throws UncheckedTimeoutException if operation times out
     */
    ClusterControllerStateResponse setNodeState(OrchestratorContext context, int storageNodeIndex, ClusterControllerNodeState wantedState) throws IOException;

    /**
     * Requests that a cluster controller sets all nodes in the cluster to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     * @throws UncheckedTimeoutException if operation times out
     */
    ClusterControllerStateResponse setApplicationState(OrchestratorContext context, ClusterControllerNodeState wantedState) throws IOException;

}
