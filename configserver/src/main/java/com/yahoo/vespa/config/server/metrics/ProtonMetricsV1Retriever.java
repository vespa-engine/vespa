package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.vespa.config.server.application.Application;
import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ProtonMetricsV1Retriever {

    private final ClusterProtonMetricsV1Retriever metricsRetriever;
    public ProtonMetricsV1Retriever() {
        this( new ClusterProtonMetricsV1Retriever());
    }

    public ProtonMetricsV1Retriever(ClusterProtonMetricsV1Retriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    public JsonResponse getMetrics(Application application) {
        var hosts = getHostsOfApplication(application);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        JSONObject jsonMetrics;
        try {
            jsonMetrics = buildJSONObject(clusterMetrics);
        } catch (JSONException e) {
            jsonMetrics = new JSONObject();
        }
        return new JsonResponse(200, jsonMetrics.toString());
    }

    public JSONObject buildJSONObject(Map<ClusterInfo, JSONObject> clusterMetrics) throws JSONException {
        JSONObject response = new JSONObject();
        response.put("name", "proton.metrics.aggregated");
        JSONArray metrics = new JSONArray();

        for (Map.Entry<ClusterInfo, JSONObject> entry : clusterMetrics.entrySet()) {
            JSONObject jsonEntry = new JSONObject();
            jsonEntry.put("cluster.id", entry.getKey().getClusterId());
            jsonEntry.put("cluster.type", entry.getKey().getClusterType());
            jsonEntry.put("metrics", entry.getValue());
            metrics.put(jsonEntry);
        }
        response.put("metrics", metrics);
        return response;
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .map(HostInfo::getHostname)
                .map(ProtonMetricsV1Retriever::createMetricsProxyURI)
                .collect(Collectors.toList());
    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":4080/metrics/v2/values");
    }
}
