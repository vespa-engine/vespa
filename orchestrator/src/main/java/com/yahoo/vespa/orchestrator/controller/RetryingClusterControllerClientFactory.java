// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.google.inject.Inject;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.JerseyJaxRsClientFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author bakksjo
 */
public class RetryingClusterControllerClientFactory implements ClusterControllerClientFactory {

    // TODO: Figure this port out dynamically.
    public static final int HARDCODED_CLUSTERCONTROLLER_PORT = 19050;
    public static final String CLUSTERCONTROLLER_API_PATH = "/";
    public static final String CLUSTERCONTROLLER_SCHEME = "http";
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
    public ClusterControllerClient createClient(List<HostName> clusterControllers,
                                                String clusterName) {
        Set<HostName> clusterControllerSet = clusterControllers.stream().collect(Collectors.toSet());
        JaxRsStrategy<ClusterControllerJaxRsApi> jaxRsApi
                = new JaxRsStrategyFactory(clusterControllerSet, HARDCODED_CLUSTERCONTROLLER_PORT, jaxRsClientFactory, CLUSTERCONTROLLER_SCHEME)
                .apiWithRetries(ClusterControllerJaxRsApi.class, CLUSTERCONTROLLER_API_PATH)
                // Use max iteration 1. The JaxRsStrategyFactory will try host 1, 2, then 3:
                //  - If host 1 responds, it will redirect to master if necessary. Otherwise
                //  - If host 2 responds, it will redirect to master if necessary. Otherwise
                //  - If host 3 responds, it may redirect to master if necessary (if they're up
                //    after all), but more likely there's no quorum and this will fail too.
                .setMaxIterations(1);
        return new ClusterControllerClientImpl(jaxRsApi, clusterName);
    }

}
