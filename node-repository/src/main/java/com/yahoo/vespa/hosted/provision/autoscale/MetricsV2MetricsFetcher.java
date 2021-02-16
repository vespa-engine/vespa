// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import ai.vespa.util.http.VespaAsyncHttpClientBuilder;
import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.orchestrator.HostNameNotFoundException;
import com.yahoo.vespa.orchestrator.Orchestrator;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches node metrics over the metrics/v2 API
 *
 * @author bratseth
 */
public class MetricsV2MetricsFetcher extends AbstractComponent implements MetricsFetcher {

    private static final Logger log = Logger.getLogger(MetricsV2MetricsFetcher.class.getName());

    private static final String apiPath = "/metrics/v2/values";

    private final NodeRepository nodeRepository;
    private final Orchestrator orchestrator;
    private final AsyncHttpClient httpClient;

    @Inject
    @SuppressWarnings("unused")
    public MetricsV2MetricsFetcher(NodeRepository nodeRepository, Orchestrator orchestrator) {
        this(nodeRepository, orchestrator, new AsyncApacheHttpClient());
    }

    public MetricsV2MetricsFetcher(NodeRepository nodeRepository, Orchestrator orchestrator, AsyncHttpClient httpClient) {
        this.nodeRepository = nodeRepository;
        this.orchestrator = orchestrator;
        this.httpClient = httpClient;
    }

    @Override
    public CompletableFuture<MetricsResponse> fetchMetrics(ApplicationId application) {
        NodeList applicationNodes = nodeRepository.nodes().list().owner(application).state(Node.State.active);

        Optional<Node> metricsV2Container = applicationNodes.container()
                                                            .matching(node -> expectedUp(node))
                                                            .stream()
                                                            .findFirst();
        if (metricsV2Container.isEmpty()) {
            return CompletableFuture.completedFuture(MetricsResponse.empty());
        }
        else {
            // Consumer 'autoscaling' defined in com.yahoo.vespa.model.admin.monitoring.MetricConsumer
            String url = "http://" + metricsV2Container.get().hostname() + ":" + 4080 + apiPath + "?consumer=autoscaling";
            return httpClient.get(url)
                             .thenApply(response -> new MetricsResponse(response, applicationNodes, nodeRepository));
        }
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

    /** A simple async HTTP client */
    public interface AsyncHttpClient {

        CompletableFuture<String> get(String url);
        void close();

    }

    /** Implements the AsyncHttpClient interface by delegating to an Apache HTTP client */
    public static class AsyncApacheHttpClient implements AsyncHttpClient {

        private final CloseableHttpAsyncClient httpClient = VespaAsyncHttpClientBuilder.create().build();

        public AsyncApacheHttpClient() {
            httpClient.start();
        }

        @Override
        public CompletableFuture<String> get(String url) {
            CompletableFuture<String> callback = new CompletableFuture<>();
            httpClient.execute(new SimpleHttpRequest("GET", url), new CallbackAdaptor(callback));
            return callback;
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

        private static class CallbackAdaptor implements FutureCallback<SimpleHttpResponse> {

            private final CompletableFuture<String> callback;

            public CallbackAdaptor(CompletableFuture<String> callback) {
                this.callback = callback;
            }

            @Override
            public void completed(SimpleHttpResponse simpleHttpResponse) {
                callback.complete(simpleHttpResponse.getBodyText());
            }

            @Override
            public void failed(Exception e) {
                callback.completeExceptionally(e);
            }

            @Override
            public void cancelled() {
                callback.cancel(true);
            }

        }

    }

}
