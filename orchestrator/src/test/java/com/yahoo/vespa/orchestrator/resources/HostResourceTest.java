// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.BatchHostNameNotFoundException;
import com.yahoo.vespa.orchestrator.BatchInternalErrorException;
import com.yahoo.vespa.orchestrator.DummyAntiServiceMonitor;
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.Policy;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.orchestrator.status.ZkStatusService;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class HostResourceTest {
    private static final Clock clock = mock(Clock.class);
    private static final int SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS = 0;
    private static final TenantId TENANT_ID = new TenantId("tenantId");
    private static final ApplicationInstanceId APPLICATION_INSTANCE_ID = new ApplicationInstanceId("applicationId");
    private static final ServiceMonitor serviceMonitor = mock(ServiceMonitor.class);
    private static final StatusService EVERY_HOST_IS_UP_HOST_STATUS_SERVICE = new ZkStatusService(
            new MockCurator(), mock(Metric.class), new TestTimer(), new DummyAntiServiceMonitor());
    private static final ApplicationApiFactory applicationApiFactory = new ApplicationApiFactory(3, clock);

    static {
        when(serviceMonitor.getApplication(any(HostName.class)))
                .thenReturn(Optional.of(
                        new ApplicationInstance(
                                TENANT_ID,
                                APPLICATION_INSTANCE_ID,
                                Set.of())));
    }

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    private static final ServiceMonitor alwaysEmptyServiceMonitor = new ServiceMonitor() {
        private final ServiceModel emptyServiceModel = new ServiceModel(Map.of());

        @Override
        public ServiceModel getServiceModelSnapshot() {
            return emptyServiceModel;
        }
    };

    private static class AlwaysAllowPolicy implements Policy {
        @Override
        public SuspensionReasons grantSuspensionRequest(OrchestratorContext context, ApplicationApi applicationApi) {
            return SuspensionReasons.nothingNoteworthy();
        }

        @Override
        public void releaseSuspensionGrant(OrchestratorContext context, ApplicationApi application) {
        }

        @Override
        public void acquirePermissionToRemove(OrchestratorContext context, ApplicationApi applicationApi) {
        }

        @Override
        public void releaseSuspensionGrant(
                OrchestratorContext context, ApplicationInstance applicationInstance,
                HostName hostName,
                ApplicationLock hostStatusRegistry) {
        }
    }

    private final OrchestratorImpl alwaysAllowOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
            serviceMonitor,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
            clock,
            applicationApiFactory,
            flagSource);

    private final OrchestratorImpl hostNotFoundOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
            alwaysEmptyServiceMonitor,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
            clock,
            applicationApiFactory,
            flagSource);

    private final UriInfo uriInfo = mock(UriInfo.class);

    @Before
    public void setUp() {
        when(clock.instant()).thenReturn(Instant.now());
    }

    @Test
    public void returns_200_on_success() {
        HostResource hostResource =
                new HostResource(alwaysAllowOrchestrator, uriInfo);

        final String hostName = "hostname";

        UpdateHostResponse response = hostResource.suspend(hostName);

        assertEquals(hostName, response.hostname());
    }

    @Test
    public void returns_200_on_success_batch() {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchOperationResult response = hostSuspensionResource.suspendAll("parentHostname",
                                                                          Arrays.asList("hostname1", "hostname2"));
        assertTrue(response.success());
    }

    @Test
    public void returns_200_empty_batch() {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchOperationResult response = hostSuspensionResource.suspendAll("parentHostname", List.of());
        assertTrue(response.success());
    }

    @Test
    public void throws_404_when_host_unknown() {
        try {
            HostResource hostResource =
                    new HostResource(hostNotFoundOrchestrator, uriInfo);
            hostResource.suspend("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertEquals(404, w.getResponse().getStatus());
        }
    }

    // Note: Missing host is 404 for a single-host, but 400 for multi-host (batch).
    // This is so because the hostname is part of the URL path for single-host, while the
    // hostnames are part of the request body for multi-host.
    @Test
    public void throws_400_when_host_unknown_for_batch() {
        try {
            HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(hostNotFoundOrchestrator);
            hostSuspensionResource.suspendAll("parentHostname", Arrays.asList("hostname1", "hostname2"));
            fail();
        } catch (WebApplicationException w) {
            assertEquals(400, w.getResponse().getStatus());
        }
    }

    private static class AlwaysFailPolicy implements Policy {
        @Override
        public SuspensionReasons grantSuspensionRequest(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException {
            throw newHostStateChangeDeniedException();
        }

        @Override
        public void releaseSuspensionGrant(OrchestratorContext context, ApplicationApi application) throws HostStateChangeDeniedException {
            throw newHostStateChangeDeniedException();
        }

        @Override
        public void acquirePermissionToRemove(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException {
            throw newHostStateChangeDeniedException();
        }

        @Override
        public void releaseSuspensionGrant(
                OrchestratorContext context, ApplicationInstance applicationInstance,
                HostName hostName,
                ApplicationLock hostStatusRegistry) throws HostStateChangeDeniedException {
            throw newHostStateChangeDeniedException();
        }

        private static HostStateChangeDeniedException newHostStateChangeDeniedException() {
            return new HostStateChangeDeniedException(
                    new HostName("some-host"),
                    "impossible-policy",
                    "This policy rejects all requests");
        }
    }

    @Test
    public void throws_409_when_request_rejected_by_policies() {
        final OrchestratorImpl alwaysRejectResolver = new OrchestratorImpl(
                new AlwaysFailPolicy(),
                new ClusterControllerClientFactoryMock(),
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
                serviceMonitor,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
                clock,
                applicationApiFactory,
                flagSource);

        try {
            HostResource hostResource = new HostResource(alwaysRejectResolver, uriInfo);
            hostResource.suspend("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertEquals(409, w.getResponse().getStatus());
        }
    }

    @Test
    public void throws_409_when_request_rejected_by_policies_for_batch() {
        final OrchestratorImpl alwaysRejectResolver = new OrchestratorImpl(
                new AlwaysFailPolicy(),
                new ClusterControllerClientFactoryMock(),
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
                serviceMonitor,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
                clock,
                applicationApiFactory,
                flagSource);

        try {
            HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysRejectResolver);
            hostSuspensionResource.suspendAll("parentHostname", Arrays.asList("hostname1", "hostname2"));
            fail();
        } catch (WebApplicationException w) {
            assertEquals(409, w.getResponse().getStatus());
        }
    }

    @Test(expected = BadRequestException.class)
    public void patch_state_may_throw_bad_request() {
        Orchestrator orchestrator = mock(Orchestrator.class);
        HostResource hostResource = new HostResource(orchestrator, uriInfo);

        String hostNameString = "hostname";
        PatchHostRequest request = new PatchHostRequest();
        request.state = "bad state";

        hostResource.patch(hostNameString, request);
    }

    @Test
    public void patch_works() throws OrchestrationException {
        Orchestrator orchestrator = mock(Orchestrator.class);
        HostResource hostResource = new HostResource(orchestrator, uriInfo);

        String hostNameString = "hostname";
        PatchHostRequest request = new PatchHostRequest();
        request.state = "NO_REMARKS";

        PatchHostResponse response = hostResource.patch(hostNameString, request);
        assertEquals(response.description, "ok");
        verify(orchestrator, times(1)).setNodeStatus(new HostName(hostNameString), HostStatus.NO_REMARKS);
    }

    @Test(expected = InternalServerErrorException.class)
    public void patch_handles_exception_in_orchestrator() throws OrchestrationException {
        Orchestrator orchestrator = mock(Orchestrator.class);
        HostResource hostResource = new HostResource(orchestrator, uriInfo);

        String hostNameString = "hostname";
        PatchHostRequest request = new PatchHostRequest();
        request.state = "NO_REMARKS";

        doThrow(new OrchestrationException("error")).when(orchestrator).setNodeStatus(new HostName(hostNameString), HostStatus.NO_REMARKS);
        hostResource.patch(hostNameString, request);
    }

    @Test
    public void getHost_works() throws Exception {
        Orchestrator orchestrator = mock(Orchestrator.class);
        HostResource hostResource = new HostResource(orchestrator, uriInfo);

        HostName hostName = new HostName("hostname");

        UriBuilder baseUriBuilder = mock(UriBuilder.class);
        when(uriInfo.getBaseUriBuilder()).thenReturn(baseUriBuilder);
        when(baseUriBuilder.path(any(String.class))).thenReturn(baseUriBuilder);
        when(baseUriBuilder.path(any(Class.class))).thenReturn(baseUriBuilder);
        URI uri = new URI("https://foo.com/bar");
        when(baseUriBuilder.build()).thenReturn(uri);

        ServiceInstance serviceInstance = new ServiceInstance(
                new ConfigId("configId"),
                hostName,
                ServiceStatus.UP);
        ServiceCluster serviceCluster = new ServiceCluster(
                new ClusterId("clusterId"),
                new ServiceType("serviceType"),
                Collections.singleton(serviceInstance));
        serviceInstance.setServiceCluster(serviceCluster);

        Host host = new Host(
                hostName,
                HostInfo.createSuspended(HostStatus.ALLOWED_TO_BE_DOWN, Instant.EPOCH),
                new ApplicationInstanceReference(
                        new TenantId("tenantId"),
                        new ApplicationInstanceId("applicationId")),
                Collections.singletonList(serviceInstance));
        when(orchestrator.getHost(hostName)).thenReturn(host);
        GetHostResponse response = hostResource.getHost(hostName.s());
        assertEquals("https://foo.com/bar", response.applicationUrl());
        assertEquals("hostname", response.hostname());
        assertEquals("ALLOWED_TO_BE_DOWN", response.state());
        assertEquals("1970-01-01T00:00:00Z", response.suspendedSince());
        assertEquals(1, response.services().size());
        assertEquals("clusterId", response.services().get(0).clusterId);
        assertEquals("configId", response.services().get(0).configId);
        assertEquals("UP", response.services().get(0).serviceStatus);
        assertEquals("serviceType", response.services().get(0).serviceType);
    }

    @Test
    public void throws_409_on_timeout() throws HostNameNotFoundException, HostStateChangeDeniedException {
        Orchestrator orchestrator = mock(Orchestrator.class);
        doThrow(new UncheckedTimeoutException("Timeout Message")).when(orchestrator).resume(any(HostName.class));

        try {
            HostResource hostResource = new HostResource(orchestrator, uriInfo);
            hostResource.resume("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertEquals(409, w.getResponse().getStatus());
            assertEquals("resume failed: Timeout Message [deadline]", w.getMessage());
        }
    }

    @Test
    public void throws_409_on_suspendAll_timeout() throws BatchHostStateChangeDeniedException, BatchHostNameNotFoundException, BatchInternalErrorException {
        Orchestrator orchestrator = mock(Orchestrator.class);
        doThrow(new UncheckedTimeoutException("Timeout Message")).when(orchestrator).suspendAll(any(), any());

        try {
            HostSuspensionResource resource = new HostSuspensionResource(orchestrator);
            resource.suspendAll("parenthost", Arrays.asList("h1", "h2", "h3"));
            fail();
        } catch (WebApplicationException w) {
            assertEquals(409, w.getResponse().getStatus());
        }
    }
}
