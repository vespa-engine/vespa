// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.orchestrator.TestUtil;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SingleInstanceClusterControllerClientFactoryTest {
    private static final int PORT = SingleInstanceClusterControllerClientFactory.CLUSTERCONTROLLER_HARDCODED_PORT;
    private static final String PATH = SingleInstanceClusterControllerClientFactory.CLUSTERCONTROLLER_API_PATH;

    private static final HostName HOST_NAME_1 = new HostName("host1");
    private static final HostName HOST_NAME_2 = new HostName("host2");
    private static final HostName HOST_NAME_3 = new HostName("host3");

    private final ClusterControllerJaxRsApi mockApi = mock(ClusterControllerJaxRsApi.class);
    private final JaxRsClientFactory jaxRsClientFactory = mock(JaxRsClientFactory.class);
    private final ClusterControllerClientFactory clientFactory
            = new SingleInstanceClusterControllerClientFactory(jaxRsClientFactory);

    @Before
    public void setup() {
        when(
                jaxRsClientFactory.createClient(
                        eq(ClusterControllerJaxRsApi.class),
                        any(HostName.class),
                        anyInt(),
                        anyString()))
                .thenReturn(mockApi);
    }

    @Test
    public void testCreateClientWithNoClusterControllerInstances() throws Exception {
        final Collection<ServiceInstance<ServiceMonitorStatus>> clusterControllers = Collections.emptySet();

        try {
            clientFactory.createClient(clusterControllers, "clusterName");
            fail();
        } catch (IllegalArgumentException e) {
            // As expected.
        }
    }

    @Test
    public void testCreateClientWithSingleClusterControllerInstance() throws Exception {
        final Collection<ServiceInstance<ServiceMonitorStatus>> clusterControllers = Collections.singleton(
                new ServiceInstance<>(clusterControllerConfigId(1), HOST_NAME_1, ServiceMonitorStatus.UP));

        clientFactory.createClient(clusterControllers, "clusterName")
                .setNodeState(0, ClusterControllerState.MAINTENANCE);

        verify(jaxRsClientFactory).createClient(
                ClusterControllerJaxRsApi.class,
                HOST_NAME_1,
                PORT,
                PATH);
    }

    @Test
    public void testCreateClientWithTwoNonClusterControllerInstances() throws Exception {
        final Collection<ServiceInstance<ServiceMonitorStatus>> clusterControllers = TestUtil.makeServiceInstanceSet(
                new ServiceInstance<>(new ConfigId("not-a-cluster-controller-1"), HOST_NAME_1, ServiceMonitorStatus.UP),
                new ServiceInstance<>(new ConfigId("not-a-cluster-controller-2"), HOST_NAME_2, ServiceMonitorStatus.UP));

        try {
            clientFactory.createClient(clusterControllers, "clusterName");
            fail();
        } catch (IllegalArgumentException e) {
            // As expected.
        }
    }

    @Test
    public void testCreateClientWithThreeClusterControllerInstances() throws Exception {
        final Collection<ServiceInstance<ServiceMonitorStatus>> clusterControllers = TestUtil.makeServiceInstanceSet(
                new ServiceInstance<>(clusterControllerConfigId(1), HOST_NAME_1, ServiceMonitorStatus.UP),
                new ServiceInstance<>(clusterControllerConfigId(2), HOST_NAME_2, ServiceMonitorStatus.UP),
                new ServiceInstance<>(clusterControllerConfigId(3), HOST_NAME_3, ServiceMonitorStatus.UP));

        clientFactory.createClient(clusterControllers, "clusterName")
                .setNodeState(0, ClusterControllerState.MAINTENANCE);

        verify(jaxRsClientFactory).createClient(
                eq(ClusterControllerJaxRsApi.class),
                argThat(is(anyOf(
                        equalTo(HOST_NAME_1),
                        equalTo(HOST_NAME_2),
                        equalTo(HOST_NAME_3)))),
                eq(PORT),
                eq(PATH));
    }

    private static ConfigId clusterControllerConfigId(final int index) {
        return new ConfigId("admin/cluster-controllers/" + index);
    }
}
