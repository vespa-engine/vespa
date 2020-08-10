package com.yahoo.vespa.config.server.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.yolean.Exceptions;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import static com.yahoo.vespa.config.server.metrics.MetricsSlime.doMetricsRequest;

public class ClusterProtonMetricsRetriever {

    private static final Logger log = Logger.getLogger(ClusterProtonMetricsRetriever.class.getName());

    private static final CloseableHttpClient httpClient = VespaHttpClientBuilder
                                                            .create(PoolingHttpClientConnectionManager::new)
                                                            .setDefaultRequestConfig(
                                                                    RequestConfig.custom()
                                                                            .setConnectTimeout(10 * 1000)
                                                                            .setSocketTimeout(10 * 1000)
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
        Slime hostResponseBody;
        try {
            hostResponseBody = doMetricsRequest(hostURI, httpClient);
        } catch (IOException e) {
            log.info("Was unable to fetch metrics from " + hostURI + " : " + Exceptions.toMessageString(e));
            hostResponseBody = new Slime();
        }
        var parseError = hostResponseBody.get().field("error_message");

        if (parseError.valid()) {
            log.info("Failed to retrieve metrics from " + hostURI + ": " + parseError.asString());
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
}
