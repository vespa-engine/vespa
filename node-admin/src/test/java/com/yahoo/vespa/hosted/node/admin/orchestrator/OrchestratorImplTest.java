// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;

import com.yahoo.vespa.orchestrator.restapi.HostApi;
import com.yahoo.vespa.orchestrator.restapi.HostSuspensionApi;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bakksjo
 * @author dybis
 */
public class OrchestratorImplTest {
    private HostApi hostApi;
    private OrchestratorImpl orchestrator;
    private HostSuspensionApi hostSuspensionApi;
    final String hostNameString = "host";
    final HostName hostName = new HostName(hostNameString);
    final List<String> hosts = new ArrayList<>();

    @Before
    public void before() {
        hostApi = mock(HostApi.class);
     //   final JaxRsStrategy<HostApi> hostApiClient = new LocalPassThroughJaxRsStrategy<>(hostApi);

        hostSuspensionApi = mock(HostSuspensionApi.class);
       // final JaxRsStrategy<HostSuspensionApi> hostSuspendClient = new LocalPassThroughJaxRsStrategy<>(hostSuspensionApi);

      //  orchestrator = new OrchestratorImpl(hostApiClient, hostSuspendClient);
    }
/*
    @Test
    public void testSingleOperations() throws Exception {
        // Make resume and suspend always succeed.
        when(hostApi.resume(hostNameString)).thenReturn(new UpdateHostResponse(hostNameString, null));
        when(hostApi.suspend(hostNameString)).thenReturn(new UpdateHostResponse(hostNameString, null));

        orchestrator.resume(hostName);
        verify(hostApi, times(1)).resume(hostNameString);

        orchestrator.suspend(hostName);
        verify(hostApi, times(1)).suspend(hostNameString);

        hosts.add(hostNameString);
    }

    @Test
    public void testListSuspendOk() throws Exception {
        hosts.add(hostNameString);
        when(hostSuspensionApi.suspendAll(Mockito.any())).thenReturn(new BatchOperationResult(null));
        assertThat(orchestrator.suspend("parent", hosts), is(Optional.empty()));
    }

    @Test
    public void testListSuspendFailed() throws Exception {
        hosts.add(hostNameString);
        when(hostSuspensionApi.suspendAll(Mockito.any())).thenReturn(new BatchOperationResult("no no"));
        assertThat(orchestrator.suspend("parent", hosts), is(Optional.of("no no")));
    }*/
}
