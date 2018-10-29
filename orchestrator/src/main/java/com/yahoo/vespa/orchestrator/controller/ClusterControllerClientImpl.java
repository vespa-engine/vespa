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

    // On setNodeState calls against the CC ensemble.
    //
    // We'd like to set a timeout for the request to the first CC such that if the first
    // CC is faulty, there's sufficient time to send the request to the second and third CC.
    // The timeouts would be:
    //   timeout(1. request) = SHARE_REMAINING_TIME * T
    //   timeout(2. request) = SHARE_REMAINING_TIME * T * (1 - SHARE_REMAINING_TIME)
    //   timeout(3. request) = SHARE_REMAINING_TIME * T * (1 - SHARE_REMAINING_TIME)^2
    //
    // Using a share of 50% gives approximately:
    //   timeout(1. request) = T * 0.5
    //   timeout(2. request) = T * 0.25
    //   timeout(3. request) = T * 0.125
    //
    // which seems fine
    public static final float SHARE_REMAINING_TIME = 0.5f;

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
                    context.getSuboperationTimeoutInSeconds(SHARE_REMAINING_TIME),
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
                    context.getSuboperationTimeoutInSeconds(SHARE_REMAINING_TIME),
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
