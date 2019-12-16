/*
 * Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 */

package ai.vespa.metricsproxy.http.application;

import java.net.URI;

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

}
