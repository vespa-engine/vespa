// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.google.inject.Inject;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author bakksjo
 */
public class RetryingClusterControllerClientFactory implements ClusterControllerClientFactory {

    // TODO: Figure this port out dynamically.
    public static final int HARDCODED_CLUSTERCONTROLLER_PORT = 19050;
    public static final String CLUSTERCONTROLLER_API_PATH = "/";
    private static final int CLUSTER_CONTROLLER_CONNECT_TIMEOUT_MS = 1000;
    private static final int CLUSTER_CONTROLLER_READ_TIMEOUT_MS = 1000;

    private JaxRsClientFactory jaxRsClientFactory;

    @Inject
    public RetryingClusterControllerClientFactory() {
        this(new JerseyJaxRsClientFactory(CLUSTER_CONTROLLER_CONNECT_TIMEOUT_MS, CLUSTER_CONTROLLER_READ_TIMEOUT_MS));
    }

    public RetryingClusterControllerClientFactory(JaxRsClientFactory jaxRsClientFactory) {
        this.jaxRsClientFactory = jaxRsClientFactory;
    }

    @Override
    public ClusterControllerClient createClient(Collection<? extends ServiceInstance<?>> clusterControllers,
                                                String clusterName) {
        Set<HostName> hostNames = clusterControllers.stream()
                .map(s -> s.hostName())
                .collect(Collectors.toSet());
        JaxRsStrategy<ClusterControllerJaxRsApi> jaxRsApi
                = new JaxRsStrategyFactory(hostNames, HARDCODED_CLUSTERCONTROLLER_PORT, jaxRsClientFactory)
                .apiWithRetries(ClusterControllerJaxRsApi.class, CLUSTERCONTROLLER_API_PATH);
        return new ClusterControllerClientImpl(jaxRsApi, clusterName);
    }

}
