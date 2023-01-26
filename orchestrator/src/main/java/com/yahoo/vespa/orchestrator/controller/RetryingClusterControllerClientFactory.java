// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.controller;

import ai.vespa.hosted.client.AbstractHttpClient;
import ai.vespa.hosted.client.HttpClient;
import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.vespa.applicationmodel.HostName;
import org.apache.hc.core5.http.message.BasicHeader;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author jonmv
 */
public class RetryingClusterControllerClientFactory extends AbstractComponent implements ClusterControllerClientFactory {

    private static Logger log = Logger.getLogger(RetryingClusterControllerClientFactory.class.getName());

    private final HttpClient client;

    @Inject
    public RetryingClusterControllerClientFactory() {
        this(AbstractHttpClient.wrapping(VespaHttpClientBuilder.create()
                                                               .setUserAgent("orchestrator-cluster-controller-client")
                                                               .setDefaultHeaders(List.of(new BasicHeader("Accept", "application/json")))
                                                               .build()));
    }

    RetryingClusterControllerClientFactory(HttpClient client) {
        this.client = client;
    }

    @Override
    public ClusterControllerClient createClient(List<HostName> clusterControllers, String clusterName) {
        return new ClusterControllerClientImpl(client, clusterControllers, clusterName);
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
