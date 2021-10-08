// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.LocalPassThroughJaxRsStrategy;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClusterControllerClientTest {
    private static final String CLUSTER_NAME = "clusterName";
    private static final int STORAGE_NODE_INDEX = 0;

    private final ClusterControllerJaxRsApi clusterControllerApi = mock(ClusterControllerJaxRsApi.class);
    private final JaxRsStrategy<ClusterControllerJaxRsApi> strategyMock = new LocalPassThroughJaxRsStrategy<>(clusterControllerApi);
    private final ClusterControllerClient clusterControllerClient = new ClusterControllerClientImpl(strategyMock, CLUSTER_NAME);
    private final ClusterControllerNodeState wantedState = ClusterControllerNodeState.MAINTENANCE;
    private final OrchestratorContext context = mock(OrchestratorContext.class);
    private final ClusterControllerClientTimeouts timeouts = mock(ClusterControllerClientTimeouts.class);
    private final ClusterControllerStateRequest.State state = new ClusterControllerStateRequest.State(wantedState, ClusterControllerClientImpl.REQUEST_REASON);

    @Before
    public void setUp() {
        when(context.getClusterControllerTimeouts()).thenReturn(timeouts);
        when(context.isProbe()).thenReturn(false);
        when(timeouts.getServerTimeoutOrThrow()).thenReturn(Duration.ofSeconds(1));
    }

    @Test
    public void correctParametersArePassedThrough() throws IOException {
        setNodeStateAndVerify(null);
    }

    @Test
    public void probingIsCorrectlyPassedThrough() throws IOException {
        when(context.isProbe()).thenReturn(true);
        setNodeStateAndVerify(true);
    }

    private void setNodeStateAndVerify(Boolean expectedProbe) throws IOException {
        clusterControllerClient.setNodeState(context, STORAGE_NODE_INDEX, wantedState);

        final ClusterControllerStateRequest expectedNodeStateRequest = new ClusterControllerStateRequest(
                state, ClusterControllerStateRequest.Condition.SAFE, expectedProbe);

        verify(clusterControllerApi, times(1))
                .setNodeState(
                        eq(CLUSTER_NAME),
                        eq(STORAGE_NODE_INDEX),
                        eq(1.0f),
                        eq(expectedNodeStateRequest));
    }
}
