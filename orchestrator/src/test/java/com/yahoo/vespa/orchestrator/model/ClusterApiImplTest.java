// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.test.ManualClock;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceStatusInfo;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.OrchestratorUtil;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaClusterPolicy;
import com.yahoo.vespa.orchestrator.policy.SuspensionReasons;
import com.yahoo.vespa.orchestrator.status.HostInfos;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author hakonhall
 */
public class ClusterApiImplTest {

    private final ApplicationApi applicationApi = mock(ApplicationApi.class);
    private final ModelTestUtils modelUtils = new ModelTestUtils();
    private final ManualClock clock = new ManualClock(Instant.ofEpochSecond(1600436659));
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();

    @Test
    public void testServicesDownAndNotInGroup() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");
        HostName hostName4 = new HostName("host4");
        HostName hostName5 = new HostName("host5");

        ServiceCluster serviceCluster = modelUtils.createServiceCluster(
                "cluster",
                new ServiceType("service-type"),
                Arrays.asList(
                        modelUtils.createServiceInstance("service-1", hostName1, ServiceStatus.UP),
                        modelUtils.createServiceInstance("service-2", hostName2, ServiceStatus.DOWN),
                        modelUtils.createServiceInstance("service-3", hostName3, ServiceStatus.UP),
                        modelUtils.createServiceInstance("service-4", hostName4, ServiceStatus.DOWN),
                        modelUtils.createServiceInstance("service-5", hostName5, ServiceStatus.UP)
                )
        );
        modelUtils.createApplicationInstance(Collections.singletonList(serviceCluster));

        modelUtils.createNode(hostName1, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName2, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName3, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(hostName4, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(hostName5, HostStatus.NO_REMARKS);

        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(modelUtils.createApplicationInstance(new ArrayList<>()), hostName5),
                modelUtils.getHostInfos(),
                modelUtils.getClusterControllerClientFactory(), ModelTestUtils.NUMBER_OF_CONFIG_SERVERS, clock);

        assertEquals("{ clusterId=cluster, serviceType=service-type }", clusterApi.clusterInfo());
        assertFalse(clusterApi.isStorageCluster());
        assertEquals(" Suspended hosts: [host3, host4]. Services down on resumed hosts: [" +
                        "ServiceInstance{configId=service-2, hostName=host2, serviceStatus=" +
                        "ServiceStatusInfo{status=DOWN, since=Optional.empty, lastChecked=Optional.empty}}].",
                clusterApi.downDescription());
        assertEquals(60, clusterApi.percentageOfServicesDown());
        assertEquals(80, clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown());
    }

    /** Make a ClusterApiImpl for the cfg1 config server, with cfg3 missing from the cluster (not provisioned). */
    private ClusterApiImpl makeCfg1ClusterApi(ServiceStatus cfg1ServiceStatus, ServiceStatus cfg2ServiceStatus) {
        return makeConfigClusterApi(ModelTestUtils.NUMBER_OF_CONFIG_SERVERS, cfg1ServiceStatus, cfg2ServiceStatus);
    }

    @Test
    public void testCfg1SuspensionFailsWithMissingCfg3() {
        ClusterApiImpl clusterApi = makeCfg1ClusterApi(ServiceStatus.UP, ServiceStatus.UP);

        HostedVespaClusterPolicy policy = new HostedVespaClusterPolicy(flagSource);

        try {
            policy.verifyGroupGoingDownIsFine(clusterApi);
            fail();
        } catch (HostStateChangeDeniedException e) {
            assertThat(e.getMessage(),
                    containsString("Changing the state of cfg1 would violate enough-services-up: " +
                            "Suspension of service with type 'configserver' not allowed: 33% are suspended already. " +
                            "Services down on resumed hosts: [1 missing config server]."));
        }

        flagSource.withBooleanFlag(Flags.GROUP_SUSPENSION.id(), true);

        try {
            policy.verifyGroupGoingDownIsFine(clusterApi);
            fail();
        } catch (HostStateChangeDeniedException e) {
            assertThat(e.getMessage(),
                    containsString("Suspension of service with type 'configserver' not allowed: 33% are suspended already. " +
                            "Services down on resumed hosts: [1 missing config server]."));
        }
    }

    @Test
    public void testCfg1SuspendsIfDownWithMissingCfg3() throws HostStateChangeDeniedException {
        ClusterApiImpl clusterApi = makeCfg1ClusterApi(ServiceStatus.DOWN, ServiceStatus.UP);

        HostedVespaClusterPolicy policy = new HostedVespaClusterPolicy(flagSource);

        policy.verifyGroupGoingDownIsFine(clusterApi);
    }

    @Test
    public void testSingleConfigServerCanSuspend() {
        for (var status : EnumSet.of(ServiceStatus.UP, ServiceStatus.DOWN)) {
            var clusterApi = makeConfigClusterApi(1, status);
            var policy = new HostedVespaClusterPolicy(flagSource);
            try {
                policy.verifyGroupGoingDownIsFine(clusterApi);
            } catch (HostStateChangeDeniedException e) {
                fail("Expected suspension to succeed");
            }
        }
    }

    @Test
    public void testNoServices() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");
        HostName hostName4 = new HostName("host4");
        HostName hostName5 = new HostName("host5");
        HostName hostName6 = new HostName("host6");

        ServiceInstance service2 = modelUtils.createServiceInstance("service-2", hostName2, ServiceStatus.DOWN);

        // Within down moratorium
        Instant downSince5 = clock.instant().minus(ClusterApiImpl.downMoratorium).plus(Duration.ofSeconds(5));
        ServiceInstance service5 = modelUtils.createServiceInstance("service-5", hostName5,
                new ServiceStatusInfo(ServiceStatus.DOWN, downSince5, downSince5, Optional.empty(), Optional.empty()));

        // After down moratorium
        Instant downSince6 = clock.instant().minus(ClusterApiImpl.downMoratorium).minus(Duration.ofSeconds(5));
        ServiceInstance service6 = modelUtils.createServiceInstance("service-6", hostName6,
                new ServiceStatusInfo(ServiceStatus.DOWN, downSince6, downSince6, Optional.empty(), Optional.empty()));

        ServiceCluster serviceCluster = modelUtils.createServiceCluster(
                "cluster",
                new ServiceType("service-type"),
                Arrays.asList(
                        modelUtils.createServiceInstance("service-1", hostName1, ServiceStatus.UP),
                        service2,
                        modelUtils.createServiceInstance("service-3", hostName3, ServiceStatus.UP),
                        modelUtils.createServiceInstance("service-4", hostName4, ServiceStatus.DOWN),
                        service5,
                        service6
                )
        );
        modelUtils.createApplicationInstance(Collections.singletonList(serviceCluster));

        modelUtils.createNode(hostName1, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName2, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName3, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(hostName4, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(hostName5, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName6, HostStatus.NO_REMARKS);

        var reason2 = SuspensionReasons.isDown(service2);
        var reason6 = SuspensionReasons.downSince(service6, downSince6, Duration.ofSeconds(35));
        var reasons2and6 = new SuspensionReasons().mergeWith(reason2).mergeWith(reason6);

        verifyNoServices(serviceCluster, Optional.empty(), false, hostName1);
        verifyNoServices(serviceCluster, Optional.of(reason2), false, hostName2);
        verifyNoServices(serviceCluster, Optional.of(SuspensionReasons.nothingNoteworthy()), false, hostName3);
        verifyNoServices(serviceCluster, Optional.of(SuspensionReasons.nothingNoteworthy()), false, hostName4);
        verifyNoServices(serviceCluster, Optional.empty(), false, hostName5);
        verifyNoServices(serviceCluster, Optional.of(reason6), false, hostName6);

        verifyNoServices(serviceCluster, Optional.empty(), false, hostName1, hostName2);
        verifyNoServices(serviceCluster, Optional.of(reasons2and6), false, hostName2, hostName3, hostName6);
        verifyNoServices(serviceCluster, Optional.of(reasons2and6), false,
                hostName2, hostName3, hostName4, hostName6);
        verifyNoServices(serviceCluster, Optional.empty(), true,
                hostName2, hostName3, hostName4, hostName5, hostName6);
        verifyNoServices(serviceCluster, Optional.empty(), false,
                hostName1, hostName2, hostName3, hostName4, hostName6);
        verifyNoServices(serviceCluster, Optional.empty(), true,
                hostName1, hostName2, hostName3, hostName4, hostName5, hostName6);
    }

    private void verifyNoServices(ServiceCluster serviceCluster,
                                  Optional<SuspensionReasons> expectedNoServicesInGroupIsUp,
                                  boolean expectedNoServicesOutsideGroupIsDown,
                                  HostName... groupNodes) {
        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(modelUtils.createApplicationInstance(new ArrayList<>()), groupNodes),
                modelUtils.getHostInfos(),
                modelUtils.getClusterControllerClientFactory(), ModelTestUtils.NUMBER_OF_CONFIG_SERVERS, clock);

        assertEquals(expectedNoServicesInGroupIsUp.map(SuspensionReasons::getMessagesInOrder),
                     clusterApi.reasonsForNoServicesInGroupIsUp().map(SuspensionReasons::getMessagesInOrder));
        assertEquals(expectedNoServicesOutsideGroupIsDown, clusterApi.noServicesOutsideGroupIsDown());
    }

    @Test
    public void testStorageCluster() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");

        ServiceCluster serviceCluster = modelUtils.createServiceCluster(
                "cluster",
                ServiceType.STORAGE,
                Arrays.asList(
                        modelUtils.createServiceInstance("storage-1", hostName1, ServiceStatus.UP),
                        modelUtils.createServiceInstance("storage-2", hostName2, ServiceStatus.DOWN)
                )
        );


        ApplicationInstance applicationInstance = modelUtils.createApplicationInstance(new ArrayList<>());
        serviceCluster.setApplicationInstance(applicationInstance);

        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(applicationInstance, hostName1, hostName3),
                new HostInfos(),
                modelUtils.getClusterControllerClientFactory(), ModelTestUtils.NUMBER_OF_CONFIG_SERVERS, clock);

        assertTrue(clusterApi.isStorageCluster());
        assertEquals(Optional.of(hostName1), clusterApi.storageNodeInGroup().map(storageNode -> storageNode.hostName()));
        assertEquals(Optional.of(hostName1), clusterApi.upStorageNodeInGroup().map(storageNode -> storageNode.hostName()));
    }

    private ClusterApiImpl makeConfigClusterApi(int clusterSize, ServiceStatus first, ServiceStatus... rest) {
        var serviceStatusList = new ArrayList<ServiceStatus>();
        serviceStatusList.add(first);
        serviceStatusList.addAll(List.of(rest));
        var hostnames = IntStream.rangeClosed(1, serviceStatusList.size())
                                 .mapToObj(i -> new HostName("cfg" + i))
                                 .collect(Collectors.toList());
        var instances = new ArrayList<ServiceInstance>();
        for (int i = 0; i < hostnames.size(); i++) {
            instances.add(modelUtils.createServiceInstance("cs" + i + 1, hostnames.get(i), serviceStatusList.get(i)));
        }
        ServiceCluster serviceCluster = modelUtils.createServiceCluster(
                ClusterId.CONFIG_SERVER.s(),
                ServiceType.CONFIG_SERVER,
                instances
        );
        for (var instance : instances) {
            instance.setServiceCluster(serviceCluster);
        }

        Set<ServiceCluster> serviceClusterSet = Set.of(serviceCluster);

        ApplicationInstance application = new ApplicationInstance(
                TenantId.HOSTED_VESPA,
                ApplicationInstanceId.CONFIG_SERVER,
                serviceClusterSet);

        serviceCluster.setApplicationInstance(application);

        when(applicationApi.applicationId()).thenReturn(OrchestratorUtil.toApplicationId(application.reference()));

        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(application, hostnames.get(0)),
                modelUtils.getHostInfos(),
                modelUtils.getClusterControllerClientFactory(), clusterSize, clock);

        assertEquals(clusterSize - serviceStatusList.size(), clusterApi.missingServices());
        assertEquals(clusterSize == serviceStatusList.size(), clusterApi.noServicesOutsideGroupIsDown());

        return clusterApi;
    }

}
