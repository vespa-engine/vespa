// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.orchestrator;

import com.yahoo.vespa.hosted.node.admin.configserver.ConfigServerApiImpl;
import com.yahoo.vespa.hosted.node.admin.configserver.HttpException;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
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
    void testSuspendCall() {
        when(configServerApi.put(
                eq(OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended"),
                eq(Optional.empty()),
                eq(UpdateHostResponse.class),
                any()
        )).thenReturn(new UpdateHostResponse(hostName, null));

        orchestrator.suspend(hostName);
    }

    @Test
    void testSuspendCallWithFailureReason() {
        assertThrows(OrchestratorException.class, () -> {
            when(configServerApi.put(
                    eq(OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended"),
                    eq(Optional.empty()),
                    eq(UpdateHostResponse.class),
                    any()
            )).thenReturn(new UpdateHostResponse(hostName, new HostStateChangeDenialReason("hostname", "fail")));

            orchestrator.suspend(hostName);
        });
    }

    @Test
    void testSuspendCallWithNotFound() {
        assertThrows(OrchestratorNotFoundException.class, () -> {
            when(configServerApi.put(any(String.class), any(), any(), any()))
                    .thenThrow(new HttpException.NotFoundException("Not Found"));

            orchestrator.suspend(hostName);
        });
    }

    @Test
    void testSuspendCallWithSomeOtherException() {
        assertThrows(RuntimeException.class, () -> {
            when(configServerApi.put(any(String.class), any(), any(), any()))
                    .thenThrow(new RuntimeException("Some parameter was wrong"));

            orchestrator.suspend(hostName);
        });
    }


    @Test
    void testResumeCall() {
        when(configServerApi.delete(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended",
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName, null));

        orchestrator.resume(hostName);
    }

    @Test
    void testResumeCallWithFailureReason() {
        assertThrows(OrchestratorException.class, () -> {
            when(configServerApi.delete(
                    OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName + "/suspended",
                    UpdateHostResponse.class
            )).thenReturn(new UpdateHostResponse(hostName, new HostStateChangeDenialReason("hostname", "fail")));

            orchestrator.resume(hostName);
        });
    }

    @Test
    void testResumeCallWithNotFound() {
        assertThrows(OrchestratorNotFoundException.class, () -> {
            when(configServerApi.delete(
                    any(String.class),
                    any()
            )).thenThrow(new HttpException.NotFoundException("Not Found"));

            orchestrator.resume(hostName);
        });
    }

    @Test
    void testResumeCallWithSomeOtherException() {
        assertThrows(RuntimeException.class, () -> {
            when(configServerApi.put(any(String.class), any(), any(), any()))
                    .thenThrow(new RuntimeException("Some parameter was wrong"));

            orchestrator.suspend(hostName);
        });
    }

    @Test
    void testBatchSuspendCall() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = List.of("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");

        when(configServerApi.put(
                eq("/orchestrator/v1/suspensions/hosts/host1.test.yahoo.com?hostname=a1.host1.test.yahoo.com&hostname=a2.host1.test.yahoo.com"),
                eq(Optional.empty()),
                eq(BatchOperationResult.class),
                any()
        )).thenReturn(BatchOperationResult.successResult());

        orchestrator.suspend(parentHostName, hostNames);
    }

    @Test
    void testBatchSuspendCallWithFailureReason() {
        assertThrows(OrchestratorException.class, () -> {
            String parentHostName = "host1.test.yahoo.com";
            List<String> hostNames = List.of("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
            String failureReason = "Failed to suspend";

            when(configServerApi.put(
                    eq("/orchestrator/v1/suspensions/hosts/host1.test.yahoo.com?hostname=a1.host1.test.yahoo.com&hostname=a2.host1.test.yahoo.com"),
                    eq(Optional.empty()),
                    eq(BatchOperationResult.class),
                    any()
            )).thenReturn(new BatchOperationResult(failureReason));

            orchestrator.suspend(parentHostName, hostNames);
        });
    }

    @Test
    void testBatchSuspendCallWithSomeException() {
        assertThrows(RuntimeException.class, () -> {
            String parentHostName = "host1.test.yahoo.com";
            List<String> hostNames = List.of("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
            String exceptionMessage = "Exception: Something crashed!";

            when(configServerApi.put(
                    eq("/orchestrator/v1/suspensions/hosts/host1.test.yahoo.com?hostname=a1.host1.test.yahoo.com&hostname=a2.host1.test.yahoo.com"),
                    eq(Optional.empty()),
                    eq(BatchOperationResult.class),
                    any()
            )).thenThrow(new RuntimeException(exceptionMessage));

            orchestrator.suspend(parentHostName, hostNames);
        });
    }

}
