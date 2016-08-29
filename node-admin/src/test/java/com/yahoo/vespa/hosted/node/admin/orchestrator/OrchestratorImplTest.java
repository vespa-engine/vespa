package com.yahoo.vespa.hosted.node.admin.orchestrator;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.node.admin.util.ConfigServerHttpRequestExecutor;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.HostStateChangeDenialReason;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author valerijf
 */
public class OrchestratorImplTest {
    private static final HostName hostName = new HostName("host123.yahoo.com");
    private ConfigServerHttpRequestExecutor requestExecutor;
    private OrchestratorImpl orchestrator;

    @Before
    public void setup() {
        requestExecutor = mock(ConfigServerHttpRequestExecutor.class);
        orchestrator = new OrchestratorImpl(requestExecutor);
    }

    @Test
    public void testSuspendCall() {
        when(requestExecutor.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                Optional.empty(),
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName.s(), null));

        boolean response = orchestrator.suspend(hostName);
        assertTrue("Expected Orchestrator to approve", response);
    }

    @Test
    public void testSuspendCallWithFailureReason() {
        when(requestExecutor.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                Optional.empty(),
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName.s(), new HostStateChangeDenialReason("hostname", "service", "fail")));

        boolean response = orchestrator.suspend(hostName);
        assertFalse("Expected Orchestrator to deny when presented with HostChangeDenialReason", response);
    }

    @Test
    public void testSuspendCallWithNotFound() {
        when(requestExecutor.put(
                any(String.class),
                any(Integer.class),
                any(),
                any()
        )).thenThrow(requestExecutor.new NotFoundException("Not Found"));

        boolean response = orchestrator.suspend(hostName);
        assertTrue("Expected Orchestrator to respond with true even when NotFoundException is thrown", response);
    }

    @Test
    public void testSuspendCallWithSomeOtherException() {
        when(requestExecutor.put(
                any(String.class),
                any(Integer.class),
                any(),
                any()
        )).thenThrow(new RuntimeException("Some parameter was wrong"));

        boolean response = orchestrator.suspend(hostName);
        assertFalse("Expected Orchestrator to respond with false when some other exception is thrown", response);
    }


    @Test
    public void testResumeCall() {
        when(requestExecutor.delete(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName.s(), null));

        boolean response = orchestrator.resume(hostName);
        assertTrue("Expected Orchestrator to approve", response);
    }

    @Test
    public void testResumeCallWithFailureReason() {
        when(requestExecutor.delete(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_API + "/" + hostName+ "/suspended",
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                UpdateHostResponse.class
        )).thenReturn(new UpdateHostResponse(hostName.s(), new HostStateChangeDenialReason("hostname", "service", "fail")));

        boolean response = orchestrator.resume(hostName);
        assertFalse("Expected Orchestrator to deny when presented with HostChangeDenialReason", response);
    }

    @Test
    public void testResumeCallWithNotFound() {
        when(requestExecutor.delete(
                any(String.class),
                any(Integer.class),
                any()
        )).thenThrow(requestExecutor.new NotFoundException("Not Found"));

        boolean response = orchestrator.resume(hostName);
        assertTrue("Expected Orchestrator to respond with true even when NotFoundException is thrown", response);
    }

    @Test
    public void testResumeCallWithSomeOtherException() {
        when(requestExecutor.put(
                any(String.class),
                any(Integer.class),
                any(),
                any()
        )).thenThrow(new RuntimeException("Some parameter was wrong"));

        boolean response = orchestrator.suspend(hostName);
        assertFalse("Expected Orchestrator to respond with false when some other exception is thrown", response);
    }


    @Test
    public void testBatchSuspendCall() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");

        when(requestExecutor.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenReturn(BatchOperationResult.successResult());

        Optional<String> response = orchestrator.suspend(parentHostName, hostNames);
        assertFalse("Expected failureReason to be empty", response.isPresent());
    }

    @Test
    public void testBatchSuspendCallWithFailureReason() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
        String failureReason = "Failed to suspend";

        when(requestExecutor.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenReturn(new BatchOperationResult(failureReason));

        Optional<String> response = orchestrator.suspend(parentHostName, hostNames);
        assertEquals("Expected failureReason to be empty", response, Optional.of(failureReason));
    }

    @Test
    public void testBatchSuspendCallWithSomeException() {
        String parentHostName = "host1.test.yahoo.com";
        List<String> hostNames = Arrays.asList("a1.host1.test.yahoo.com", "a2.host1.test.yahoo.com");
        String exceptionMessage = "Exception: Something crashed!";

        when(requestExecutor.put(
                OrchestratorImpl.ORCHESTRATOR_PATH_PREFIX_HOST_SUSPENSION_API,
                OrchestratorImpl.HARDCODED_ORCHESTRATOR_PORT,
                Optional.of(new BatchHostSuspendRequest(parentHostName, hostNames)),
                BatchOperationResult.class
        )).thenThrow(new RuntimeException(exceptionMessage));

        Optional<String> response = orchestrator.suspend(parentHostName, hostNames);
        assertEquals("Expected failureReason to be empty", response, Optional.of(exceptionMessage));
    }
}
