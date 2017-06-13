// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import java.io.IOException;

/**
 * @author bakksjo
 */
public interface ClusterControllerClient {

    /**
     * Requests that a cluster controller sets the requested node to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     */
    ClusterControllerStateResponse setNodeState(int storageNodeIndex, ClusterControllerNodeState wantedState) throws IOException;

    /**
     * Requests that a cluster controller sets all nodes in the cluster to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     */
    ClusterControllerStateResponse setApplicationState(ClusterControllerNodeState wantedState) throws IOException;

}
