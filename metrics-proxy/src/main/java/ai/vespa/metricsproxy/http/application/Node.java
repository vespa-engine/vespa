// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.ConsumerId;

import java.net.URI;
import java.util.Objects;

/**
 * Represents a node to retrieve metrics from.
 *
 * @author gjoranv
 */
public class Node {

    final String configId;
    final String host;
    final int port;
    final String path;

    private final String metricsUriBase;

    public Node(MetricsNodesConfig.Node nodeConfig) {
        this(nodeConfig.configId(), nodeConfig.hostname(), nodeConfig.metricsPort() , nodeConfig.metricsPath());
    }

    public Node(String configId, String host, int port, String path) {
        Objects.requireNonNull(configId, "Null configId is not allowed");
        Objects.requireNonNull(host, "Null host is not allowed");
        Objects.requireNonNull(path, "Null path is not allowed");

        this.configId = configId;
        this.host = host;
        this.port = port;
        this.path = path;
        metricsUriBase = "http://" + host + ":" + port + path;
    }

    public String getName() {
        return configId;
    }

    URI metricsUri(ConsumerId consumer) {
        return URI.create(metricsUriBase + "?consumer=" + consumer.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return port == node.port &&
                configId.equals(node.configId) &&
                host.equals(node.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configId, host, port);
    }
}
