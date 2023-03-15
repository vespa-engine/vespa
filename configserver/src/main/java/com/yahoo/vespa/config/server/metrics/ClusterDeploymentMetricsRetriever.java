// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.metrics.ClusterControllerMetrics;
import com.yahoo.metrics.ContainerMetrics;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.yolean.Exceptions;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Client for reaching out to nodes in an application instance and get their
 * metrics.
 *
 * @author olaa
 * @author ogronnesby
 */
public class ClusterDeploymentMetricsRetriever {

    private static final Logger log = Logger.getLogger(ClusterDeploymentMetricsRetriever.class.getName());

    private static final String VESPA_CONTAINER = "vespa.container";
    private static final String VESPA_QRSERVER = "vespa.qrserver";
    private static final String VESPA_DISTRIBUTOR = "vespa.distributor";
    private static final String VESPA_CONTAINER_CLUSTERCONTROLLER = "vespa.container-clustercontroller";
    private static final List<String> WANTED_METRIC_SERVICES = List.of(VESPA_CONTAINER, VESPA_QRSERVER, VESPA_DISTRIBUTOR, VESPA_CONTAINER_CLUSTERCONTROLLER);


    private static final ExecutorService executor = Executors.newFixedThreadPool(10, new DaemonThreadFactory("cluster-deployment-metrics-retriever-"));

    private static final CloseableHttpClient httpClient =
            VespaHttpClientBuilder.custom()
                    .connectTimeout(Timeout.ofSeconds(10))
                    .connectionManagerFactory(registry -> new PoolingHttpClientConnectionManager(registry, null, null, TimeValue.ofMinutes(1)))
                    .apacheBuilder()
                    .setDefaultRequestConfig(
                            RequestConfig.custom()
                                         .setConnectionRequestTimeout(Timeout.ofSeconds(60))
                                         .setResponseTimeout(Timeout.ofSeconds(10))
                                         .build())
                    .build();

    /**
     * Call the metrics API on each host and aggregate the metrics
     * into a single value, grouped by cluster.
     */
    public Map<ClusterInfo, DeploymentMetricsAggregator> requestMetricsGroupedByCluster(Collection<URI> hosts) {
        Map<ClusterInfo, DeploymentMetricsAggregator> clusterMetricsMap = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();
        List<Callable<Void>> jobs = hosts.stream()
                .map(hostUri -> (Callable<Void>) () -> {
                    try {
                        getHostMetrics(hostUri, clusterMetricsMap);
                    } catch (Exception e) {
                        log.log(Level.FINE, e, () -> "Failed to download metrics: " + e.getMessage());
                    }
                    return null;
                })
                .toList();
        try {
            executor.invokeAll(jobs, 1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException("Failed to retrieve metrics in time: " + e.getMessage(), e);
        }

        log.log(Level.FINE, () ->
                String.format("Metric retrieval for %d nodes took %d milliseconds", hosts.size(), System.currentTimeMillis() - startTime)
        );

        return clusterMetricsMap;
    }

    private static void getHostMetrics(URI hostURI, Map<ClusterInfo, DeploymentMetricsAggregator> clusterMetricsMap) {
        Slime responseBody = doMetricsRequest(hostURI);
        Cursor error = responseBody.get().field("error_message");

        if (error.valid()) {
            log.info("Failed to retrieve metrics from " + hostURI + ": " + error.asString());
        }

        Inspector services = responseBody.get().field("services");
        services.traverse((ArrayTraverser) (i, servicesInspector) ->
            parseService(servicesInspector, clusterMetricsMap)
        );
    }

    private static void parseService(Inspector service, Map<ClusterInfo, DeploymentMetricsAggregator> clusterMetricsMap) {
        String serviceName = service.field("name").asString();
        if (!WANTED_METRIC_SERVICES.contains(serviceName)) return;
        service.field("metrics").traverse((ArrayTraverser) (i, metric) ->
                addMetricsToAggregator(serviceName, metric, clusterMetricsMap)
        );
    }

    private static void addMetricsToAggregator(String serviceName, Inspector metric, Map<ClusterInfo, DeploymentMetricsAggregator> clusterMetricsMap) {
        Inspector values = metric.field("values");
        ClusterInfo clusterInfo = getClusterInfoFromDimensions(metric.field("dimensions"));
        Supplier<DeploymentMetricsAggregator> aggregator = () -> clusterMetricsMap.computeIfAbsent(clusterInfo, c -> new DeploymentMetricsAggregator());

        switch (serviceName) {
            case VESPA_CONTAINER -> {
                optionalDouble(values.field("query_latency.sum")).ifPresent(qlSum ->
                        aggregator.get().addContainerLatency(qlSum, values.field("query_latency.count").asDouble()));
                optionalDouble(values.field("feed.latency.sum")).ifPresent(flSum ->
                        aggregator.get().addFeedLatency(flSum, values.field("feed.latency.count").asDouble()));
            }
            case VESPA_QRSERVER -> optionalDouble(values.field("query_latency.sum")).ifPresent(qlSum ->
                    aggregator.get().addQrLatency(qlSum, values.field("query_latency.count").asDouble()));
            case VESPA_DISTRIBUTOR -> optionalDouble(values.field("vds.distributor.docsstored.average"))
                    .ifPresent(docCount -> aggregator.get().addDocumentCount(docCount));
            case VESPA_CONTAINER_CLUSTERCONTROLLER ->
                    optionalDouble(values.field(ClusterControllerMetrics.RESOURCE_USAGE_MAX_MEMORY_UTILIZATION.max())).ifPresent(memoryUtil ->
                            aggregator.get()
                                    .addMemoryUsage(memoryUtil, values.field(ClusterControllerMetrics.RESOURCE_USAGE_MEMORY_LIMIT.last()).asDouble())
                                    .addDiskUsage(values.field(ClusterControllerMetrics.RESOURCE_USAGE_MAX_DISK_UTILIZATION.max()).asDouble(),
                                            values.field(ClusterControllerMetrics.RESOURCE_USAGE_DISK_LIMIT.last()).asDouble()));
        }
    }

    private static ClusterInfo getClusterInfoFromDimensions(Inspector dimensions) {
        return new ClusterInfo(dimensions.field("clusterid").asString(), dimensions.field("clustertype").asString());
    }

    @SuppressWarnings("deprecation")
    private static Slime doMetricsRequest(URI hostURI) {
        HttpGet get = new HttpGet(hostURI);
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            byte[] body = EntityUtils.toByteArray(response.getEntity());
            return SlimeUtils.jsonToSlime(body);
        } catch (IOException e) {
            log.info("Was unable to fetch metrics from " + hostURI + " : " + Exceptions.toMessageString(e));
            return new Slime();
        }
    }

    private static OptionalDouble optionalDouble(Inspector field) {
        return field.valid() ? OptionalDouble.of(field.asDouble()) : OptionalDouble.empty();
    }
}
