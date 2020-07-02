package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.MetricsResponse;
import java.net.URI;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ApplicationMetricsV2Retriever {

    private final ClusterMetricsRetriever metricsRetriever;

    public ApplicationMetricsV2Retriever() {
        this(new ClusterMetricsRetriever());
    }

    public ApplicationMetricsV2Retriever(ClusterMetricsRetriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    public MetricsResponse getMetrics(Application application) {
        var hosts = getHostsOfApplication(application);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new MetricsResponse(200, application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .map(HostInfo::getHostname)
                .map(ApplicationMetricsV2Retriever::createMetricsProxyURI)
                .collect(Collectors.toList());
    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":4080/metrics/v2/values");
    }
}
