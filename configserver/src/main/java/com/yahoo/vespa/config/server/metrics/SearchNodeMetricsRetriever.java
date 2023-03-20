// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.response.SearchNodeMetricsResponse;
import java.net.URI;
import java.util.Collection;
import java.util.function.Predicate;

public class SearchNodeMetricsRetriever {

    private final ClusterSearchNodeMetricsRetriever metricsRetriever;

    public SearchNodeMetricsRetriever() {
        this( new ClusterSearchNodeMetricsRetriever());
    }

    public SearchNodeMetricsRetriever(ClusterSearchNodeMetricsRetriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    public SearchNodeMetricsResponse getMetrics(Application application) {
        var hosts = getHostsOfApplication(application);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new SearchNodeMetricsResponse(application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().anyMatch(isSearchNode()))
                .map(HostInfo::getHostname)
                .map(SearchNodeMetricsRetriever::createMetricsProxyURI)
                .toList();
    }

    private static Predicate<ServiceInfo> isSearchNode() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("searchnode");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":19092/metrics/v2/values");
    }

}
