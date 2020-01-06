/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package com.yahoo.vespa.model.admin.metricsproxy;

import ai.vespa.metricsproxy.http.MetricsHandler;
import ai.vespa.metricsproxy.http.application.MetricsNodesConfig;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author gjoranv
 */
public class MetricsNodesConfigGenerator {

    public static List<MetricsNodesConfig.Node.Builder> generate(List<MetricsProxyContainer> containers) {
        return containers.stream()
                .map(MetricsNodesConfigGenerator::toNodeBuilder)
                .collect(Collectors.toList());
    }

    private static MetricsNodesConfig.Node.Builder toNodeBuilder(MetricsProxyContainer container) {
        return new MetricsNodesConfig.Node.Builder()
                .configId(container.getHost().getConfigId())
                .hostname(container.getHostName())
                .metricsPort(MetricsProxyContainer.BASEPORT)
                .metricsPath(MetricsHandler.VALUES_PATH);
    }

}
