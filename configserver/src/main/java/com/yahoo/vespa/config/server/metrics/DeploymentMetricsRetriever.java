// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.NodeSuspensionProvider;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.vespa.config.server.http.v2.response.DeploymentMetricsResponse;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
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
    private final FlagSource flagSource;

    public DeploymentMetricsRetriever(ClusterDeploymentMetricsRetriever metricsRetriever,
                                      NodeSuspensionProvider nodeSuspensionProvider,
                                      FlagSource flagSource) {
        this.metricsRetriever = metricsRetriever;
        this.nodeSuspensionProvider = nodeSuspensionProvider;
        this.flagSource = flagSource;
    }

    public DeploymentMetricsResponse getMetrics(Application application) {
        var suspendedHostnames = nodeSuspensionProvider.suspendedHosts(application.getId());
        String consumer = Flags.DEPLOYMENT_METRICS_CONSUMER.bindTo(flagSource)
                .with(application.getId())
                .value();
        var hosts = getHostsOfApplication(application, suspendedHostnames, consumer);
        var clusterMetrics = metricsRetriever.requestMetricsGroupedByCluster(hosts);
        return new DeploymentMetricsResponse(application.getId(), clusterMetrics);
    }

    private static Collection<URI> getHostsOfApplication(Application application, Set<String> suspendedHostnames, String consumer) {
        return application.getModel().getHosts().stream()
                .filter(host -> host.getServices().stream().noneMatch(isLogserver()))
                .filter(host -> !suspendedHostnames.contains(host.getHostname()))
                .map(HostInfo::getHostname)
                .map(hostname -> createMetricsProxyURI(hostname, consumer))
                .toList();
    }

    private static Predicate<ServiceInfo> isLogserver() {
        return serviceInfo -> serviceInfo.getServiceType().equalsIgnoreCase("logserver");
    }

    private static URI createMetricsProxyURI(String hostname, String consumer) {
        return URI.create("http://" + hostname + ":19092/metrics/v1/values?consumer=" + consumer);
    }

}
