// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApiImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author freva
 */
public class OrchestratorImplTest {
    private static final String hostName = "host123.yahoo.com";

    private final ConfigServerApiImpl configServerApi = mock(ConfigServerApiImpl.class);
    private final OrchestratorImpl orchestrator = new OrchestratorImpl(configServerApi);

    @Test
    public void testSuspendCall() {
        when(configServerApi.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                Optional.empty(),
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName, null));

        orchestrator.suspend(hostName);
    }

    @Test(expected=OrchestratorException.class)
    public void testSuspendCallWithFailureReason() {
        when(configServerApi.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                Optional.empty(),
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName, new HostStateChangeDenialReason("hostname", "fail")));

        orchestrator.suspend(hostName);
    }

    @Test(expected=OrchestratorNotFoundException.class)
    public void testSuspendCallWithNotFound() {
        when(configServerApi.put(
                any(String.class),
                any(),
                any()
        )).thenThrow(new HttpException.NotFoundException("Not Found"));

        orchestrator.suspend(hostName);
    }

    @Test(expected=RuntimeException.class)
    public void testSuspendCallWithSomeOtherException() {
        when(configServerApi.put(
                any(String.class),
                any(),
                any()
        )).thenThrow(new RuntimeException("Some parameter was wrong"));

        orchestrator.suspend(hostName);
    }


    @Test
    public void testResumeCall() {
        when(configServerApi.delete(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName, null));

        orchestrator.resume(hostName);
    }

    @Test(expected=OrchestratorException.class)
    public void testResumeCallWithFailureReason() {
        when(configServerApi.delete(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName, new HostStateChangeDenialReason("hostname", "fail")));

        orchestrator.resume(hostName);
    }

    @Test(expected=OrchestratorNotFoundException.class)
    public void testResumeCallWithNotFound() {
        when(configServerApi.delete(
                any(String.class),
                any()
        )).thenThrow(new HttpException.NotFoundException("Not Found"));

        orchestrator.resume(hostName);
    }

    @Test(expected=RuntimeException.class)
    public void testResumeCallWithSomeOtherException() {
        when(configServerApi.put(
                any(String.class),
                any(),
                any()
        )).thenThrow(new RuntimeException("Some parameter was wrong"));

        orchestrator.suspend(hostName);
    }


    @Test
    public void testBatchSuspendCall() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");

        when(configServerApi.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenReturn(BatchOperationResult.successResult());

        orchestrator.suspend(parentHostName, hostNames);
    }

    @Test(expected=OrchestratorException.class)
    public void testBatchSuspendCallWithFailureReason() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
        String failureReason = "Failed to suspend";

        when(configServerApi.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenReturn(new BatchOperationResult(failureReason));

        orchestrator.suspend(parentHostName, hostNames);
    }

    @Test(expected=RuntimeException.class)
    public void testBatchSuspendCallWithSomeException() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
        String exceptionMessage = "Exception: Something crashed!";

        when(configServerApi.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenThrow(new RuntimeException(exceptionMessage));

        orchestrator.suspend(parentHostName, hostNames);
    }
}
