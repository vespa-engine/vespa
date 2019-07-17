// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author olaa
 */
public class ClusterInfo {

    private final String clusterId;
    private final ClusterType clusterType;
    private final List<URI> hostnames;

    public ClusterInfo(String clusterId, ClusterType clusterType) {
        this(clusterId, clusterType, new ArrayList<>());
    }

    public ClusterInfo(String clusterId, ClusterType clusterType, List<URI> hostnames) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.hostnames = hostnames;
    }

    public String getClusterId() {
        return clusterId;
    }

    public ClusterType getClusterType() {
        return clusterType;
    }

    public List<URI> getHostnames() {
        return hostnames;
    }

    public void addHost(URI host) {
        hostnames.add(host);
    }

    public enum ClusterType {content, container, admin};
}
