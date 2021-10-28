// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.metrics;


import java.util.Objects;

/**
 * @author olaa
 */
public class ClusterInfo {

    private final String clusterId;
    private final String clusterType;

    public ClusterInfo(String clusterId, String clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
    }

    public String getClusterId() {
        return clusterId;
    }

    public String getClusterType() {
        return clusterType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clusterId, clusterType);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClusterInfo)) return false;
        ClusterInfo other = (ClusterInfo) o;
        return clusterId.equals(other.clusterId) && clusterType.equals(other.clusterType);
    }

}
