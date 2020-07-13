package com.yahoo.vespa.config.server.metrics;

import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import static com.yahoo.vespa.config.server.metrics.ClusterDeploymentMetricsV1Retriever.doMetricsRequest;
import static com.yahoo.vespa.config.server.metrics.ClusterDeploymentMetricsV1Retriever.getClusterInfoFromDimensions;

public class ClusterProtonMetricsV1Retriever {

    private static final Logger log = Logger.getLogger(ClusterProtonMetricsV1Retriever.class.getName());

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
        Slime hostResponseBody = doMetricsRequest(hostURI);
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
