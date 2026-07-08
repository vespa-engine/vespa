// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.NodeSuspensionProvider;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.response.DeploymentMetricsResponse;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Finds all hosts we want to fetch metrics for, generates the appropriate URIs
 * and returns the generated MetricsResponse.
 *
 * @author olaa
 */
public class DeploymentMetricsRetriever {

    private final ClusterDeploymentMetricsRetriever metricsRetriever;
    private final NodeSuspensionProvider nodeSuspensionProvider;

    public DeploymentMetricsRetriever() {
        this(new ClusterDeploymentMetricsRetriever(), NodeSuspensionProvider.EMPTY);
    }

    public DeploymentMetricsRetriever(ClusterDeploymentMetricsRetriever metricsRetriever) {
        this(metricsRetriever, NodeSuspensionProvider.EMPTY);
    }

    public DeploymentMetricsRetriever(ClusterDeploymentMetricsRetriever metricsRetriever,
                                      NodeSuspensionProvider nodeSuspensionProvider) {
        this.metricsRetriever = metricsRetriever;
        this.nodeSuspensionProvider = nodeSuspensionProvider;
    }

    public DeploymentMetricsResponse getMetrics(Application application) {
        var suspendedHostnames = nodeSuspensionProvider.suspendedHosts(application.getId());
        var hosts = getHostsOfApplication(application, suspendedHostnames);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new DeploymentMetricsResponse(application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application, Set<String> suspendedHostnames) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .filter(host -> !suspendedHostnames.contains(host.getHostname()))
                .map(HostInfo::getHostname)
                .map(DeploymentMetricsRetriever::createMetricsProxyURI)
                .toList();
    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname) {
        return URI.create("http://" + hostname + ":19092/metrics/v1/values?consumer=Vespa");
    }

}
