// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.client.VespaJerseyJaxRsClientFactory;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RetryingClusterControllerClientFactoryTest {
    private final Clock clock = new ManualClock();

    @Test
    @SuppressWarnings("removal") // VespaJerseyJaxRsClientFactory
    public void verifyJerseyCallForSetNodeState() throws IOException {
        VespaJerseyJaxRsClientFactory clientFactory = mock(VespaJerseyJaxRsClientFactory.class);
        ClusterControllerJaxRsApi api = mock(ClusterControllerJaxRsApi.class);
        when(clientFactory.createClient(any())).thenReturn(api);
        RetryingClusterControllerClientFactory factory = new RetryingClusterControllerClientFactory(clientFactory);
        String clusterName = "clustername";
        HostName host1 = new HostName("host1");
        HostName host2 = new HostName("host2");
        HostName host3 = new HostName("host3");
        List<HostName> clusterControllers = Arrays.asList(host1, host2, host3);
        ClusterControllerClient client = factory.createClient(clusterControllers, clusterName);
        OrchestratorContext context = OrchestratorContext.createContextForSingleAppOp(clock);
        int storageNode = 2;
        ClusterControllerNodeState wantedState = ClusterControllerNodeState.MAINTENANCE;
        client.setNodeState(context, storageNode, wantedState);

        ArgumentCaptor<ClusterControllerStateRequest> requestCaptor = ArgumentCaptor.forClass(ClusterControllerStateRequest.class);

        verify(api, times(1)).setNodeState(eq(clusterName), eq(storageNode), eq(9.6f), requestCaptor.capture());
        ClusterControllerStateRequest request = requestCaptor.getValue();
        assertEquals(ClusterControllerStateRequest.Condition.SAFE, request.condition);
        Map<String, Object> expectedState = new HashMap<>();
        expectedState.put("user", new ClusterControllerStateRequest.State(wantedState, "Orchestrator"));
        assertEquals(expectedState, request.state);
    }
}
