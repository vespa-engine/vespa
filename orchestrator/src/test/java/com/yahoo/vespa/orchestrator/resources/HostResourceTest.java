// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.jdisc.Timer;
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
import com.yahoo.vespa.orchestrator.Host;
import com.yahoo.vespa.orchestrator.InstanceLookupService;
import com.yahoo.vespa.orchestrator.OrchestrationException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.Policy;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.GetHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.PatchHostResponse;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.StatusService;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceClusterSet;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class HostResourceTest {
    private static final Timer timer = mock(Timer.class);
    private static final int SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS = 0;
    private static final TenantId TENANT_ID = new TenantId("tenantId");
    private static final ApplicationInstanceId APPLICATION_INSTANCE_ID = new ApplicationInstanceId("applicationId");
    private static final ApplicationInstanceReference APPLICATION_INSTANCE_REFERENCE =
            new ApplicationInstanceReference(TENANT_ID, APPLICATION_INSTANCE_ID);

    private static final StatusService EVERY_HOST_IS_UP_HOST_STATUS_SERVICE = mock(StatusService.class);
    private static final MutableStatusRegistry EVERY_HOST_IS_UP_MUTABLE_HOST_STATUS_REGISTRY = mock(MutableStatusRegistry.class);
    static {
        when(EVERY_HOST_IS_UP_HOST_STATUS_SERVICE.forApplicationInstance(eq(APPLICATION_INSTANCE_REFERENCE)))
                .thenReturn(EVERY_HOST_IS_UP_MUTABLE_HOST_STATUS_REGISTRY);
        when(EVERY_HOST_IS_UP_HOST_STATUS_SERVICE.lockApplicationInstance_forCurrentThreadOnly(eq(APPLICATION_INSTANCE_REFERENCE)))
                .thenReturn(EVERY_HOST_IS_UP_MUTABLE_HOST_STATUS_REGISTRY);
        when(EVERY_HOST_IS_UP_MUTABLE_HOST_STATUS_REGISTRY.getHostStatus(any()))
                .thenReturn(HostStatus.NO_REMARKS);
        when(EVERY_HOST_IS_UP_MUTABLE_HOST_STATUS_REGISTRY.getApplicationInstanceStatus())
                .thenReturn(ApplicationInstanceStatus.NO_REMARKS);
    }

    private static final InstanceLookupService mockInstanceLookupService = mock(InstanceLookupService.class);
    static {
        when(mockInstanceLookupService.findInstanceByHost(any()))
                .thenReturn(Optional.of(
                        new ApplicationInstance(
                                TENANT_ID,
                                APPLICATION_INSTANCE_ID,
                                makeServiceClusterSet())));
    }


    private static final InstanceLookupService alwaysEmptyInstanceLookUpService = new InstanceLookupService() {
        @Override
        public Optional<ApplicationInstance> findInstanceById(
                final ApplicationInstanceReference applicationInstanceReference) {
            return Optional.empty();
        }

        @Override
        public Optional<ApplicationInstance> findInstanceByHost(final HostName hostName) {
            return Optional.empty();
        }

        @Override
        public Set<ApplicationInstanceReference> knownInstances() {
            return Collections.emptySet();
        }
    };

    private static class AlwaysAllowPolicy implements Policy {
        @Override
        public void grantSuspensionRequest(OrchestratorContext context, ApplicationApi applicationApi) {
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
                MutableStatusRegistry hostStatusRegistry) {
        }
    }

    private static final OrchestratorImpl alwaysAllowOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE, mockInstanceLookupService,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
            timer
    );

    private static final OrchestratorImpl hostNotFoundOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE, alwaysEmptyInstanceLookUpService,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
            timer
    );

    private final UriInfo uriInfo = mock(UriInfo.class);

    @Test
    public void returns_200_on_success() {
        HostResource hostResource =
                new HostResource(alwaysAllowOrchestrator, uriInfo);

        final String hostName = "hostname";

        UpdateHostResponse response = hostResource.suspend(hostName);

        assertThat(response.hostname()).isEqualTo(hostName);
    }

    @Test
    public void returns_200_on_success_batch() {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchOperationResult response = hostSuspensionResource.suspendAll("parentHostname",
                                                                          Arrays.asList("hostname1", "hostname2"));
        assertThat(response.success());
    }

    @Test
    public void returns_200_empty_batch() {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchOperationResult response = hostSuspensionResource.suspendAll("parentHostname",
                                                                          Collections.emptyList());;
        assertThat(response.success());
    }

    @Test
    public void throws_404_when_host_unknown() {
        try {
            HostResource hostResource =
                    new HostResource(hostNotFoundOrchestrator, uriInfo);
            hostResource.suspend("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(404);
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
            assertThat(w.getResponse().getStatus()).isEqualTo(400);
        }
    }

    private static class AlwaysFailPolicy implements Policy {
        @Override
        public void grantSuspensionRequest(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException {
            doThrow();
        }

        @Override
        public void releaseSuspensionGrant(OrchestratorContext context, ApplicationApi application) throws HostStateChangeDeniedException {
            doThrow();
        }

        @Override
        public void acquirePermissionToRemove(OrchestratorContext context, ApplicationApi applicationApi) throws HostStateChangeDeniedException {
            doThrow();
        }

        @Override
        public void releaseSuspensionGrant(
                OrchestratorContext context, ApplicationInstance applicationInstance,
                HostName hostName,
                MutableStatusRegistry hostStatusRegistry) throws HostStateChangeDeniedException {
            doThrow();
        }

        private static void doThrow() throws HostStateChangeDeniedException {
            throw new HostStateChangeDeniedException(
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
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,mockInstanceLookupService,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
                timer);

        try {
            HostResource hostResource = new HostResource(alwaysRejectResolver, uriInfo);
            hostResource.suspend("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(409);
        }
    }

    @Test
    public void throws_409_when_request_rejected_by_policies_for_batch() {
        final OrchestratorImpl alwaysRejectResolver = new OrchestratorImpl(
                new AlwaysFailPolicy(),
                new ClusterControllerClientFactoryMock(),
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
                mockInstanceLookupService,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS,
                timer);

        try {
            HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysRejectResolver);
            hostSuspensionResource.suspendAll("parentHostname", Arrays.asList("hostname1", "hostname2"));
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(409);
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
                HostStatus.ALLOWED_TO_BE_DOWN,
                new ApplicationInstanceReference(
                        new TenantId("tenantId"),
                        new ApplicationInstanceId("applicationId")),
                Collections.singletonList(serviceInstance));
        when(orchestrator.getHost(hostName)).thenReturn(host);
        GetHostResponse response = hostResource.getHost(hostName.s());
        assertEquals("https://foo.com/bar", response.applicationUrl());
        assertEquals("hostname", response.hostname());
        assertEquals("ALLOWED_TO_BE_DOWN", response.state());
        assertEquals(1, response.services().size());
        assertEquals("clusterId", response.services().get(0).clusterId);
        assertEquals("configId", response.services().get(0).configId);
        assertEquals("UP", response.services().get(0).serviceStatus);
        assertEquals("serviceType", response.services().get(0).serviceType);
    }
}
