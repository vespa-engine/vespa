package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.container.handler.metrics.JsonResponse;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.ProtonMetricsResponse;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ProtonMetricsRetriever {

    private final ClusterProtonMetricsRetriever metricsRetriever;
    public ProtonMetricsRetriever() {
        this( new ClusterProtonMetricsRetriever());
    }

    public ProtonMetricsRetriever(ClusterProtonMetricsRetriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    public ProtonMetricsResponse getMetrics(Application application) {
        var hosts = getHostsOfApplication(application);
        var clusterMetrics = metricsRetriever.requestMetrics(hosts);
        return new ProtonMetricsResponse(200, application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .map(HostInfo::getHostname)
                .map(ProtonMetricsRetriever::createMetricsProxyURI)
                .collect(Collectors.toList());
    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":4080/metrics/v2/values");
    }
}
