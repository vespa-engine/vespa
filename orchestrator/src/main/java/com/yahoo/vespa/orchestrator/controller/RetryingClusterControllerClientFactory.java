// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategyFactory;
import com.yahoo.vespa.jaxrs.client.VespaJerseyJaxRsClientFactory;

import java.util.HashSet;
import java.util.List;

/**
 * @author bakksjo
 */
@SuppressWarnings("removal") // VespaJerseyJaxRsClientFactory
public class RetryingClusterControllerClientFactory extends AbstractComponent implements ClusterControllerClientFactory {

    // TODO: Figure this port out dynamically.
    public static final int HARDCODED_CLUSTERCONTROLLER_PORT = 19050;
    public static final String CLUSTERCONTROLLER_API_PATH = "/";
    public static final String CLUSTERCONTROLLER_SCHEME = "http";

    private final VespaJerseyJaxRsClientFactory jaxRsClientFactory;

    @Inject
    public RetryingClusterControllerClientFactory() {
        this(new VespaJerseyJaxRsClientFactory("orchestrator-cluster-controller-client"));
    }

    RetryingClusterControllerClientFactory(VespaJerseyJaxRsClientFactory jaxRsClientFactory) {
        this.jaxRsClientFactory = jaxRsClientFactory;
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName) {
        JaxRsStrategy<ClusterControllerJaxRsApi> jaxRsApi =
                new JaxRsStrategyFactory(
                        new HashSet<>(clusterControllers),
                        HARDCODED_CLUSTERCONTROLLER_PORT,
                        jaxRsClientFactory,
                        CLUSTERCONTROLLER_SCHEME)
                        .apiWithRetries(ClusterControllerJaxRsApi.class, CLUSTERCONTROLLER_API_PATH)
                        // Use max iteration 1: The JaxRsStrategyFactory will try host 1, 2, then 3:
                        //  - If host 1 responds, it will redirect to master if necessary. Otherwise
                        //  - If host 2 responds, it will redirect to master if necessary. Otherwise
                        //  - If host 3 responds, it may redirect to master if necessary (if they're up
                        //    after all), but more likely there's no quorum and this will fail too.
                        // If there's only 1 CC, we'll try that one twice.
                        .setMaxIterations(clusterControllers.size() > 1 ? 1 : 2);
        return new ClusterControllerClientImpl(jaxRsApi, clusterName);
    }

    @Override
    public void deconstruct() {
        jaxRsClientFactory.close();
    }
}
