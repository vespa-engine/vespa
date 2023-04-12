// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.test.ManualClock;
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
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactoryMock;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.model.ContentService;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.policy.BatchHostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.StatusService;
import com.yahoo.vespa.orchestrator.status.ZkStatusService;
import com.yahoo.vespa.service.monitor.ServiceModel;
import com.yahoo.vespa.service.monitor.ServiceMonitor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN;
import static com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus.NO_REMARKS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.atLeastOnce;

/**
 * Test Orchestrator with a mock backend (the MockCurator)
 *
 * @author smorgrav
 */
public class OrchestratorImplTest {

    private static final Zone zone = Zone.defaultZone();

    private final ManualClock clock = new ManualClock();
    private final ApplicationApiFactory applicationApiFactory = new ApplicationApiFactory(3, 5, clock);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final MockCurator curator = new MockCurator();
    private final ZkStatusService statusService = new ZkStatusService(
            curator,
            mock(Metric.class),
            new TestTimer(),
            new DummyAntiServiceMonitor());

    private ApplicationId app1;
    private ApplicationId app2;
    private HostName app1_host1;

    private OrchestratorImpl orchestrator;
    private ClusterControllerClientFactoryMock clustercontroller;

    @Before
    public void setUp() {
        // Extract applications and hosts from dummy instance lookup service
        Iterator<ApplicationInstance> iterator = DummyServiceMonitor.getApplications().iterator();
        ApplicationInstanceReference app1_ref = iterator.next().reference();
        app1 = OrchestratorUtil.toApplicationId(app1_ref);
        app1_host1 = DummyServiceMonitor.getContentHosts(app1_ref).iterator().next();
        app2 = OrchestratorUtil.toApplicationId(iterator.next().reference());

        clustercontroller = new ClusterControllerClientFactoryMock();
        orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(flagSource, zone),
                                                                  clustercontroller,
                                                                  applicationApiFactory,
                                                                  flagSource),
                                            clustercontroller,
                                            statusService,
                                            new DummyServiceMonitor(),
                                            0,
                                            new ManualClock(),
                                            applicationApiFactory,
                                            flagSource);

        clustercontroller.setAllDummyNodesAsUp();
    }

    @Test
    public void application_has_initially_no_remarks() throws Exception {
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
    }

    @Test
    public void application_can_be_set_in_suspend() throws Exception {
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
    }

    @Test
    public void application_can_be_removed_from_suspend() throws Exception {
        orchestrator.suspend(app1);
        orchestrator.resume(app1);
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
    }

    @Test
    public void appliations_list_returns_empty_initially() {
        assertTrue(orchestrator.getAllSuspendedApplications().isEmpty());
    }

    @Test
    public void appliations_list_returns_suspended_apps() throws Exception {
        // One suspended app
        orchestrator.suspend(app1);
        assertEquals(1, orchestrator.getAllSuspendedApplications().size());
        assertTrue(orchestrator.getAllSuspendedApplications().contains(app1));

        // Two suspended apps
        orchestrator.suspend(app2);
        assertEquals(2, orchestrator.getAllSuspendedApplications().size());
        assertTrue(orchestrator.getAllSuspendedApplications().contains(app1));
        assertTrue(orchestrator.getAllSuspendedApplications().contains(app2));

        // Back to one when resetting one app to no_remarks
        orchestrator.resume(app1);
        assertEquals(1, orchestrator.getAllSuspendedApplications().size());
        assertTrue(orchestrator.getAllSuspendedApplications().contains(app2));
    }


    @Test
    public void application_operations_are_idempotent() throws Exception {
        // Two suspends
        orchestrator.suspend(app1);
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app2));

        // Three no_remarks
        orchestrator.resume(app1);
        orchestrator.resume(app1);
        orchestrator.resume(app1);
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app2));

        // Two suspends and two on two applications interleaved
        orchestrator.suspend(app2);
        orchestrator.resume(app1);
        orchestrator.suspend(app2);
        orchestrator.resume(app1);
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app2));
    }

    @Test
    public void application_suspend_sets_application_nodes_in_maintenance_and_allowed_to_be_down() throws Exception {
        // Pre condition
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_suspend_while_app_is_resumed_set_allowed_to_be_down_and_set_it_in_maintenance() throws Exception {
        // Pre condition
        assertEquals(NO_REMARKS, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1_host1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_suspend_while_app_is_suspended_does_nothing() throws Exception {
        // Pre condition
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));

        orchestrator.suspend(app1_host1);

        // Should not change anything
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_resume_after_app_is_resumed_removes_allowed_be_down_and_set_it_up() throws Exception {
        // Pre condition
        orchestrator.suspend(app1);
        assertEquals(ALLOWED_TO_BE_DOWN, orchestrator.getApplicationInstanceStatus(app1));
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));

        orchestrator.resume(app1);
        orchestrator.resume(app1_host1);

        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        assertFalse(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void node_resume_while_app_is_suspended_does_nothing() throws Exception {
        orchestrator.suspend(app1_host1);
        orchestrator.suspend(app1);

        orchestrator.resume(app1_host1);

        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        assertTrue(isInMaintenance(app1, app1_host1));
    }

    @Test
    public void applicationReferenceHasTenantAndAppInstance() {
        ServiceMonitor service = new DummyServiceMonitor();
        String applicationInstanceId = service.getApplication(DummyServiceMonitor.TEST1_HOST_NAME).get()
                .reference().toString();
        assertEquals("test-tenant-id:application:prod:utopia-1:instance", applicationInstanceId);
    }

    @Test
    public void testSetNodeState() throws OrchestrationException {
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
        orchestrator.setNodeStatus(app1_host1, HostStatus.ALLOWED_TO_BE_DOWN);
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, orchestrator.getNodeStatus(app1_host1));
        orchestrator.setNodeStatus(app1_host1, HostStatus.NO_REMARKS);
        assertEquals(HostStatus.NO_REMARKS, orchestrator.getNodeStatus(app1_host1));
    }

    @Test
    public void suspendAllWorks() {
        // A spy is preferential because suspendAll() relies on delegating the hard work to suspend() and resume().
        OrchestratorImpl orchestrator = spy(this.orchestrator);

        orchestrator.suspendAll(
                new HostName("parentHostname"),
                Arrays.asList(
                        DummyServiceMonitor.TEST1_HOST_NAME,
                        DummyServiceMonitor.TEST3_HOST_NAME,
                        DummyServiceMonitor.TEST6_HOST_NAME));

        // As of 2016-06-07 the order of the node groups are as follows:
        //   TEST3: mediasearch:imagesearch:default
        //   TEST6: tenant-id-3:application-instance-3:default
        //   TEST1: test-tenant-id:application:instance
        InOrder order = inOrder(orchestrator);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST3_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST6_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST1_NODE_GROUP, true);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST3_NODE_GROUP, false);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST6_NODE_GROUP, false);
        verifySuspendGroup(order, orchestrator, DummyServiceMonitor.TEST1_NODE_GROUP, false);
        order.verifyNoMoreInteractions();
    }

    private void verifySuspendGroup(InOrder order, OrchestratorImpl orchestrator, NodeGroup nodeGroup, boolean probe)
            throws HostStateChangeDeniedException{
        ArgumentCaptor<OrchestratorContext> argument = ArgumentCaptor.forClass(OrchestratorContext.class);
        order.verify(orchestrator).suspendGroup(argument.capture(), eq(nodeGroup));
        assertEquals(probe, argument.getValue().isProbe());
    }

    @Test
    public void whenSuspendAllFails() {
        // A spy is preferential because suspendAll() relies on delegating the hard work to suspend() and resume().
        OrchestratorImpl orchestrator = spy(this.orchestrator);

        Throwable supensionFailure = new HostStateChangeDeniedException(
                DummyServiceMonitor.TEST6_HOST_NAME,
                "some-constraint",
                "error message");
        doThrow(supensionFailure).when(orchestrator).suspendGroup(any(), eq(DummyServiceMonitor.TEST6_NODE_GROUP));

        try {
            orchestrator.suspendAll(
                    new HostName("parentHostname"),
                    Arrays.asList(
                            DummyServiceMonitor.TEST1_HOST_NAME,
                            DummyServiceMonitor.TEST3_HOST_NAME,
                            DummyServiceMonitor.TEST6_HOST_NAME));
            fail();
        } catch (BatchHostStateChangeDeniedException e) {
            assertEquals("Failed to suspend NodeGroup{application=tenant-id-3:application-instance-3:prod:utopia-1:default, " +
                            "hostNames=[test6.hostname.tld]} with parent host parentHostname: " +
                            "Changing the state of test6.hostname.tld would violate " +
                            "some-constraint: error message",
                    e.getMessage());
        }

        InOrder order = inOrder(orchestrator);
        order.verify(orchestrator).suspendGroup(any(), eq(DummyServiceMonitor.TEST3_NODE_GROUP));
        order.verify(orchestrator).suspendGroup(any(), eq(DummyServiceMonitor.TEST6_NODE_GROUP));
        order.verifyNoMoreInteractions();
    }

    @Test
    public void testLargeLocks() {
        var tenantId = new TenantId("tenant");
        var applicationInstanceId = new ApplicationInstanceId("app:dev:us-east-1:default");
        var applicationInstanceReference = new ApplicationInstanceReference(tenantId, applicationInstanceId);

        var policy = mock(HostedVespaPolicy.class);
        var zookeeperStatusService = mock(ZkStatusService.class);
        var serviceMonitor = mock(ServiceMonitor.class);
        var applicationInstance = mock(ApplicationInstance.class);
        var clusterControllerClientFactory = mock(ClusterControllerClientFactory.class);
        var clock = new ManualClock();
        var applicationApiFactory = mock(ApplicationApiFactory.class);
        var lock = mock(ApplicationLock.class);

        when(policy.grantSuspensionRequest(any(), any())).thenReturn(SuspensionReasons.nothingNoteworthy());
        when(serviceMonitor.getApplication(any(HostName.class))).thenReturn(Optional.of(applicationInstance));
        when(applicationInstance.reference()).thenReturn(applicationInstanceReference);
        when(zookeeperStatusService.lockApplication(any(), any())).thenReturn(lock);
        when(lock.getApplicationInstanceStatus()).thenReturn(NO_REMARKS);

        var orchestrator = new OrchestratorImpl(
                policy,
                clusterControllerClientFactory,
                zookeeperStatusService,
                serviceMonitor,
                20,
                clock,
                applicationApiFactory,
                flagSource);

        HostName parentHostname = new HostName("parent.vespa.ai");

        verify(serviceMonitor, atLeastOnce()).registerListener(zookeeperStatusService);
        verifyNoMoreInteractions(serviceMonitor);

        orchestrator.suspendAll(parentHostname, List.of(parentHostname));

        ArgumentCaptor<OrchestratorContext> contextCaptor = ArgumentCaptor.forClass(OrchestratorContext.class);
        verify(zookeeperStatusService, times(2)).lockApplication(contextCaptor.capture(), any());
        List<OrchestratorContext> contexts = contextCaptor.getAllValues();

        // First invocation is probe, second is not.
        assertEquals(2, contexts.size());
        assertTrue(contexts.get(0).isProbe());
        assertTrue(contexts.get(0).largeLocks());
        assertFalse(contexts.get(1).isProbe());
        assertTrue(contexts.get(1).largeLocks());

        verify(applicationApiFactory, times(2)).create(any(), any(), any());
        verify(policy, times(2)).grantSuspensionRequest(any(), any());
        verify(serviceMonitor, atLeastOnce()).getApplication(any(HostName.class));
        verify(lock, times(2)).getApplicationInstanceStatus();

        // Each zookeeperStatusService that is created, is closed.
        verify(zookeeperStatusService, times(2)).lockApplication(any(), any());
        verify(lock, times(2)).close();

        verifyNoMoreInteractions(
                policy,
                clusterControllerClientFactory,
                zookeeperStatusService,
                lock,
                serviceMonitor,
                applicationApiFactory);
    }

    @Test
    public void testIsQuiescent() {
        StatusService statusService = new ZkStatusService(new MockCurator(),
                                                          mock(Metric.class),
                                                          new TestTimer(),
                                                          new DummyAntiServiceMonitor());

        HostName hostName = new HostName("my.host");
        HostName ccHost = new HostName("cc.host");
        TenantId tenantId = new TenantId("tenant");
        ApplicationInstanceId applicationInstanceId = new ApplicationInstanceId("app:env:region:instance");
        ApplicationInstanceReference reference = new ApplicationInstanceReference(tenantId, applicationInstanceId);
        ApplicationId id = ApplicationId.from("tenant", "app", "instance");

        ApplicationInstance applicationInstance =
                new ApplicationInstance(tenantId,
                                        applicationInstanceId,
                                        Set.of(new ServiceCluster(new ClusterId("foo"),
                                                                  ServiceType.STORAGE,
                                                                  Set.of(new ServiceInstance(new ConfigId("foo/storage/1"),
                                                                                             hostName,
                                                                                             ServiceStatus.UP),
                                                                         new ServiceInstance(new ConfigId("foo/storage/2"),
                                                                                             hostName,
                                                                                             ServiceStatus.UP))),
                                               new ServiceCluster(new ClusterId("bar"),
                                                                  ServiceType.SEARCH,
                                                                  Set.of(new ServiceInstance(new ConfigId("bar/storage/0"),
                                                                                             hostName,
                                                                                             ServiceStatus.UP),
                                                                         new ServiceInstance(new ConfigId("bar/storage/3"),
                                                                                             hostName,
                                                                                             ServiceStatus.UP))),
                                               new ServiceCluster(new ClusterId("cluster-controllers"),
                                                                  ServiceType.CLUSTER_CONTROLLER,
                                                                  Set.of(new ServiceInstance(new ConfigId("what/standalone/cluster-controllers/0"),
                                                                                             ccHost,
                                                                                             ServiceStatus.UP)))));

        ServiceMonitor serviceMonitor = () -> new ServiceModel(Map.of(reference, applicationInstance));

        ClusterControllerClientFactory clusterControllerClientFactory = mock(ClusterControllerClientFactory.class);
        ClusterControllerClient fooClient = mock(ClusterControllerClient.class);
        ClusterControllerClient barClient = mock(ClusterControllerClient.class);
        when(clusterControllerClientFactory.createClient(List.of(ccHost), "foo")).thenReturn(fooClient);
        when(clusterControllerClientFactory.createClient(List.of(ccHost), "bar")).thenReturn(barClient);

        orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(flagSource, zone),
                                                                  clusterControllerClientFactory,
                                                                  applicationApiFactory,
                                                                  flagSource),
                                            clusterControllerClientFactory,
                                            statusService,
                                            serviceMonitor,
                                            0,
                                            new ManualClock(),
                                            applicationApiFactory,
                                            flagSource);

        when(fooClient.trySetNodeState(any(), any(), eq(1), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenReturn(true);
        when(fooClient.trySetNodeState(any(), any(), eq(2), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenReturn(true);
        when(barClient.trySetNodeState(any(), any(), eq(0), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenReturn(true);
        when(barClient.trySetNodeState(any(), any(), eq(3), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenReturn(true);
        assertTrue(orchestrator.isQuiescent(id));

        when(fooClient.trySetNodeState(any(), any(), eq(2), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenReturn(false);
        assertFalse(orchestrator.isQuiescent(id));

        when(fooClient.trySetNodeState(any(), any(), eq(2), eq(ClusterControllerNodeState.MAINTENANCE), eq(ContentService.STORAGE_NODE), eq(false))).thenThrow(new RuntimeException());
        assertFalse(orchestrator.isQuiescent(id));
    }

    @Test
    public void testGetHost() {
        ClusterControllerClientFactory clusterControllerClientFactory = new ClusterControllerClientFactoryMock();
        StatusService statusService = new ZkStatusService(
                new MockCurator(),
                mock(Metric.class),
                new TestTimer(),
                new DummyAntiServiceMonitor());

        HostName hostName = new HostName("host.yahoo.com");
        TenantId tenantId = new TenantId("tenant");
        ApplicationInstanceId applicationInstanceId =
                new ApplicationInstanceId("applicationInstanceId");
        ApplicationInstanceReference reference = new ApplicationInstanceReference(
                tenantId,
                applicationInstanceId);

        ApplicationInstance applicationInstance =
                new ApplicationInstance(
                        tenantId,
                        applicationInstanceId,
                        Set.of(new ServiceCluster(
                                new ClusterId("clusterId"),
                                new ServiceType("serviceType"),
                                Set.of(new ServiceInstance(
                                               new ConfigId("configId1"),
                                               hostName,
                                               ServiceStatus.UP),
                                       new ServiceInstance(
                                               new ConfigId("configId2"),
                                               hostName,
                                               ServiceStatus.NOT_CHECKED)))));

        ServiceMonitor serviceMonitor = () -> new ServiceModel(Map.of(reference, applicationInstance));

        orchestrator = new OrchestratorImpl(new HostedVespaPolicy(new HostedVespaClusterPolicy(flagSource, zone),
                                                                  clusterControllerClientFactory,
                                                                  applicationApiFactory,
                                                                  flagSource),
                                            clusterControllerClientFactory,
                                            statusService,
                                            serviceMonitor,
                                            0,
                                            new ManualClock(),
                                            applicationApiFactory,
                                            flagSource);

        orchestrator.setNodeStatus(hostName, HostStatus.ALLOWED_TO_BE_DOWN);

        Host host = orchestrator.getHost(hostName);
        assertEquals(reference, host.getApplicationInstanceReference());
        assertEquals(hostName, host.getHostName());
        assertEquals(HostStatus.ALLOWED_TO_BE_DOWN, host.getHostInfo().status());
        assertTrue(host.getHostInfo().suspendedSince().isPresent());
        assertEquals(2, host.getServiceInstances().size());
    }

    private boolean isInMaintenance(ApplicationId appId, HostName hostName) throws ApplicationIdNotFoundException {
        for (ApplicationInstance app : DummyServiceMonitor.getApplications()) {
            if (app.reference().equals(OrchestratorUtil.toApplicationInstanceReference(appId, new DummyServiceMonitor()))) {
                return clustercontroller.isInMaintenance(app, hostName);
            }
        }
        throw new ApplicationIdNotFoundException();
    }
}
