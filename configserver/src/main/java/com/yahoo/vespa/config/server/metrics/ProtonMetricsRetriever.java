// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.ProtonMetricsResponse;
import java.net.URI;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new ProtonMetricsResponse(application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().anyMatch(isSearchNode()))
                .map(HostInfo::getHostname)
                .map(ProtonMetricsRetriever::createMetricsProxyURI)
                .collect(Collectors.toList());
    }

    private static Predicate<ServiceInfo> isSearchNode() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("searchnode");
    }
    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":19092/metrics/v2/values");
    }
}
