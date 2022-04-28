// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import ai.vespa.hosted.client.AbstractHttpClient;
import ai.vespa.hosted.client.HttpClient;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bakksjo
 */
public class RetryingClusterControllerClientFactory extends AbstractComponent implements ClusterControllerClientFactory {

    private static Logger log = Logger.getLogger(RetryingClusterControllerClientFactory.class.getName());

    private final HttpClient client;

    @Inject
    public RetryingClusterControllerClientFactory() {
        this(AbstractHttpClient.wrapping(VespaHttpClientBuilder.create()
                                                               .setUserAgent("orchestrator-cluster-controller-client")
                                                               .build()));
    }

    RetryingClusterControllerClientFactory(HttpClient client) {
        this.client = client;
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName) {
        List<HostName> hosts = clusterControllers.size() == 1
                                // If there's only 1 CC, we'll try that one twice.
                                ? List.of(clusterControllers.get(0), clusterControllers.get(0))
                                // Otherwise, try each host once:
                                //  * if host 1 responds, it will redirect to master if necessary; otherwise
                                //  * if host 2 responds, it will redirect to master if necessary; otherwise
                                //  * if host 3 responds, it may redirect to master if necessary (if they're up
                                //    after all), but more likely there's no quorum and this will fail too.
                                : List.copyOf(clusterControllers);
        return new ClusterControllerClientImpl(client, hosts, clusterName);
    }

    @Override
    public void deconstruct() {
        try {
            client.close();
        }
        catch (IOException e) {
            log.log(Level.WARNING, "failed shutting down", e);
        }
    }

}
