// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.jaxrs.client.JaxRsClientFactory;
import com.yahoo.vespa.jaxrs.client.JaxRsStrategy;
import com.yahoo.vespa.jaxrs.client.NoRetryJaxRsStrategy;

import java.util.List;
import java.util.logging.Logger;

/**
 * @author bakksjo
 */
public class SingleInstanceClusterControllerClientFactory implements ClusterControllerClientFactory {

    public static final int CLUSTERCONTROLLER_HARDCODED_PORT = 19050;
    public static final String CLUSTERCONTROLLER_API_PATH = "/";

    private static final Logger log = Logger.getLogger(SingleInstanceClusterControllerClientFactory.class.getName());

    private JaxRsClientFactory jaxRsClientFactory;

    public SingleInstanceClusterControllerClientFactory(JaxRsClientFactory jaxRsClientFactory) {
        this.jaxRsClientFactory = jaxRsClientFactory;
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers,
                                                String clusterName) {
        if (clusterControllers.isEmpty()) {
            throw new IllegalArgumentException("No cluster controller instances found");
        }
        HostName controllerHostName = clusterControllers.iterator().next();
        int port = CLUSTERCONTROLLER_HARDCODED_PORT;  // TODO: Get this from service monitor.

        log.log(LogLevel.DEBUG, () ->
                "For cluster '" + clusterName + "' with controllers " + clusterControllers
                        + ", creating api client for " + controllerHostName.s() + ":" + port);

        JaxRsStrategy<ClusterControllerJaxRsApi> strategy = new NoRetryJaxRsStrategy<>(
                controllerHostName,
                port,
                jaxRsClientFactory,
                ClusterControllerJaxRsApi.class,
                CLUSTERCONTROLLER_API_PATH);

        return new ClusterControllerClientImpl(strategy, clusterName);
    }

}
