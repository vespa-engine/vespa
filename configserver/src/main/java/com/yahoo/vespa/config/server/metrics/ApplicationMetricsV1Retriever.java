// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.MetricsResponse;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Finds all hosts we want to fetch metrics for, generates the appropriate URIs
 * and returns the generated MetricsResponse.
 *
 * @author olaa
 */
public class ApplicationMetricsV1Retriever {

    private final ClusterMetricsRetriever metricsRetriever;

    private static final String VESPA_CONTAINER = "vespa.container";
    private static final String VESPA_QRSERVER = "vespa.qrserver";
    private static final String VESPA_DISTRIBUTOR = "vespa.distributor";
    private static final List<String> WANTED_METRIC_SERVICES = List.of(VESPA_CONTAINER, VESPA_QRSERVER, VESPA_DISTRIBUTOR);

    public ApplicationMetricsV1Retriever() {
        this(new ClusterMetricsRetriever(WANTED_METRIC_SERVICES));
    }

    public ApplicationMetricsV1Retriever(ClusterMetricsRetriever metricsRetriever) {
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
                .map(ApplicationMetricsV1Retriever::createMetricsProxyURI)
                .collect(Collectors.toList());

    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":19092/metrics/v1/values?consumer=Vespa");
    }

}
