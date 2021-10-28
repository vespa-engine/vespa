// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    public final String role;
    public final String hostname;
    private final int port;
    private final String path;

    private final String metricsUriBase;

    public Node(MetricsNodesConfig.Node nodeConfig) {
        this(nodeConfig.role(), nodeConfig.hostname(), nodeConfig.metricsPort() , nodeConfig.metricsPath());
    }

    public Node(String role, String hostname, int port, String path) {
        Objects.requireNonNull(role, "Null role is not allowed");
        Objects.requireNonNull(hostname, "Null hostname is not allowed");
        Objects.requireNonNull(path, "Null path is not allowed");

        this.role = role;
        this.hostname = hostname;
        this.port = port;
        this.path = path;
        metricsUriBase = "http://" + hostname + ":" + port + path;
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
                path.equals(node.path) &&
                role.equals(node.role) &&
                hostname.equals(node.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, hostname, port, path);
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(role).append(":").append(metricsUriBase);
        return sb.toString();
    }
}
