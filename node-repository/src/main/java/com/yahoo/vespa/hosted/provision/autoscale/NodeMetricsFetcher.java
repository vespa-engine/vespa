// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches node metrics over the metrics/v2 API
 *
 * @author bratseth
 */
public class NodeMetricsFetcher extends AbstractComponent implements NodeMetrics {

    private static final Logger log = Logger.getLogger(NodeMetricsFetcher.class.getName());

    private static final String apiPath = "/metrics/v2/values";

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final HttpClient httpClient;

    @Inject
    @SuppressWarnings("unused")
    public NodeMetricsFetcher(NodeRepository nodeRepository, Orchestrator orchestrator) {
        this(nodeRepository, orchestrator, new ApacheHttpClient());
    }

    NodeMetricsFetcher(NodeRepository nodeRepository, Orchestrator orchestrator, HttpClient httpClient) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.httpClient = httpClient;
    }

    @Override
    public Collection<MetricValue> fetchMetrics(ApplicationId application) {
        Optional<Node> metricsV2Container = nodeRepository.list()
                                                          .owner(application)
                                                          .state(Node.State.active)
                                                          .container()
                                                          .filter(node -> expectedUp(node))
                                                          .stream()
                                                          .findFirst();
        if (metricsV2Container.isEmpty()) return Collections.emptyList();
        String url = "http://" + metricsV2Container.get().hostname() + ":" + 4080 + apiPath + "?consumer=default";
        String response = httpClient.get(url);
        return new MetricsResponse(response).metrics();
    }

    @Override
    public void deconstruct() {
        httpClient.close();
    }

    private boolean expectedUp(Node node) {
        try {
            return ! orchestrator.getNodeStatus(new HostName(node.hostname())).isSuspended();
        }
        catch (HostNameNotFoundException e) {
            return false;
        }
    }

    /** The simplest possible http client interface */
    public interface HttpClient {

        String get(String url);
        void close();

    }

    /** Implements the HttpClient interface by delegating to an Apache HTTP client */
    public static class ApacheHttpClient implements HttpClient {

        private final CloseableHttpClient httpClient = VespaHttpClientBuilder.createWithBasicConnectionManager().build();

        @Override
        public String get(String url) {
            try {
                return httpClient.execute(new HttpGet(url), new BasicResponseHandler());
            }
            catch (IOException e) {
                throw new UncheckedIOException("Could not get " + url, e);
            }
        }

        @Override
        public void close() {
            try {
                httpClient.close();
            }
            catch (IOException e) {
                log.log(Level.WARNING, "Exception deconstructing", e);
            }
        }


    }

}
