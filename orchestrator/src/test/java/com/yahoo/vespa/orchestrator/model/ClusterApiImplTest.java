// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ClusterApiImplTest {
    final ApplicationApi applicationApi = mock(ApplicationApi.class);
    final ModelTestUtils modelUtils = new ModelTestUtils();

    @Test
    public void testServicesDownAndNotInGroup() {
        HostName hostName1 = modelUtils.createNode("host1", HostStatus.NO_REMARKS);
        HostName hostName2 = modelUtils.createNode("host2", HostStatus.NO_REMARKS);
        HostName hostName3 = modelUtils.createNode("host3", HostStatus.ALLOWED_TO_BE_DOWN);
        HostName hostName4 = modelUtils.createNode("host4", HostStatus.ALLOWED_TO_BE_DOWN);
        HostName hostName5 = modelUtils.createNode("host5", HostStatus.NO_REMARKS);


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

        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(modelUtils.createApplicationInstance(new ArrayList<>()), hostName5),
                modelUtils.getHostStatusMap(),
                modelUtils.getClusterControllerClientFactory());

        assertEquals("{ clusterId=cluster, serviceType=service-type }", clusterApi.clusterInfo());
        assertFalse(clusterApi.isStorageCluster());
        assertEquals("[ServiceInstance{configId=service-2, hostName=host2, serviceStatus=DOWN}, "
                        + "ServiceInstance{configId=service-3, hostName=host3, serviceStatus=UP}, "
                        + "ServiceInstance{configId=service-4, hostName=host4, serviceStatus=DOWN}]",
                clusterApi.servicesDownAndNotInGroupDescription());
        assertEquals("[host3, host4]",
                clusterApi.nodesAllowedToBeDownNotInGroupDescription());
        assertEquals(60, clusterApi.percentageOfServicesDown());
        assertEquals(80, clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown());
    }

    @Test
    public void testNoServices() {
        HostName hostName1 = modelUtils.createNode("host1", HostStatus.NO_REMARKS);
        HostName hostName2 = modelUtils.createNode("host2", HostStatus.NO_REMARKS);
        HostName hostName3 = modelUtils.createNode("host3", HostStatus.ALLOWED_TO_BE_DOWN);
        HostName hostName4 = modelUtils.createNode("host4", HostStatus.ALLOWED_TO_BE_DOWN);
        HostName hostName5 = modelUtils.createNode("host5", HostStatus.NO_REMARKS);


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

        verifyNoServices(serviceCluster, false, false, hostName1);
        verifyNoServices(serviceCluster, true, false, hostName2);
        verifyNoServices(serviceCluster, true, false, hostName3);
        verifyNoServices(serviceCluster, true, false, hostName4);
        verifyNoServices(serviceCluster, false, false, hostName5);

        verifyNoServices(serviceCluster, false, false, hostName1, hostName2);
        verifyNoServices(serviceCluster, true, false, hostName2, hostName3);
        verifyNoServices(serviceCluster, true, true, hostName2, hostName3, hostName4);
        verifyNoServices(serviceCluster, false, true, hostName1, hostName2, hostName3, hostName4);
    }

    private void verifyNoServices(ServiceCluster serviceCluster,
                                  boolean expectedNoServicesInGroupIsUp,
                                  boolean expectedNoServicesOutsideGroupIsDown,
                                  HostName... groupNodes) {
        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(modelUtils.createApplicationInstance(new ArrayList<>()), groupNodes),
                modelUtils.getHostStatusMap(),
                modelUtils.getClusterControllerClientFactory());

        assertEquals(expectedNoServicesInGroupIsUp, clusterApi.noServicesInGroupIsUp());
        assertEquals(expectedNoServicesOutsideGroupIsDown, clusterApi.noServicesOutsideGroupIsDown());
    }

    @Test
    public void testStorageCluster() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");

        ServiceCluster serviceCluster = modelUtils.createServiceCluster(
                "cluster",
                VespaModelUtil.STORAGENODE_SERVICE_TYPE,
                Arrays.asList(
                        modelUtils.createServiceInstance("storage-1", hostName1, ServiceStatus.UP),
                        modelUtils.createServiceInstance("storage-2", hostName2, ServiceStatus.DOWN)
                )
        );

        ClusterApiImpl clusterApi = new ClusterApiImpl(
                applicationApi,
                serviceCluster,
                new NodeGroup(modelUtils.createApplicationInstance(new ArrayList<>()), hostName1, hostName3),
                new HashMap<>(),
                modelUtils.getClusterControllerClientFactory());

        assertTrue(clusterApi.isStorageCluster());
        assertEquals(Optional.of(hostName1), clusterApi.storageNodeInGroup().map(storageNode -> storageNode.hostName()));
        assertEquals(Optional.of(hostName1), clusterApi.upStorageNodeInGroup().map(storageNode -> storageNode.hostName()));
    }
}
