// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.orchestrator.OrchestratorContext;

import java.io.IOException;

/**
 * Default implementation of the ClusterControllerClient.
 *
 * @author smorgrav
 */
public class ClusterControllerClientImpl implements ClusterControllerClient{

    public static final String REQUEST_REASON = "Orchestrator";

    private final JaxRsStrategy<ClusterControllerJaxRsApi> clusterControllerApi;
    private final String clusterName;

    public ClusterControllerClientImpl(JaxRsStrategy<ClusterControllerJaxRsApi> clusterControllerApi,
                                       String clusterName) {
        this.clusterName = clusterName;
        this.clusterControllerApi = clusterControllerApi;
    }

    /**
     * Requests that a cluster controller sets the requested node to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     */
    @Override
    public ClusterControllerStateResponse setNodeState(OrchestratorContext context,
                                                       int storageNodeIndex,
                                                       ClusterControllerNodeState wantedState) throws IOException {
        ClusterControllerStateRequest.State state = new ClusterControllerStateRequest.State(wantedState, REQUEST_REASON);
        ClusterControllerStateRequest stateRequest = new ClusterControllerStateRequest(state, ClusterControllerStateRequest.Condition.SAFE);

        try {
            return clusterControllerApi.apply(api -> api.setNodeState(
                    clusterName,
                    storageNodeIndex,
                    context.getSuboperationTimeoutInSeconds().orElse(null),
                    stateRequest)
            );
        } catch (IOException e) {
            String message = String.format(
                    "Giving up setting %s for storage node with index %d in cluster %s",
                    stateRequest,
                    storageNodeIndex,
                    clusterName);

            throw new IOException(message, e);
        }
    }

    /**
     * Requests that a cluster controller sets all nodes in the cluster to the requested state.
     *
     * @throws IOException if there was a problem communicating with the cluster controller
     */
    @Override
    public ClusterControllerStateResponse setApplicationState(
            OrchestratorContext context,
            final ClusterControllerNodeState wantedState) throws IOException {
        final ClusterControllerStateRequest.State state = new ClusterControllerStateRequest.State(wantedState, REQUEST_REASON);
        final ClusterControllerStateRequest stateRequest = new ClusterControllerStateRequest(state, ClusterControllerStateRequest.Condition.FORCE);

        try {
            return clusterControllerApi.apply(api -> api.setClusterState(
                    clusterName,
                    context.getSuboperationTimeoutInSeconds().orElse(null),
                    stateRequest));
        } catch (IOException e) {
            final String message = String.format(
                    "Giving up setting %s for cluster %s",
                    stateRequest,
                    clusterName);

            throw new IOException(message, e);
        }
    }
}
