// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;


import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.TestUtil;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerState;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerStateResponse;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceClusterSet;
import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceInstanceSet;
import static com.yahoo.vespa.service.monitor.ServiceMonitorStatus.DOWN;
import static com.yahoo.vespa.service.monitor.ServiceMonitorStatus.NOT_CHECKED;
import static com.yahoo.vespa.service.monitor.ServiceMonitorStatus.UP;
import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author oyving
 * @author bakksjo
 */
public class HostedVespaPolicyTest {
    private static final TenantId TENANT_ID = new TenantId("tenantId");
    private static final ApplicationInstanceId APPLICATION_INSTANCE_ID = new ApplicationInstanceId("applicationId");
    private static final HostName HOST_NAME_1 = new HostName("host-1");
    private static final HostName HOST_NAME_2 = new HostName("host-2");
    private static final HostName HOST_NAME_3 = new HostName("host-3");
    private static final HostName HOST_NAME_4 = new HostName("host-4");
    private static final HostName HOST_NAME_5 = new HostName("host-5");
    private static final ServiceType SERVICE_TYPE_1 = new ServiceType("service-1");
    private static final ServiceType SERVICE_TYPE_2 = new ServiceType("service-2");

    private final ClusterControllerClientFactory clusterControllerClientFactory
            = mock(ClusterControllerClientFactory.class);
    private final ClusterControllerClient client = mock(ClusterControllerClient.class);
    {
        when(clusterControllerClientFactory.createClient(any(), any())).thenReturn(client);
    }

    private final HostedVespaPolicy policy
            = new HostedVespaPolicy(clusterControllerClientFactory);

    private final MutableStatusRegistry mutablestatusRegistry = mock(MutableStatusRegistry.class);
    {
        when(mutablestatusRegistry.getHostStatus(any())).thenReturn(HostStatus.NO_REMARKS);
    }

    @Test
    public void test_policy_everyone_agrees_everything_is_up() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP))
                .build();

        policy.grantSuspensionRequest(
                applicationInstance,
                HOST_NAME_1,
                mutablestatusRegistry
        );

        verify(mutablestatusRegistry, times(1)).setHostState(HOST_NAME_1, HostStatus.ALLOWED_TO_BE_DOWN);
    }

    private void grantWithAdminCluster(
            ServiceMonitorStatus statusParallelInstanceOtherHost,
            ServiceMonitorStatus statusInstanceOtherHost,
            ServiceType serviceType,
            boolean expectGranted) throws HostStateChangeDeniedException {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, statusInstanceOtherHost)
                        .instance(HOST_NAME_3, UP))
                .addCluster(new ClusterBuilder(VespaModelUtil.ADMIN_CLUSTER_ID, serviceType)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, statusParallelInstanceOtherHost))
                .build();

        if (expectGranted) {
            policy.grantSuspensionRequest(
                    applicationInstance,
                    HOST_NAME_1,
                    mutablestatusRegistry
            );

            verify(mutablestatusRegistry, times(1)).setHostState(HOST_NAME_1, HostStatus.ALLOWED_TO_BE_DOWN);
        } else {
            try {
                policy.grantSuspensionRequest(
                        applicationInstance,
                        HOST_NAME_1,
                        mutablestatusRegistry);
                fail();
            } catch (HostStateChangeDeniedException e) {
                // As expected.
                assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            }

            verify(mutablestatusRegistry, never()).setHostState(any(), any());
        }
    }

    @Test
    public void test_parallel_cluster_down_is_ok() throws Exception {
        grantWithAdminCluster(DOWN, UP, new ServiceType("some-service-type"), true);
    }

    @Test
    public void test_slobrok_cluster_down_is_not_ok() throws Exception {
        grantWithAdminCluster(DOWN, UP, VespaModelUtil.SLOBROK_SERVICE_TYPE, false);
    }

    @Test
    public void test_other_cluster_instance_down_is_not_ok() throws Exception {
        grantWithAdminCluster(DOWN, DOWN, new ServiceType("some-service-type"), false);
    }

    @Test
    public void test_all_up_is_ok() throws Exception {
        grantWithAdminCluster(UP, UP, new ServiceType("some-service-type"), true);
    }

    @Test
    public void test_policy_other_host_allowed_to_be_down() {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_2))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        try {
            policy.grantSuspensionRequest(
                    applicationInstance,
                    HOST_NAME_3,
                    mutablestatusRegistry);
            fail();
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(SERVICE_TYPE_1);
        }

        verify(mutablestatusRegistry, never()).setHostState(any(), any());
    }

    @Test
    public void test_policy_this_host_allowed_to_be_down() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_3))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);

        verify(mutablestatusRegistry, times(1)).setHostState(HOST_NAME_3, HostStatus.ALLOWED_TO_BE_DOWN);
    }

    @Test
    public void from_five_to_ten_percent_suspension() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_2))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        policy.grantSuspensionRequest(
                applicationInstance,
                HOST_NAME_3,
                mutablestatusRegistry);

        verify(mutablestatusRegistry, times(1)).setHostState(HOST_NAME_3, HostStatus.ALLOWED_TO_BE_DOWN);
    }

    @Test
    public void from_ten_to_fifteen_percent_suspension() {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_2))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        try {
            policy.grantSuspensionRequest(
                    applicationInstance,
                    HOST_NAME_3,
                    mutablestatusRegistry);
            fail();
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(SERVICE_TYPE_1);
        }

        verify(mutablestatusRegistry, never()).setHostState(any(), any());
    }

    @Test
    public void from_five_to_fifteen_percent_suspension() {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_2))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        try {
            policy.grantSuspensionRequest(
                    applicationInstance,
                    HOST_NAME_3,
                    mutablestatusRegistry);
            fail();
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(SERVICE_TYPE_1);
        }

        verify(mutablestatusRegistry, never()).setHostState(any(), any());
    }

    @Test
    public void test_policy_no_services() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder().build();

        HostName hostName = new HostName("test-hostname");
        policy.grantSuspensionRequest(
                applicationInstance,
                hostName,
                mutablestatusRegistry
        );

        verify(mutablestatusRegistry, times(1)).setHostState(hostName, HostStatus.ALLOWED_TO_BE_DOWN);
    }

    // The cluster name happens to be the cluster id of any of the content service clusters.
    private static final String CONTENT_CLUSTER_NAME = "content-cluster-id";
    private static final HostName CLUSTER_CONTROLLER_HOST = new HostName("controller-0");
    private static final HostName STORAGE_NODE_HOST = new HostName("storage-2");
    private static final int STORAGE_NODE_INDEX = 2;

    private static final ServiceCluster<ServiceMonitorStatus> CLUSTER_CONTROLLER_SERVICE_CLUSTER = new ServiceCluster<>(
            new ClusterId("cluster-0"),
            VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
            makeServiceInstanceSet(
                    new ServiceInstance<>(
                            TestUtil.clusterControllerConfigId(0),
                            CLUSTER_CONTROLLER_HOST,
                            UP)));

    private static final ServiceCluster<ServiceMonitorStatus> DISTRIBUTOR_SERVICE_CLUSTER = new ServiceCluster<>(
            new ClusterId(CONTENT_CLUSTER_NAME),
            VespaModelUtil.DISTRIBUTOR_SERVICE_TYPE,
            makeServiceInstanceSet(
                    new ServiceInstance<>(
                            new ConfigId("distributor-id-1"),
                            new HostName("distributor-1"),
                            UP)));

    private static final ServiceCluster<ServiceMonitorStatus> STORAGE_SERVICE_CLUSTER = new ServiceCluster<>(
            new ClusterId(CONTENT_CLUSTER_NAME),
            VespaModelUtil.STORAGENODE_SERVICE_TYPE,
            makeServiceInstanceSet(
                    new ServiceInstance<>(
                            TestUtil.storageNodeConfigId(STORAGE_NODE_INDEX),
                            STORAGE_NODE_HOST,
                            UP)));

    private static final ApplicationInstance<ServiceMonitorStatus> APPLICATION_INSTANCE =
            new ApplicationInstance<>(
                    TENANT_ID,
                    APPLICATION_INSTANCE_ID,
                    makeServiceClusterSet(
                            CLUSTER_CONTROLLER_SERVICE_CLUSTER,
                            DISTRIBUTOR_SERVICE_CLUSTER,
                            STORAGE_SERVICE_CLUSTER));

    // The grantSuspensionRequest() and releaseSuspensionGrant() functions happen to have similar signature,
    // which allows us to reuse test code for testing both functions. The actual call to one of these two functions
    // is encapsulated into the following functional interface.
    interface PolicyFunction {
        void grant(
                final HostedVespaPolicy policy,
                final ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                final HostName hostName,
                final MutableStatusRegistry hostStatusRegistry) throws HostStateChangeDeniedException;
    }

    /**
     * Since grantSuspensionRequest and releaseSuspensionGrant is quite similar, this test util contains the bulk
     * of the test code used to test their common functionality.
     *
     * @param grantFunction  Encapsulates the grant function to call
     * @param currentHostStatus  The current HostStatus of the host
     * @param expectedNodeStateSentToClusterController  The NodeState the test expects to be sent to the controller,
     *                                                  or null if no CC request is expected to be sent.
     * @param expectedHostStateSetOnHostStatusService  The HostState the test expects to be set on the host service.
     */
    private void testCommonGrantFunctionality(
            PolicyFunction grantFunction,
            ApplicationInstance<ServiceMonitorStatus> application,
            HostStatus currentHostStatus,
            Optional<ClusterControllerState> expectedNodeStateSentToClusterController,
            HostStatus expectedHostStateSetOnHostStatusService) throws Exception {
        // There is only one service running on the host, which is a storage node.
        // Therefore, the corresponding cluster controller will have to be contacted
        // to ask for permission.

        ClusterControllerStateResponse response = new ClusterControllerStateResponse(true, "ok");
        // MOTD: anyInt() MUST be used for an int field, otherwise a NullPointerException is thrown!
        // In general, special anyX() must be used for primitive fields.
        when(client.setNodeState(anyInt(), any())).thenReturn(response);

        when(mutablestatusRegistry.getHostStatus(any())).thenReturn(currentHostStatus);

        // Execution phase.
        grantFunction.grant(policy, application, STORAGE_NODE_HOST, mutablestatusRegistry);

        // Verification phase.

        if (expectedNodeStateSentToClusterController.isPresent()) {
            List<HostName> clusterControllers = CLUSTER_CONTROLLER_SERVICE_CLUSTER.serviceInstances().stream()
                    .map(service -> service.hostName())
                    .collect(Collectors.toList());
            
            verify(clusterControllerClientFactory, times(1))
                    .createClient(clusterControllers, CONTENT_CLUSTER_NAME);
            verify(client, times(1))
                    .setNodeState(
                            STORAGE_NODE_INDEX,
                            expectedNodeStateSentToClusterController.get());
        } else {
            verify(client, never()).setNodeState(anyInt(), any());
        }

        verify(mutablestatusRegistry, times(1))
                .setHostState(
                        STORAGE_NODE_HOST,
                        expectedHostStateSetOnHostStatusService);
    }

    @Test
    public void test_defer_to_controller() throws Exception {
        HostStatus currentHostStatus = HostStatus.NO_REMARKS;
        ClusterControllerState expectedNodeStateSentToClusterController = ClusterControllerState.MAINTENANCE;
        HostStatus expectedHostStateSetOnHostStatusService = HostStatus.ALLOWED_TO_BE_DOWN;
        testCommonGrantFunctionality(
                HostedVespaPolicy::grantSuspensionRequest,
                APPLICATION_INSTANCE,
                currentHostStatus,
                Optional.of(expectedNodeStateSentToClusterController),
                expectedHostStateSetOnHostStatusService);
    }

    @Test
    public void test_release_suspension_grant_gives_no_remarks() throws Exception {
        HostStatus currentHostStatus = HostStatus.ALLOWED_TO_BE_DOWN;
        ClusterControllerState expectedNodeStateSentToClusterController = ClusterControllerState.UP;
        HostStatus expectedHostStateSetOnHostStatusService = HostStatus.NO_REMARKS;
        testCommonGrantFunctionality(
                HostedVespaPolicy::releaseSuspensionGrant,
                APPLICATION_INSTANCE,
                currentHostStatus,
                Optional.of(expectedNodeStateSentToClusterController),
                expectedHostStateSetOnHostStatusService);
    }

    @Test
    public void okToSuspendHostWithNoConfiguredServices() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, DOWN)
                        .instance(HOST_NAME_2, DOWN))
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(HOST_NAME_4, DOWN)
                        .instance(HOST_NAME_5, DOWN))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    @Test
    public void okToSuspendHostWithAllItsServicesDownEvenIfOthersAreDownToo() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, DOWN)
                        .instance(HOST_NAME_3, DOWN))
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(HOST_NAME_3, DOWN)
                        .instance(HOST_NAME_4, DOWN)
                        .instance(HOST_NAME_5, UP))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    @Test
    public void okToSuspendStorageNodeWhenStorageIsDown() throws Exception {
        ServiceMonitorStatus storageNodeStatus = DOWN;
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new StorageClusterBuilder()
                        // DOWN storage service => ok to suspend and no cluster controller call
                        .instance(STORAGE_NODE_HOST, DOWN, STORAGE_NODE_INDEX)
                        .instance(HOST_NAME_2, DOWN, STORAGE_NODE_INDEX + 1)
                        .instance(HOST_NAME_3, DOWN, STORAGE_NODE_INDEX + 2))
                .addCluster(CLUSTER_CONTROLLER_SERVICE_CLUSTER)
                .addCluster(DISTRIBUTOR_SERVICE_CLUSTER)
                // This service has one down service on another host, which should
                // not block us from suspension because STORAGE_NODE_HOST down too.
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(STORAGE_NODE_HOST, DOWN)
                        .instance(HOST_NAME_4, DOWN)
                        .instance(HOST_NAME_5, UP))
                .build();

        HostStatus currentHostStatus = HostStatus.NO_REMARKS;
        Optional<ClusterControllerState> dontExpectAnyCallsToClusterController = Optional.empty();
        testCommonGrantFunctionality(
                HostedVespaPolicy::grantSuspensionRequest,
                applicationInstance,
                currentHostStatus,
                dontExpectAnyCallsToClusterController,
                HostStatus.ALLOWED_TO_BE_DOWN);
    }

    @Test
    public void denySuspendOfStorageIfOthersAreDown() throws Exception {
        // If the storage service is up, but other hosts' storage services are down,
        // we should be denied permission to suspend. This behavior is common for
        // storage service and non-storage service (they differ when it comes to
        // the cluster controller).
        ServiceMonitorStatus storageNodeStatus = UP;

        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new StorageClusterBuilder()
                        .instance(STORAGE_NODE_HOST, storageNodeStatus, STORAGE_NODE_INDEX)
                        .instance(HOST_NAME_2, DOWN, STORAGE_NODE_INDEX + 1)
                        .instance(HOST_NAME_3, DOWN, STORAGE_NODE_INDEX + 2))
                .addCluster(CLUSTER_CONTROLLER_SERVICE_CLUSTER)
                .addCluster(DISTRIBUTOR_SERVICE_CLUSTER)
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(STORAGE_NODE_HOST, DOWN)
                        .instance(HOST_NAME_4, DOWN)
                        .instance(HOST_NAME_5, UP))
                .build();

        when(mutablestatusRegistry.getHostStatus(any())).thenReturn(HostStatus.NO_REMARKS);

        try {
            policy.grantSuspensionRequest(applicationInstance, STORAGE_NODE_HOST, mutablestatusRegistry);
            fail();
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(VespaModelUtil.STORAGENODE_SERVICE_TYPE);
        }
    }

    // In this test we verify the storage service cluster suspend policy of allowing at most 1
    // storage service to be effectively down. The normal policy (and the one used previously for storage)
    // is to allow 10%. Therefore, the test verifies we disallow suspending 2 hosts = 5% (some random number <10%).
    //
    // Since the Orchestrator doesn't allow suspending the host, the Orchestrator doesn't even bother calling the
    // Cluster Controller. The CC also has a policy of max 1, so it's just an optimization and safety guard.
    @Test
    public void dontBotherCallingClusterControllerIfOtherStorageNodesAreDown() throws Exception {
        StorageClusterBuilder clusterBuilder = new StorageClusterBuilder();
        for (int i = 0; i < 40; ++i) {
            clusterBuilder.instance(new HostName("host-" + i), UP, i);
        }
        ApplicationInstance<ServiceMonitorStatus> applicationInstance =
                new AppBuilder().addCluster(clusterBuilder).build();

        HostName host_1 = new HostName("host-1");
        when(mutablestatusRegistry.getHostStatus(eq(host_1))).thenReturn(HostStatus.NO_REMARKS);

        HostName host_2 = new HostName("host-2");
        when(mutablestatusRegistry.getHostStatus(eq(host_2))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);

        try {
            policy.grantSuspensionRequest(applicationInstance, host_1, mutablestatusRegistry);
            fail();
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName())
                    .isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(VespaModelUtil.STORAGENODE_SERVICE_TYPE);
        }

        verify(mutablestatusRegistry, never()).setHostState(any(), any());
    }

    @Test
    public void ownServiceInstanceDown() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, DOWN)
                        .instance(HOST_NAME_3, DOWN))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    @Test
    public void ownServiceInstanceDown_otherServiceIsAllNotChecked() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, DOWN)
                        .instance(HOST_NAME_3, DOWN))
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(HOST_NAME_3, NOT_CHECKED)
                        .instance(HOST_NAME_4, NOT_CHECKED)
                        .instance(HOST_NAME_5, NOT_CHECKED))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    @Test
    public void ownServiceInstanceDown_otherServiceIsAllNotChecked_oneHostDown() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, DOWN)
                        .instance(HOST_NAME_3, DOWN))
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(HOST_NAME_3, NOT_CHECKED)
                        .instance(HOST_NAME_4, NOT_CHECKED)
                        .instance(HOST_NAME_5, NOT_CHECKED))
                .build();

        when(mutablestatusRegistry.getHostStatus(eq(HOST_NAME_4))).thenReturn(HostStatus.ALLOWED_TO_BE_DOWN);
        try {
            policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
            fail("Should not be allowed to set " + HOST_NAME_3 + " down when " + HOST_NAME_4 + " is already down.");
        } catch (HostStateChangeDeniedException e) {
            // As expected.
            assertThat(e.getConstraintName()).isEqualTo(HostedVespaPolicy.ENOUGH_SERVICES_UP_CONSTRAINT);
            assertThat(e.getServiceType()).isEqualTo(SERVICE_TYPE_2);
        }
    }

    @Test
    public void ownServiceInstanceDown_otherServiceIsAllUp() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, DOWN)
                        .instance(HOST_NAME_3, DOWN))
                .addCluster(new ClusterBuilder(SERVICE_TYPE_2)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_4, UP)
                        .instance(HOST_NAME_5, UP))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    @Test
    public void hostHasTwoInstances_oneDownOneUp() throws Exception {
        final ApplicationInstance<ServiceMonitorStatus> applicationInstance = new AppBuilder()
                .addCluster(new ClusterBuilder(SERVICE_TYPE_1)
                        .instance(HOST_NAME_1, UP)
                        .instance(HOST_NAME_2, UP)
                        .instance(HOST_NAME_3, UP)
                        .instance(HOST_NAME_3, DOWN))
                .build();

        policy.grantSuspensionRequest(applicationInstance, HOST_NAME_3, mutablestatusRegistry);
    }

    // Helper classes for terseness.

    private static class AppBuilder {
        private final Set<ServiceCluster<ServiceMonitorStatus>> serviceClusters = new HashSet<>();

        public AppBuilder addCluster(final ServiceCluster<ServiceMonitorStatus> cluster) {
            serviceClusters.add(cluster);
            return this;
        }

        public AppBuilder addCluster(final ClusterBuilder clusterBuilder) {
            serviceClusters.add(clusterBuilder.build());
            return this;
        }

        public AppBuilder addCluster(final StorageClusterBuilder clusterBuilder) {
            serviceClusters.add(clusterBuilder.build());
            return this;
        }

        public ApplicationInstance<ServiceMonitorStatus> build() {
            return new ApplicationInstance<>(
                    TENANT_ID,
                    APPLICATION_INSTANCE_ID,
                    serviceClusters);
        }
    }

    private static class ClusterBuilder {
        private final ServiceType serviceType;
        private final Set<ServiceInstance<ServiceMonitorStatus>> instances = new HashSet<>();
        private final ClusterId clusterId;
        private int instanceIndex = 0;

        public ClusterBuilder(final ClusterId clusterId, final ServiceType serviceType) {
            this.clusterId = clusterId;
            this.serviceType = serviceType;
        }

        public ClusterBuilder(final ServiceType serviceType) {
            this.clusterId = new ClusterId("clusterId");
            this.serviceType = serviceType;
        }

        public ClusterBuilder instance(final HostName hostName, final ServiceMonitorStatus status) {
            instances.add(new ServiceInstance<>(new ConfigId("configId-" + instanceIndex), hostName, status));
            ++instanceIndex;
            return this;
        }

        public ServiceCluster<ServiceMonitorStatus> build() {
            return new ServiceCluster<>(clusterId, serviceType, instances);
        }
    }

    private static class StorageClusterBuilder {
        private final Set<ServiceInstance<ServiceMonitorStatus>> instances = new HashSet<>();

        public StorageClusterBuilder instance(final HostName hostName, final ServiceMonitorStatus status, int index) {
            instances.add(new ServiceInstance<>(TestUtil.storageNodeConfigId(index), hostName, status));
            return this;
        }

        public ServiceCluster<ServiceMonitorStatus> build() {
            return new ServiceCluster<>(new ClusterId(CONTENT_CLUSTER_NAME), VespaModelUtil.STORAGENODE_SERVICE_TYPE, instances);
        }
    }
}
