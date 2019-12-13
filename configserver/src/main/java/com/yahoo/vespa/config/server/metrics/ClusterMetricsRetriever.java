// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.log.LogLevel;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.yolean.Exceptions;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


/**
 * Client for reaching out to nodes in an application instance and get their
 * metrics.
 *
 * @author olaa
 * @author ogronnesby
 */
public class ClusterMetricsRetriever {

    private static final Logger log = Logger.getLogger(ClusterMetricsRetriever.class.getName());

    private static final String VESPA_CONTAINER = "vespa.container";
    private static final String VESPA_QRSERVER = "vespa.qrserver";
    private static final String VESPA_DISTRIBUTOR = "vespa.distributor";
    private static final List<String> WANTED_METRIC_SERVICES = List.of(VESPA_CONTAINER, VESPA_QRSERVER, VESPA_DISTRIBUTOR);


    private static final CloseableHttpClient httpClient = VespaHttpClientBuilder.create()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(10 * 1000)
                    .setSocketTimeout(10 * 1000)
                    .build())
            .build();

    /**
     * Call the metrics API on each host and aggregate the metrics
     * into a single value, grouped by cluster.
     */
    public Map<ClusterInfo, MetricsAggregator> requestMetricsGroupedByCluster(Collection<URI> hosts) {
        Map<ClusterInfo, MetricsAggregator> clusterMetricsMap = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();
        Runnable retrieveMetricsJob = () ->
                hosts.parallelStream().forEach(host ->
                    getHostMetrics(host, clusterMetricsMap)
                );

        ForkJoinPool threadPool = new ForkJoinPool(5);
        threadPool.submit(retrieveMetricsJob);
        threadPool.shutdown();

        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.log(LogLevel.DEBUG, () ->
                String.format("Metric retrieval for %d nodes took %d milliseconds", hosts.size(), System.currentTimeMillis() - startTime)
        );

        return clusterMetricsMap;
    }

    private static void getHostMetrics(URI hostURI, Map<ClusterInfo, MetricsAggregator> clusterMetricsMap) {
            Slime responseBody = doMetricsRequest(hostURI);
            var parseError = responseBody.get().field("error_message");

            if (parseError.valid()) {
                log.info("Failed to retrieve metrics from " + hostURI + ": " + parseError.asString());
            }

            Inspector services = responseBody.get().field("services");
            services.traverse((ArrayTraverser) (i, servicesInspector) ->
                parseService(servicesInspector, clusterMetricsMap)
            );
    }

    private static Slime doMetricsRequest(URI hostURI) {
        HttpGet get = new HttpGet(hostURI);
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            InputStream is = response.getEntity().getContent();
            Slime slime = SlimeUtils.jsonToSlime(is.readAllBytes());
            is.close();
            return slime;
        } catch (IOException e) {
            // Usually caused by applications being deleted during metric retrieval
            log.warning("Was unable to fetch metrics from " + hostURI + " : " + Exceptions.toMessageString(e));
            return new Slime();
        }
    }

    private static void parseService(Inspector service, Map<ClusterInfo, MetricsAggregator> clusterMetricsMap) {
        String serviceName = service.field("name").asString();
        service.field("metrics").traverse((ArrayTraverser) (i, metric) ->
                addMetricsToAggeregator(serviceName, metric, clusterMetricsMap)
        );
    }

    private static void addMetricsToAggeregator(String serviceName, Inspector metric, Map<ClusterInfo, MetricsAggregator> clusterMetricsMap) {
        if (!WANTED_METRIC_SERVICES.contains(serviceName)) return;
        Inspector values = metric.field("values");
        ClusterInfo clusterInfo = getClusterInfoFromDimensions(metric.field("dimensions"));
        MetricsAggregator metricsAggregator = clusterMetricsMap.computeIfAbsent(clusterInfo, c -> new MetricsAggregator());

        switch (serviceName) {
            case "vespa.container":
                metricsAggregator.addContainerLatency(
                        values.field("query_latency.sum").asDouble(),
                        values.field("query_latency.count").asDouble());
                metricsAggregator.addFeedLatency(
                        values.field("feed.latency.sum").asDouble(),
                        values.field("feed.latency.count").asDouble());
                break;
            case "vespa.qrserver":
                metricsAggregator.addQrLatency(
                        values.field("query_latency.sum").asDouble(),
                        values.field("query_latency.count").asDouble());
                break;
            case "vespa.distributor":
                metricsAggregator.addDocumentCount(values.field("vds.distributor.docsstored.average").asDouble());
                break;
        }
    }

    private static ClusterInfo getClusterInfoFromDimensions(Inspector dimensions) {
        return new ClusterInfo(dimensions.field("clusterid").asString(), dimensions.field("clustertype").asString());
    }
}
