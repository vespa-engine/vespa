// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import ai.vespa.util.http.hc5.VespaHttpClientBuilder;
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
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ClusterProtonMetricsRetriever {

    private static final Logger log = Logger.getLogger(ClusterProtonMetricsRetriever.class.getName());

    private static final CloseableHttpClient httpClient = VespaHttpClientBuilder
                                                            .create(PoolingHttpClientConnectionManager::new)
                                                            .setDefaultRequestConfig(
                                                                    RequestConfig.custom()
                                                                                 .setConnectTimeout(Timeout.ofSeconds(10))
                                                                                 .setResponseTimeout(Timeout.ofSeconds(10))
                                                                                 .build())
                                                            .build();


    public Map<String, ProtonMetricsAggregator> requestMetricsGroupedByCluster(Collection<URI> hosts) {
        Map<String, ProtonMetricsAggregator> clusterMetricsMap = new ConcurrentHashMap<>();
        for (URI uri : hosts) {
            addMetricsFromHost(uri, clusterMetricsMap);
        }
/*        long startTime = System.currentTimeMillis();
        Runnable retrieveMetricsJob = () ->
                hosts.parallelStream().forEach(host ->
                        addMetricsFromHost(host, clusterMetricsMap)
                );

        ForkJoinPool threadPool = new ForkJoinPool(10);
        threadPool.submit(retrieveMetricsJob);
        threadPool.shutdown();

        try {
            threadPool.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.log(Level.FINE, () ->
                String.format("Proton metric retrieval for %d nodes took %d milliseconds", hosts.size(), System.currentTimeMillis() - startTime)
        );*/

        return clusterMetricsMap;
    }

    private static void addMetricsFromHost(URI hostURI, Map<String, ProtonMetricsAggregator> clusterMetricsMap) {
        Slime hostResponseBody = doMetricsRequest(hostURI);
        Cursor error = hostResponseBody.get().field("error_message");

        if (error.valid()) {
            log.info("Failed to retrieve metrics from " + hostURI + ": " + error.asString());
        }

        Inspector nodes = hostResponseBody.get().field("nodes");
        nodes.traverse((ArrayTraverser) (i, nodesInspector) ->
                parseNode(nodesInspector, clusterMetricsMap)
        );
    }

    private static void parseNode(Inspector node, Map<String, ProtonMetricsAggregator> clusterMetricsMap) {
        String nodeRole = node.field("role").asString();
        if(nodeRole.contains("content")) {
            ProtonMetricsAggregator aggregator = new ProtonMetricsAggregator();
            clusterMetricsMap.put(nodeRole, aggregator);
            node.field("services").traverse((ArrayTraverser) (i, servicesInspector) ->
                    addServicesToAggregator(servicesInspector, aggregator)
            );
        }
    }

    private static void addServicesToAggregator(Inspector services, ProtonMetricsAggregator aggregator) {
        services.field("metrics").traverse((ArrayTraverser) (i, metricsInspector) ->
                addMetricsToAggregator(metricsInspector, aggregator)
        );
    }

    private static void addMetricsToAggregator(Inspector metrics, ProtonMetricsAggregator aggregator) {
        aggregator.addAll(metrics.field("values"));
    }

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
}
