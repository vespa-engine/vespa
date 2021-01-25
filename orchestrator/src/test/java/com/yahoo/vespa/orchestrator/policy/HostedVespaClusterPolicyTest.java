// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.VespaModelUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class HostedVespaClusterPolicyTest {
    private ApplicationApi applicationApi = mock(ApplicationApi.class);
    private ClusterApi clusterApi = mock(ClusterApi.class);
    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private HostedVespaClusterPolicy policy = spy(new HostedVespaClusterPolicy(flagSource));

    @Before
    public void setUp() {
        when(clusterApi.getApplication()).thenReturn(applicationApi);
    }

    @Test
    public void testSlobrokSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(VespaModelUtil.ADMIN_CLUSTER_ID);
        when(clusterApi.serviceType()).thenReturn(ServiceType.SLOBROK);
        assertEquals(ConcurrentSuspensionLimitForCluster.ONE_NODE,
                policy.getConcurrentSuspensionLimit(clusterApi, false));
    }

    @Test
    public void testAdminSuspensionLimit() {
        when(clusterApi.clusterId()).thenReturn(VespaModelUtil.ADMIN_CLUSTER_ID);
        when(clusterApi.serviceType()).thenReturn(new ServiceType("non-slobrok-service-type"));
        assertEquals(ConcurrentSuspensionLimitForCluster.ALL_NODES,
                policy.getConcurrentSuspensionLimit(clusterApi, false));
    }

    @Test
    public void testStorageSuspensionLimit() {
        when(clusterApi.serviceType()).thenReturn(ServiceType.STORAGE);
        when(clusterApi.clusterId()).thenReturn(new ClusterId("some-cluster-id"));
        when(clusterApi.isStorageCluster()).thenReturn(true);
        assertEquals(ConcurrentSuspensionLimitForCluster.ALL_NODES,
                policy.getConcurrentSuspensionLimit(clusterApi, true));
    }

    @Test
    public void testStorageSuspensionLimit_legacy() {
        when(clusterApi.clusterId()).thenReturn(new ClusterId("some-cluster-id"));
        when(clusterApi.isStorageCluster()).thenReturn(true);
        assertEquals(ConcurrentSuspensionLimitForCluster.ONE_NODE,
                policy.getConcurrentSuspensionLimit(clusterApi, false));
    }

    @Test
    public void testTenantHostSuspensionLimit() {
        when(applicationApi.applicationId()).thenReturn(VespaModelUtil.TENANT_HOST_APPLICATION_ID);
        when(clusterApi.isStorageCluster()).thenReturn(false);
        assertEquals(ConcurrentSuspensionLimitForCluster.TWENTY_PERCENT,
                policy.getConcurrentSuspensionLimit(clusterApi, false));
    }

    @Test
    public void testDefaultSuspensionLimit() {
        when(applicationApi.applicationId()).thenReturn(ApplicationId.fromSerializedForm("a:b:c"));
        when(clusterApi.clusterId()).thenReturn(new ClusterId("some-cluster-id"));
        when(clusterApi.isStorageCluster()).thenReturn(false);
        assertEquals(ConcurrentSuspensionLimitForCluster.TEN_PERCENT,
                policy.getConcurrentSuspensionLimit(clusterApi, false));
    }

    @Test
    public void verifyGroupGoingDownIsFine_noServicesOutsideGroupIsDownIsFine() {
        verifyGroupGoingDownIsFine(true, Optional.empty(), 13, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_noServicesInGroupIsUp() {
        var reasons = new SuspensionReasons().addReason(new HostName("host1"), "supension reason 1");
        verifyGroupGoingDownIsFine(false, Optional.of(reasons), 13, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_percentageIsFine() {
        verifyGroupGoingDownIsFine(false, Optional.empty(), 9, true);
    }

    @Test
    public void verifyGroupGoingDownIsFine_fails() {
        verifyGroupGoingDownIsFine(false, Optional.empty(), 13, false);
    }

    private void verifyGroupGoingDownIsFine(boolean noServicesOutsideGroupIsDown,
                                            Optional<SuspensionReasons> noServicesInGroupIsUp,
                                            int percentageOfServicesDownIfGroupIsAllowedToBeDown,
                                            boolean expectSuccess) {
        when(clusterApi.noServicesOutsideGroupIsDown()).thenReturn(noServicesOutsideGroupIsDown);
        when(clusterApi.reasonsForNoServicesInGroupIsUp()).thenReturn(noServicesInGroupIsUp);
        when(clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()).thenReturn(20);
        doReturn(ConcurrentSuspensionLimitForCluster.TEN_PERCENT).when(policy).getConcurrentSuspensionLimit(clusterApi, false);

        when(applicationApi.applicationId()).thenReturn(ApplicationId.fromSerializedForm("a:b:c"));
        when(clusterApi.serviceType()).thenReturn(new ServiceType("service-type"));
        when(clusterApi.percentageOfServicesDown()).thenReturn(5);
        when(clusterApi.percentageOfServicesDownIfGroupIsAllowedToBeDown()).thenReturn(percentageOfServicesDownIfGroupIsAllowedToBeDown);
        when(clusterApi.downDescription()).thenReturn(" Down description");

        NodeGroup nodeGroup = mock(NodeGroup.class);
        when(clusterApi.getNodeGroup()).thenReturn(nodeGroup);
        when(nodeGroup.toCommaSeparatedString()).thenReturn("node-group");

        try {
            SuspensionReasons reasons = policy.verifyGroupGoingDownIsFine(clusterApi);
            if (!expectSuccess) {
                fail();
            }

            if (noServicesInGroupIsUp.isPresent()) {
                assertEquals(noServicesInGroupIsUp.get().getMessagesInOrder(), reasons.getMessagesInOrder());
            }
        } catch (HostStateChangeDeniedException e) {
            if (!expectSuccess) {
                assertEquals("Changing the state of node-group would violate enough-services-up: " +
                        "Suspension of service with type 'service-type' would increase from 5% to 13%, " +
                        "over the limit of 10%. Down description", e.getMessage());
                assertEquals("enough-services-up", e.getConstraintName());
            }
        }
    }
}
