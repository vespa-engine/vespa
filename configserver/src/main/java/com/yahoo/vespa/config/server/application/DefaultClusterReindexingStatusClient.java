// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import ai.vespa.util.http.hc5.VespaAsyncHttpClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.concurrent.CompletableFutures;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.config.server.modelfactory.ModelResult;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.yahoo.config.model.api.container.ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
import static com.yahoo.yolean.Exceptions.throwUnchecked;
import static com.yahoo.yolean.Exceptions.uncheck;

/**
 * Retrieves reindexing status from cluster controllers over HTTP
 *
 * @author bjorncs
 */
public class DefaultClusterReindexingStatusClient implements ClusterReindexingStatusClient {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Executor executor =
            Executors.newSingleThreadExecutor(new DaemonThreadFactory("cluster-controller-reindexing-client-"));
    private final CloseableHttpAsyncClient httpClient = createHttpClient();

    public DefaultClusterReindexingStatusClient() {
        httpClient.start();
    }

    @Override
    public Map<String, ClusterReindexing> getReindexingStatus(ModelResult application) throws IOException {
        Map<ClusterId, List<ServiceInfo>> clusters = clusterControllerClusters(application);
        Map<ClusterId, CompletableFuture<Map<String, ClusterReindexing>>> futureStatusPerCluster = new HashMap<>();
        clusters.forEach((clusterId, clusterNodes) -> {
            var parallelRequests = clusterNodes.stream()
                    .map(this::getReindexingStatus)
                    .toList();
            CompletableFuture<Map<String, ClusterReindexing>> combinedRequest = CompletableFutures.firstOf(parallelRequests);
            futureStatusPerCluster.put(clusterId, combinedRequest);
        });

        try {
            Map<String, ClusterReindexing> statusPerCluster = new HashMap<>();
            futureStatusPerCluster.forEach((clusterId, futureStatus) -> {
                statusPerCluster.putAll(futureStatus.join());
            });
            return Map.copyOf(statusPerCluster);
        } catch (Exception e) {
            throw new IOException("Failed to get reindexing status from cluster controllers: " + e.getMessage(), e);
        }
    }

    @Override public void close() { uncheck(() -> httpClient.close()); }

    private CompletableFuture<Map<String, ClusterReindexing>> getReindexingStatus(ServiceInfo service) {
        URI uri = URI.create(String.format("http://%s:%d/reindexing/v1/status", service.getHostName(), getStatePort(service)));
        CompletableFuture<SimpleHttpResponse> responsePromise = new CompletableFuture<>();
        httpClient.execute(SimpleRequestBuilder.get(uri).build(), new FutureCallback<>() {
            @Override public void completed(SimpleHttpResponse result) { responsePromise.complete(result); }
            @Override public void failed(Exception ex) { responsePromise.completeExceptionally(ex); }
            @Override public void cancelled() { responsePromise.cancel(false); }
        });
        return responsePromise.handleAsync((response, error) ->  {
            if (response != null) {
                return uncheck(() -> toClusterReindexing(response));
            } else {
                throw throwUnchecked(new IOException(String.format("For '%s': %s", uri, error.getMessage()), error));
            }
        }, executor);
    }

    private static Map<String, ClusterReindexing> toClusterReindexing(SimpleHttpResponse response) throws IOException {
        if (response.getCode() != HttpStatus.SC_OK) throw new IOException("Expected status code 200, got " + response.getCode());
        if (response.getBody() == null) throw new IOException("Response has no content");
        return toClusterReindexing(response.getBodyBytes());
    }

    private static Map<String, ClusterReindexing> toClusterReindexing(byte[] requestBody) throws IOException {
        JsonNode jsonNode = mapper.readTree(requestBody);
        Map<String, ClusterReindexing> clusters = new HashMap<>();
        for (var clusterNames = jsonNode.get("clusters").fieldNames(); clusterNames.hasNext(); ) {
            String clusterName = clusterNames.next();
            JsonNode clusterJson = jsonNode.get("clusters").get(clusterName);
            Map<String, ClusterReindexing.Status> documentStatuses = new HashMap<>();
            for (var documentTypes = clusterJson.get("documentTypes").fieldNames(); documentTypes.hasNext(); ) {
                String type = documentTypes.next();
                JsonNode statusJson = clusterJson.get("documentTypes").get(type);
                Instant startedMillis = Instant.ofEpochMilli(statusJson.get("startedMillis").longValue());
                Instant endedMillis = Optional.ofNullable(statusJson.get("endedMillis"))
                                              .map(json -> Instant.ofEpochMilli(json.longValue()))
                                              .orElse(null);
                Double progress = Optional.ofNullable(statusJson.get("progress"))
                                          .map(JsonNode::doubleValue)
                                          .orElse(null);
                ClusterReindexing.State state = Optional.ofNullable(statusJson.get("state"))
                                                        .map(json -> ClusterReindexing.State.fromString(json.textValue()))
                                                        .orElse(null);
                String message = Optional.ofNullable(statusJson.get("message"))
                                         .map(JsonNode::textValue)
                                         .orElse(null);
                documentStatuses.put(type, new ClusterReindexing.Status(startedMillis, endedMillis, state, message, progress));
            }
            clusters.put(clusterName, new ClusterReindexing(documentStatuses));
        }
        return Map.copyOf(clusters);
    }

    private static int getStatePort(ServiceInfo service) {
        return service.getPorts().stream()
                .filter(port -> port.getTags().contains("state"))
                .map(PortInfo::getPort)
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Cluster controller container has no container port"));
    }

    private static Map<ClusterId, List<ServiceInfo>> clusterControllerClusters(ModelResult application) {
        return application.getModel().getHosts().stream()
                .flatMap(host -> host.getServices().stream())
                .filter(service -> service.getServiceType().equals(CLUSTERCONTROLLER_CONTAINER.serviceName))
                .collect(Collectors.groupingBy(service -> new ClusterId(service.getProperty("clustername").get())));

    }

    @SuppressWarnings("deprecation")
    private static CloseableHttpAsyncClient createHttpClient() {
        return VespaAsyncHttpClientBuilder
                .create()
                .setIOReactorConfig(IOReactorConfig.custom()
                        .setSoTimeout(Timeout.ofSeconds(2))
                        .build())
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(2))
                                .setConnectionRequestTimeout(Timeout.ofSeconds(2))
                                .setResponseTimeout(Timeout.ofSeconds(4))
                                .build())
                .setUserAgent("cluster-controller-reindexing-client")
                .build();

    }

}
