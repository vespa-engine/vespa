// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.response.DeploymentMetricsResponse;

import java.net.URI;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Finds all hosts we want to fetch metrics for, generates the appropriate URIs
 * and returns the generated MetricsResponse.
 *
 * @author olaa
 */
public class DeploymentMetricsRetriever {

    private final ClusterDeploymentMetricsRetriever metricsRetriever;

    public DeploymentMetricsRetriever() {
        this(new ClusterDeploymentMetricsRetriever());
    }

    public DeploymentMetricsRetriever(ClusterDeploymentMetricsRetriever metricsRetriever) {
        this.metricsRetriever = metricsRetriever;
    }

    public DeploymentMetricsResponse getMetrics(Application application) {
        var hosts = getHostsOfApplication(application);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new DeploymentMetricsResponse(application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .map(HostInfo::getHostname)
                .map(DeploymentMetricsRetriever::createMetricsProxyURI)
                .collect(Collectors.toList());

    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":19092/metrics/v1/values?consumer=Vespa");
    }

}
