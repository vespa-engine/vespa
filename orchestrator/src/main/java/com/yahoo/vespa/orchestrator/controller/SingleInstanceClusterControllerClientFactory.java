// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.NoRetryJaxRsStrategy;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;

import java.util.Collection;
import java.util.Comparator;
import java.util.logging.Logger;

import static com.yahoo.vespa.orchestrator.VespaModelUtil.getClusterControllerIndex;

/**
 * @author <a href="mailto:bakksjo@yahoo-inc.com">Oyvind Bakksjo</a>
 */
public class SingleInstanceClusterControllerClientFactory implements ClusterControllerClientFactory {
    public static final int CLUSTERCONTROLLER_HARDCODED_PORT = 19050;
    public static final String CLUSTERCONTROLLER_API_PATH = "/";

    private static final Logger log = Logger.getLogger(SingleInstanceClusterControllerClientFactory.class.getName());

    private static final Comparator<ServiceInstance<?>> CLUSTER_CONTROLLER_INDEX_COMPARATOR = Comparator.comparing(
            serviceInstance ->
                    getClusterControllerIndex(serviceInstance.configId()));

    private JaxRsClientFactory jaxRsClientFactory;

    public SingleInstanceClusterControllerClientFactory(
            final JaxRsClientFactory jaxRsClientFactory) {
        this.jaxRsClientFactory = jaxRsClientFactory;
    }

    @Override
    public ClusterControllerClient createClient(
            final Collection<? extends ServiceInstance<?>> clusterControllers,
            final String clusterName) {
        final ServiceInstance<?> serviceInstance = clusterControllers.stream()
                .min(CLUSTER_CONTROLLER_INDEX_COMPARATOR)
                .orElseThrow(() -> new IllegalArgumentException("No cluster controller instances found"));
        final HostName controllerHostName = serviceInstance.hostName();
        final int port = CLUSTERCONTROLLER_HARDCODED_PORT;  // TODO: Get this from service monitor.

        log.log(LogLevel.DEBUG, () ->
                "For cluster '" + clusterName + "' with controllers " + clusterControllers
                        + ", creating api client for " + controllerHostName.s() + ":" + port);

        final JaxRsStrategy<ClusterControllerJaxRsApi> strategy = new NoRetryJaxRsStrategy<>(
                controllerHostName,
                port,
                jaxRsClientFactory,
                ClusterControllerJaxRsApi.class,
                CLUSTERCONTROLLER_API_PATH);

        return new ClusterControllerClientImpl(strategy, clusterName);
    }
}
