/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

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

    final URI metricsUri;

    public Node(VespaNodesConfig.Node nodeConfig) {
        this(nodeConfig.configId(), nodeConfig.hostname(), nodeConfig.port() ,nodeConfig.path());
    }

    public Node(String configId, String host, int port, String path) {
        this.configId = configId;
        this.host = host;
        this.port = port;
        this.path = path;
        metricsUri = getMetricsUri(host, port, path);
    }

    private static URI getMetricsUri(String host, int port, String path) {
        return URI.create("http://" + host + ":" + port + path);
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
