// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.LocalPassThroughJaxRsStrategy;
import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import org.junit.Test;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 */
public class OrchestratorImplTest {
    @Test
    public void redundantResumesAreFilteredOut() throws Exception {
        final HostApi hostApi = mock(HostApi.class);
        final JaxRsStrategy<HostApi> hostApiClient = new LocalPassThroughJaxRsStrategy<>(hostApi);
        final OrchestratorImpl orchestrator = new OrchestratorImpl(hostApiClient);
        final String hostNameString = "host";
        final HostName hostName = new HostName(hostNameString);

        // Make resume and suspend always succeed.
        when(hostApi.resume(hostNameString)).thenReturn(new UpdateHostResponse(hostNameString, null));
        when(hostApi.suspend(hostNameString)).thenReturn(new UpdateHostResponse(hostNameString, null));

        orchestrator.resume(hostName);
        verify(hostApi, times(1)).resume(hostNameString);

        // A subsequent resume does not cause a network trip.
        orchestrator.resume(hostName);
        verify(hostApi, times(1)).resume(anyString());

        orchestrator.suspend(hostName);
        verify(hostApi, times(1)).suspend(hostNameString);

        orchestrator.resume(hostName);
        verify(hostApi, times(2)).resume(hostNameString);

        // A subsequent resume does not cause a network trip.
        orchestrator.resume(hostName);
        verify(hostApi, times(2)).resume(anyString());
    }
}
