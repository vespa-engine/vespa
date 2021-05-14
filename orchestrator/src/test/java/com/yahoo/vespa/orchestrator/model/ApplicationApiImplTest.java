// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceStatus;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class ApplicationApiImplTest {
    final ModelTestUtils modelUtils = new ModelTestUtils();

    @Test
    public void testApplicationId() {
        try (var api = modelUtils.createScopedApplicationApi(modelUtils.createApplicationInstance(new ArrayList<>()))) {
            assertEquals("tenant:application-name:default", api.applicationApi().applicationId().serializedForm());
        }
    }

    @Test
    public void testGetClustersThatAreOnAtLeastOneNodeInGroup() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");
        HostName hostName4 = new HostName("host4");

        ApplicationInstance applicationInstance =
                modelUtils.createApplicationInstance(Arrays.asList(
                        modelUtils.createServiceCluster(
                                "cluster-3",
                                new ServiceType("service-type-3"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-1", hostName1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-2", hostName2, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-1",
                                new ServiceType("service-type-1"),
                                Arrays.asList(
                                    modelUtils.createServiceInstance("config-id-3", hostName1, ServiceStatus.UP),
                                    modelUtils.createServiceInstance("config-id-4", hostName3, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-2",
                                new ServiceType("service-type-2"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-5", hostName1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-6", hostName2, ServiceStatus.UP)
                                )
                        )
                ));

        verifyClustersInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName1), 1, 2, 3);
        verifyClustersInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName2), 2, 3);
        verifyClustersInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName3), 1);
        verifyClustersInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName4));
    }

    private void verifyClustersInOrder(ScopedApplicationApi scopedApi,
                                       Integer... expectedClusterNumbers) {
        try (scopedApi) {
            // Note: we require the clusters to be in order.
            List<ClusterApi> clusterApis = scopedApi.applicationApi().getClusters();
            String clusterInfos = clusterApis.stream().map(clusterApi -> clusterApi.clusterInfo()).collect(Collectors.joining(","));

            String expectedClusterInfos = Arrays.stream(expectedClusterNumbers)
                    .map(number -> "{ clusterId=cluster-" + number + ", serviceType=service-type-" + number + " }")
                    .collect(Collectors.joining(","));

            assertEquals(expectedClusterInfos, clusterInfos);
        }
    }

    @Test
    public void testGetUpStorageNodesInGroupInClusterOrder() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");
        HostName hostName4 = new HostName("host4");
        HostName hostName5 = new HostName("host5");
        HostName hostName6 = new HostName("host6");
        HostName hostName7 = new HostName("host7");

        ApplicationInstance applicationInstance =
                modelUtils.createApplicationInstance(Arrays.asList(
                        modelUtils.createServiceCluster(
                                "cluster-3",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                    modelUtils.createServiceInstance("config-id-30", hostName1, ServiceStatus.UP),
                                    modelUtils.createServiceInstance("config-id-31", hostName2, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-1",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-10", hostName3, ServiceStatus.DOWN),
                                        modelUtils.createServiceInstance("config-id-11", hostName4, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-4",
                                new ServiceType("service-type-4"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-40", hostName1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-41", hostName2, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-42", hostName3, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-43", hostName5, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-2",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-20", hostName6, ServiceStatus.DOWN),
                                        modelUtils.createServiceInstance("config-id-21", hostName7, ServiceStatus.UP)
                                )
                        )
                ));

        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName1), hostName1);
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName2), hostName2);
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName3)); // host3 is DOWN
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName4), hostName4);
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName5)); // not a storage cluster

        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName1, hostName3), hostName1);

        // For the node group (host1, host4), they both have an up storage node (service instance)
        // with clusters (cluster-3, cluster-1) respectively, and so the order of the hosts are reversed
        // (host4, host1) when sorted by the clusters.
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(applicationInstance, hostName1, hostName4), hostName4, hostName1);

        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(
                applicationInstance, hostName1, hostName4, hostName5), hostName4, hostName1);
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(
                applicationInstance, hostName1, hostName4, hostName5, hostName6), hostName4, hostName1);
        verifyUpStorageNodesInOrder(modelUtils.createScopedApplicationApi(
                applicationInstance, hostName1, hostName4, hostName5, hostName7), hostName4, hostName7, hostName1);
    }

    private void verifyUpStorageNodesInOrder(ScopedApplicationApi scopedApi,
                                             HostName... expectedHostNames) {
        try (scopedApi) {
            List<HostName> upStorageNodes = scopedApi.applicationApi().getUpStorageNodesInGroupInClusterOrder().stream()
                    .map(storageNode -> storageNode.hostName())
                    .collect(Collectors.toList());
            assertEquals(Arrays.asList(expectedHostNames), upStorageNodes);
        }
    }

    @Test
    public void testUpConditionOfStorageNode() {
        verifyUpConditionWith(HostStatus.NO_REMARKS, ServiceStatus.UP, true);
        verifyUpConditionWith(HostStatus.NO_REMARKS, ServiceStatus.NOT_CHECKED, true);
        verifyUpConditionWith(HostStatus.NO_REMARKS, ServiceStatus.DOWN, false);
        verifyUpConditionWith(HostStatus.ALLOWED_TO_BE_DOWN, ServiceStatus.UP, false);
        verifyUpConditionWith(HostStatus.ALLOWED_TO_BE_DOWN, ServiceStatus.NOT_CHECKED, false);
        verifyUpConditionWith(HostStatus.ALLOWED_TO_BE_DOWN, ServiceStatus.DOWN, false);
    }

    private void verifyUpConditionWith(HostStatus hostStatus, ServiceStatus serviceStatus, boolean expectUp) {
        HostName hostName1 = new HostName("host1");
        ApplicationInstance applicationInstance =
                modelUtils.createApplicationInstance(Arrays.asList(
                        modelUtils.createServiceCluster(
                                "cluster-1",
                                ServiceType.STORAGE,
                                Arrays.asList(modelUtils.createServiceInstance("config-id-1", hostName1, serviceStatus))
                        )
                ));

        modelUtils.createNode("host1", hostStatus);

        try (var scopedApi = modelUtils.createScopedApplicationApi(applicationInstance, hostName1)) {
            List<HostName> upStorageNodes = expectUp ? Arrays.asList(hostName1) : new ArrayList<>();

            List<HostName> actualStorageNodes = scopedApi.applicationApi()
                    .getUpStorageNodesInGroupInClusterOrder()
                    .stream()
                    .map(storageNode -> storageNode.hostName())
                    .collect(Collectors.toList());
            assertEquals(upStorageNodes, actualStorageNodes);
        }
    }

    @Test
    public void testGetNodesInGroupWithStatus() {
        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");

        ApplicationInstance applicationInstance =
                modelUtils.createApplicationInstance(Arrays.asList(
                        modelUtils.createServiceCluster(
                                "cluster-1",
                                new ServiceType("service-type-1"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-10", hostName1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-11", hostName2, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-2",
                                new ServiceType("service-type-2"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-20", hostName1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-21", hostName3, ServiceStatus.UP)
                                )
                        )
                ));

        modelUtils.createNode(hostName1, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName2, HostStatus.NO_REMARKS);
        modelUtils.createNode(hostName3, HostStatus.ALLOWED_TO_BE_DOWN);

        verifyNodesInGroupWithoutRemarks(
                modelUtils.createScopedApplicationApi(applicationInstance, hostName1),
                Arrays.asList(hostName1),
                Arrays.asList());
        verifyNodesInGroupWithoutRemarks(
                modelUtils.createScopedApplicationApi(applicationInstance, hostName1, hostName2),
                Arrays.asList(hostName1, hostName2),
                Arrays.asList());
        verifyNodesInGroupWithoutRemarks(
                modelUtils.createScopedApplicationApi(applicationInstance, hostName1, hostName2, hostName3),
                Arrays.asList(hostName1, hostName2),
                Arrays.asList(hostName3));
        verifyNodesInGroupWithoutRemarks(
                modelUtils.createScopedApplicationApi(applicationInstance, hostName3),
                Arrays.asList(),
                Arrays.asList(hostName3));
    }

    private void verifyNodesInGroupWithoutRemarks(ScopedApplicationApi scopedApi,
                                                  List<HostName> noRemarksHostNames,
                                                  List<HostName> allowedToBeDownHostNames) {
        try (scopedApi) {
            List<HostName> actualNoRemarksHosts = scopedApi.applicationApi().getNodesInGroupWithStatus(HostStatus.NO_REMARKS);
            assertEquals(noRemarksHostNames, actualNoRemarksHosts);
            List<HostName> actualAllowedToBeDownHosts = scopedApi.applicationApi()
                    .getNodesInGroupWithStatus(HostStatus.ALLOWED_TO_BE_DOWN);
            assertEquals(allowedToBeDownHostNames, actualAllowedToBeDownHosts);
        }
    }

    @Test
    public void testGetStorageNodesAllowedToBeDownInGroupInReverseClusterOrder() {
        HostName allowedToBeDownHost1 = new HostName("host1");
        HostName noRemarksHost2 = new HostName("host2");
        HostName allowedToBeDownHost3 = new HostName("host3");
        HostName allowedToBeDownHost4 = new HostName("host4");
        HostName noRemarksHost5 = new HostName("host5");
        HostName noRemarksHost6 = new HostName("host6");
        HostName allowedToBeDownHost7 = new HostName("host7");

        ApplicationInstance applicationInstance =
                modelUtils.createApplicationInstance(Arrays.asList(
                        modelUtils.createServiceCluster(
                                "cluster-4",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-40", allowedToBeDownHost1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-41", noRemarksHost2, ServiceStatus.DOWN)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-1",
                                new ServiceType("service-type-1"),
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-10", allowedToBeDownHost1, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-11", allowedToBeDownHost3, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-3",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-30", allowedToBeDownHost4, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-31", noRemarksHost5, ServiceStatus.UP)
                                )
                        ),
                        modelUtils.createServiceCluster(
                                "cluster-2",
                                ServiceType.STORAGE,
                                Arrays.asList(
                                        modelUtils.createServiceInstance("config-id-20", noRemarksHost6, ServiceStatus.UP),
                                        modelUtils.createServiceInstance("config-id-21", allowedToBeDownHost7, ServiceStatus.UP)
                                )
                        )
                ));

        modelUtils.createNode(allowedToBeDownHost1, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(noRemarksHost2, HostStatus.NO_REMARKS);
        modelUtils.createNode(allowedToBeDownHost3, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(allowedToBeDownHost4, HostStatus.ALLOWED_TO_BE_DOWN);
        modelUtils.createNode(noRemarksHost5, HostStatus.ALLOWED_TO_BE_DOWN); // Really?
        modelUtils.createNode(noRemarksHost6, HostStatus.NO_REMARKS);
        modelUtils.createNode(allowedToBeDownHost7, HostStatus.ALLOWED_TO_BE_DOWN);

        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost1), allowedToBeDownHost1);
        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, noRemarksHost2));
        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost3));

        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost1, noRemarksHost6), allowedToBeDownHost1);

        // allowedToBeDownHost4 is in cluster-3, while allowedToBeDownHost1 is in cluster-4, so allowedToBeDownHost4 should be ordered
        // before allowedToBeDownHost1.
        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost1, noRemarksHost6, allowedToBeDownHost4),
                allowedToBeDownHost4, allowedToBeDownHost1);

        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost1, allowedToBeDownHost4, allowedToBeDownHost7),
                allowedToBeDownHost7, allowedToBeDownHost4, allowedToBeDownHost1);

        verifyStorageNodesAllowedToBeDown(
                modelUtils.createScopedApplicationApi(applicationInstance, allowedToBeDownHost4, allowedToBeDownHost1, allowedToBeDownHost7),
                allowedToBeDownHost7, allowedToBeDownHost4, allowedToBeDownHost1);
    }

    private void verifyStorageNodesAllowedToBeDown(
            ScopedApplicationApi scopedApi, HostName... hostNames) {
        try (scopedApi) {
            List<HostName> actualStorageNodes = scopedApi
                    .applicationApi()
                    .getSuspendedStorageNodesInGroupInReverseClusterOrder()
                    .stream()
                    .map(storageNode -> storageNode.hostName())
                    .collect(Collectors.toList());
            assertEquals(Arrays.asList(hostNames), actualStorageNodes);
        }
    }
}
