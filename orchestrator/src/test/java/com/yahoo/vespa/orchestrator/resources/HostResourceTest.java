// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.resources;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.InstanceLookupService;
import com.yahoo.vespa.orchestrator.OrchestratorImpl;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.policy.ApplicationApi;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.Policy;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchHostSuspendRequest;
import com.yahoo.vespa.orchestrator.restapi.wire.BatchOperationResult;
import com.yahoo.vespa.orchestrator.restapi.wire.UpdateHostResponse;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;
import org.junit.Test;

import javax.ws.rs.WebApplicationException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceClusterSet;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HostResourceTest {
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
                        new ApplicationInstance<>(
                                TENANT_ID,
                                APPLICATION_INSTANCE_ID,
                                makeServiceClusterSet())));
    }


    private static final InstanceLookupService alwaysEmptyInstanceLookUpService = new InstanceLookupService() {
        @Override
        public Optional<ApplicationInstance<ServiceMonitorStatus>> findInstanceById(
                final ApplicationInstanceReference applicationInstanceReference) {
            return Optional.empty();
        }

        @Override
        public Optional<ApplicationInstance<ServiceMonitorStatus>> findInstanceByHost(final HostName hostName) {
            return Optional.empty();
        }

        @Override
        public Set<ApplicationInstanceReference> knownInstances() {
            return Collections.emptySet();
        }
    };

    private static class AlwaysAllowPolicy implements Policy {
        @Override
        public void grantSuspensionRequest(
                ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                HostName hostName,
                MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException {

        }

        @Override
        public void grantSuspensionRequest(ApplicationApi applicationApi) throws HostStateChangeDeniedException {
        }

        @Override
        public void releaseSuspensionGrant(
                ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                HostName hostName,
                MutableStatusRegistry hostStatusRegistry) {
        }
    }

    private static final OrchestratorImpl alwaysAllowOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE, mockInstanceLookupService,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS
    );

    private static final OrchestratorImpl hostNotFoundOrchestrator = new OrchestratorImpl(
            new AlwaysAllowPolicy(),
            new ClusterControllerClientFactoryMock(),
            EVERY_HOST_IS_UP_HOST_STATUS_SERVICE, alwaysEmptyInstanceLookUpService,
            SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS
    );

    @Test
    public void returns_200_on_success() throws Exception {
        HostResource hostResource =
                new HostResource(alwaysAllowOrchestrator);

        final String hostName = "hostname";

        UpdateHostResponse response = hostResource.suspend(hostName);

        assertThat(response.hostname()).isEqualTo(hostName);
    }

    @Test
    public void returns_200_on_success_batch() throws Exception {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchHostSuspendRequest request =
                new BatchHostSuspendRequest("parentHostname", Arrays.asList("hostname1", "hostname2"));
        BatchOperationResult response = hostSuspensionResource.suspendAll(request);
        assertThat(response.success());
    }

    @Test
    public void returns_200_empty_batch() throws Exception {
        HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysAllowOrchestrator);
        BatchHostSuspendRequest request =
                new BatchHostSuspendRequest("parentHostname", Collections.emptyList());
        BatchOperationResult response = hostSuspensionResource.suspendAll(request);
        assertThat(response.success());
    }

    @Test
    public void throws_404_when_host_unknown() throws Exception {
        try {
            HostResource hostResource =
                    new HostResource(hostNotFoundOrchestrator);
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
    public void throws_400_when_host_unknown_for_batch() throws Exception {
        try {
            HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(hostNotFoundOrchestrator);
            BatchHostSuspendRequest request =
                    new BatchHostSuspendRequest("parentHostname", Arrays.asList("hostname1", "hostname2"));
            hostSuspensionResource.suspendAll(request);
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(400);
        }
    }

    private static class AlwaysFailPolicy implements Policy {
        @Override
        public void grantSuspensionRequest(
                ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                HostName hostName,
                MutableStatusRegistry hostStatusRegistry) throws HostStateChangeDeniedException {
            doThrow();
        }

        @Override
        public void grantSuspensionRequest(ApplicationApi applicationApi) throws HostStateChangeDeniedException {
            doThrow();
        }

        @Override
        public void releaseSuspensionGrant(
                ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                HostName hostName,
                MutableStatusRegistry hostStatusRegistry) throws HostStateChangeDeniedException {
            doThrow();
        }

        private static void doThrow() throws HostStateChangeDeniedException {
            throw new HostStateChangeDeniedException(
                    new HostName("some-host"),
                    "impossible-policy",
                    new ServiceType("silly-service"),
                    "This policy rejects all requests");
        }
    }

    @Test
    public void throws_409_when_request_rejected_by_policies() throws Exception {
        final OrchestratorImpl alwaysRejectResolver = new OrchestratorImpl(
                new AlwaysFailPolicy(),
                new ClusterControllerClientFactoryMock(),
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,mockInstanceLookupService,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS);

        try {
            HostResource hostResource = new HostResource(alwaysRejectResolver);
            hostResource.suspend("hostname");
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(409);
        }
    }

    @Test
    public void throws_409_when_request_rejected_by_policies_for_batch() throws Exception {
        final OrchestratorImpl alwaysRejectResolver = new OrchestratorImpl(
                new AlwaysFailPolicy(),
                new ClusterControllerClientFactoryMock(),
                EVERY_HOST_IS_UP_HOST_STATUS_SERVICE,
                mockInstanceLookupService,
                SERVICE_MONITOR_CONVERGENCE_LATENCY_SECONDS);

        try {
            HostSuspensionResource hostSuspensionResource = new HostSuspensionResource(alwaysRejectResolver);
            BatchHostSuspendRequest request =
                    new BatchHostSuspendRequest("parentHostname", Arrays.asList("hostname1", "hostname2"));
            hostSuspensionResource.suspendAll(request);
            fail();
        } catch (WebApplicationException w) {
            assertThat(w.getResponse().getStatus()).isEqualTo(409);
        }
    }
}
