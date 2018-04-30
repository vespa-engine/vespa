// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.TestUtil;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceClusterSet;
import static com.yahoo.vespa.orchestrator.TestUtil.makeServiceInstanceSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author hakonhall
 */
public class VespaModelUtilTest {

    // Cluster Controller Service Cluster

    private static final ClusterId CONTENT_CLUSTER_ID = new ClusterId("content-cluster-0");

    public static final HostName controller0Host = new HostName("controller-0");

    private static final ServiceInstance controller0 = new ServiceInstance(
            TestUtil.clusterControllerConfigId(CONTENT_CLUSTER_ID.toString(), 0),
            controller0Host,
            ServiceStatus.UP);
    private static final ServiceInstance controller1 = new ServiceInstance(
            TestUtil.clusterControllerConfigId(CONTENT_CLUSTER_ID.toString(), 1),
            new HostName("controller-1"),
            ServiceStatus.UP);

    private static final ServiceCluster controllerCluster =
            new ServiceCluster(
                    new ClusterId(CONTENT_CLUSTER_ID.s() + "-controller"),
                    VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
                    makeServiceInstanceSet(controller1, controller0));

    // Distributor Service Cluster

    private static final ServiceInstance distributor0 = new ServiceInstance(
            new ConfigId("distributor-config-id"),
            new HostName("distributor-0"),
            ServiceStatus.UP);


    private static final ServiceCluster distributorCluster =
            new ServiceCluster(
                    CONTENT_CLUSTER_ID,
                    VespaModelUtil.DISTRIBUTOR_SERVICE_TYPE,
                    makeServiceInstanceSet(distributor0));

    // Storage Node Service Cluster

    public static final HostName storage0Host = new HostName("storage-0");
    private static final ServiceInstance storage0 = new ServiceInstance(
            new ConfigId("storage-config-id"),
            storage0Host,
            ServiceStatus.UP);

    private static final ServiceCluster storageCluster =
            new ServiceCluster(
                    CONTENT_CLUSTER_ID,
                    VespaModelUtil.STORAGENODE_SERVICE_TYPE,
                    makeServiceInstanceSet(storage0));

    // Secondary Distributor Service Cluster

    private static final ServiceInstance secondaryDistributor0 = new ServiceInstance(
            new ConfigId("secondary-distributor-config-id"),
            new HostName("secondary-distributor-0"),
            ServiceStatus.UP);

    private static final ClusterId SECONDARY_CONTENT_CLUSTER_ID = new ClusterId("secondary-content-cluster-0");
    private static final ServiceCluster secondaryDistributorCluster =
            new ServiceCluster(
                    SECONDARY_CONTENT_CLUSTER_ID,
                    VespaModelUtil.DISTRIBUTOR_SERVICE_TYPE,
                    makeServiceInstanceSet(secondaryDistributor0));

    // Secondary Storage Node Service Cluster

    public static final HostName secondaryStorage0Host = new HostName("secondary-storage-0");
    private static final ServiceInstance secondaryStorage0 = new ServiceInstance(
            new ConfigId("secondary-storage-config-id"),
            secondaryStorage0Host,
            ServiceStatus.UP);

    private static final ServiceCluster secondaryStorageCluster =
            new ServiceCluster(
                    SECONDARY_CONTENT_CLUSTER_ID,
                    VespaModelUtil.STORAGENODE_SERVICE_TYPE,
                    makeServiceInstanceSet(secondaryStorage0));

    // The Application Instance

    public static final ApplicationInstance application =
            new ApplicationInstance(
                    new TenantId("tenant-0"),
                    new ApplicationInstanceId("application-0"),
                    makeServiceClusterSet(
                            controllerCluster,
                            distributorCluster,
                            storageCluster,
                            secondaryDistributorCluster,
                            secondaryStorageCluster));

    private ServiceCluster createServiceCluster(ServiceType serviceType) {
        return new ServiceCluster(
                new ClusterId("cluster-id"),
                serviceType,
                new HashSet<>());
    }

    @Test
    public void verifyControllerClusterIsRecognized() {
        ServiceCluster cluster = createServiceCluster(VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isClusterController(cluster));
    }

    @Test
    public void verifyNonControllerClusterIsNotRecognized() {
        ServiceCluster cluster = createServiceCluster(new ServiceType("foo"));
        assertFalse(VespaModelUtil.isClusterController(cluster));
    }

    @Test
    public void verifyStorageClusterIsRecognized() {
        ServiceCluster cluster = createServiceCluster(VespaModelUtil.STORAGENODE_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isStorage(cluster));
        cluster = createServiceCluster(VespaModelUtil.STORAGENODE_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isStorage(cluster));
    }

    @Test
    public void verifyNonStorageClusterIsNotRecognized() {
        ServiceCluster cluster = createServiceCluster(new ServiceType("foo"));
        assertFalse(VespaModelUtil.isStorage(cluster));
    }

    @Test
    public void verifyContentClusterIsRecognized() {
        ServiceCluster cluster = createServiceCluster(VespaModelUtil.DISTRIBUTOR_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isContent(cluster));
        cluster = createServiceCluster(VespaModelUtil.STORAGENODE_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isContent(cluster));
        cluster = createServiceCluster(VespaModelUtil.SEARCHNODE_SERVICE_TYPE);
        assertTrue(VespaModelUtil.isContent(cluster));
    }

    @Test
    public void verifyNonContentClusterIsNotRecognized() {
        ServiceCluster cluster = createServiceCluster(new ServiceType("foo"));
        assertFalse(VespaModelUtil.isContent(cluster));
    }

    @Test
    public void testGettingClusterControllerInstances() {
        List<HostName> controllers = VespaModelUtil.getClusterControllerInstancesInOrder(application, CONTENT_CLUSTER_ID);
        List<HostName> expectedControllers = Arrays.asList(controller0.hostName(), controller1.hostName());

        assertEquals(expectedControllers, controllers);
    }

    @Test
    public void testGetControllerHostName() {
        HostName host = VespaModelUtil.getControllerHostName(application, CONTENT_CLUSTER_ID);
        assertEquals(controller0Host, host);
    }

    @Test
    public void testGetContentClusterName() {
        ClusterId contentClusterName = VespaModelUtil.getContentClusterName(application, distributor0.hostName());
        assertEquals(CONTENT_CLUSTER_ID, contentClusterName);
    }

    @Test
    public void testGetContentClusterNameForSecondaryContentCluster() {
        ClusterId contentClusterName = VespaModelUtil.getContentClusterName(application, secondaryDistributor0.hostName());
        assertEquals(contentClusterName, SECONDARY_CONTENT_CLUSTER_ID);
    }

    @Test
    public void testGetStorageNodeAtHost() {
        Optional<ServiceInstance> service =
                VespaModelUtil.getStorageNodeAtHost(application, storage0Host);
        assertTrue(service.isPresent());
        assertEquals(storage0, service.get());
    }

    @Test
    public void testGetStorageNodeAtHostWithUnknownHost() {
        Optional<ServiceInstance> service =
                VespaModelUtil.getStorageNodeAtHost(application, new HostName("storage-1"));
        assertFalse(service.isPresent());
    }

    @Test
    public void testGetClusterControllerIndex() {
        ConfigId configId = new ConfigId("admin/cluster-controllers/2");
        assertEquals(2, VespaModelUtil.getClusterControllerIndex(configId));
    }

    @Test
    public void testGetClusterControllerIndexWithStandaloneClusterController() {
        ConfigId configId = new ConfigId("fantasy_sports/standalone/fantasy_sports-controllers/1");
        assertEquals(1, VespaModelUtil.getClusterControllerIndex(configId));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadClusterControllerConfigId() {
        ConfigId configId = new ConfigId("fantasy_sports/storage/9");
        VespaModelUtil.getClusterControllerIndex(configId);
        fail();
    }

    @Test
    public void testGetStorageNodeIndex() {
        ConfigId configId = TestUtil.storageNodeConfigId(CONTENT_CLUSTER_ID.toString(), 3);
        assertEquals(3, VespaModelUtil.getStorageNodeIndex(configId));
    }

}
