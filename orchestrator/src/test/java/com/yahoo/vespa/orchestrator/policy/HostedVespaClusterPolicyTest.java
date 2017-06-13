package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.applicationmodel.TenantId;
import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HostedVespaClusterPolicyTest {
    private ClusterApi clusterApi = mock(ClusterApi.class);
    private HostedVespaClusterPolicy policy = spy(new HostedVespaClusterPolicy());

    @Test
    public void testSlobrokSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(VespaModelUtil.ADMIN_CLUSTER_ID);
        when(clusterApi.serviceType()).thenReturn(VespaModelUtil.SLOBROK_SERVICE_TYPE);
        assertEquals(ConcurrentSuspensionLimitForCluster.ONE_NODE,
                policy.getConcurrentSuspensionLimit(clusterApi));
    }

    @Test
    public void testAdminSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(VespaModelUtil.ADMIN_CLUSTER_ID);
        when(clusterApi.serviceType()).thenReturn(new ServiceType("non-slobrok-service-type"));
        assertEquals(ConcurrentSuspensionLimitForCluster.ALL_NODES,
                policy.getConcurrentSuspensionLimit(clusterApi));
    }

    @Test
    public void testStorageSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(new ClusterId("some-cluster-id"));
        when(clusterApi.isStorageCluster()).thenReturn(true);
        assertEquals(ConcurrentSuspensionLimitForCluster.ONE_NODE,
                policy.getConcurrentSuspensionLimit(clusterApi));
    }

    @Test
    public void testDefaultSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(new ClusterId("some-cluster-id"));
        when(clusterApi.isStorageCluster()).thenReturn(false);
        assertEquals(ConcurrentSuspensionLimitForCluster.TEN_PERCENT,
                policy.getConcurrentSuspensionLimit(clusterApi));
    }

    @Test
    public void verifyGroupGoingDownIsFine_noServicesOutsideGroupIsDownIsFine() {
        verifyGroupGoingDownIsFine(true, false, 13, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_noServicesInGroupIsUp() {
        verifyGroupGoingDownIsFine(false, true, 13, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_percentageIsFine() {
        verifyGroupGoingDownIsFine(false, false, 9, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_fails() {
        verifyGroupGoingDownIsFine(false, false, 13, false);
    }

    private void verifyGroupGoingDownIsFine(boolean noServicesOutsideGroupIsDown,
                                            boolean noServicesInGroupIsUp,
                                            int percentageOfServicesDownIfGroupIsAllowedToBeDown,
                                            boolean expectSuccess) {
        when(clusterApi.noServicesOutsideGroupIsDown()).thenReturn(noServicesOutsideGroupIsDown);
        when(clusterApi.noServicesInGroupIsUp()).thenReturn(noServicesInGroupIsUp);
        when(clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()).thenReturn(20);
        doReturn(ConcurrentSuspensionLimitForCluster.TEN_PERCENT).when(policy).getConcurrentSuspensionLimit(clusterApi);

        when(clusterApi.serviceType()).thenReturn(new ServiceType("service-type"));
        when(clusterApi.percentageOfServicesDown()).thenReturn(5);
        when(clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()).thenReturn(percentageOfServicesDownIfGroupIsAllowedToBeDown);
        when(clusterApi.servicesDownAndNotInGroupDescription()).thenReturn("services-down-and-not-in-group");
        when(clusterApi.nodesAllowedToBeDownNotInGroupDescription()).thenReturn("allowed-to-be-down");

        NodeGroup nodeGroup = mock(NodeGroup.class);
        when(clusterApi.getNodeGroup()).thenReturn(nodeGroup);
        when(nodeGroup.toCommaSeparatedString()).thenReturn("node-group");

        when(clusterApi.noServicesInGroupIsUp()).thenReturn(false);
        try {
            policy.verifyGroupGoingDownIsFine(clusterApi);
            if (!expectSuccess) {
                fail();
            }
        } catch (HostStateChangeDeniedException e) {
            if (!expectSuccess) {
                assertEquals("Changing the state of node-group would violate enough-services-up: "
                        + "Suspension percentage for service type service-type would increase from "
                        + "5% to 13%, over the limit of 10%. These instances may be down: "
                        + "services-down-and-not-in-group and these hosts are allowed to be down: "
                        + "allowed-to-be-down", e.getMessage());
                assertEquals("enough-services-up", e.getConstraintName());
            }
        }
    }
}