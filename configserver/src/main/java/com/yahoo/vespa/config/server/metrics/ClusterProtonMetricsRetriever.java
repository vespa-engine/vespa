package com.yahoo.vespa.config.server.metrics;

import ai.vespa.util.http.VespaHttpClientBuilder;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.yolean.Exceptions;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.json.JSONException;
import org.json.JSONObject;

import static com.yahoo.vespa.config.server.metrics.MetricsSlime.doMetricsRequest;
import static com.yahoo.vespa.config.server.metrics.MetricsSlime.getClusterInfoFromDimensions;

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

    private static final List<String> DESIRED_METRICS = List.of(
            "content.proton.documentdb.matching.docs_matched.rate",
            "content.proton.documentdb.documents.active.last",
            "content.proton.documentdb.documents.ready.last",
            "content.proton.documentdb.documents.total.last",
            "content.proton.documentdb.disk_usage.last",
            "content.proton.resource_usage.disk.average",
            "content.proton.resource_usage.memory.average",
            "content.proton.resource_usage.feeding_blocked.last"
    );

    public Map<ClusterInfo, JSONObject> requestMetricsGroupedByCluster(Collection<URI> hosts) {
        Map<ClusterInfo, JSONObject> clusterMetricsMap = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();
        Runnable retrieveMetricsJob = () ->
                hosts.parallelStream().forEach(host ->
                        populateMetricsMapByHost(host, clusterMetricsMap)
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
        );

        return clusterMetricsMap;
    }

    private static void populateMetricsMapByHost(URI hostURI, Map<ClusterInfo, JSONObject> clusterMetricsMap) {
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


        Inspector services = hostResponseBody.get().field("services");
        JSONObject metrics = new JSONObject();
        for (String namedMetrics : DESIRED_METRICS) {
            try {
                metrics.put(namedMetrics, services.field(namedMetrics).asDouble());
            } catch (JSONException ignored) {
            }
        }

        Inspector dimensions = services.field("metrics").field("values").field("dimensions");
        clusterMetricsMap.put( getClusterInfoFromDimensions(dimensions), metrics);


    }
}
