package com.yahoo.vespa.hosted.controller.api.identifiers;

import com.yahoo.config.provision.ClusterSpec;

import java.util.Objects;

/**
 * DeploymentId x ClusterSpec.Id = ClusterId
 *
 * @author ogronnesby
 */
public class ClusterId {
    private final DeploymentId deploymentId;
    private final ClusterSpec.Id clusterId;

    public ClusterId(DeploymentId deploymentId, ClusterSpec.Id clusterId) {
        this.deploymentId = deploymentId;
        this.clusterId = clusterId;
    }

    public DeploymentId deploymentId() {
        return deploymentId;
    }

    public ClusterSpec.Id clusterId() {
        return clusterId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClusterId clusterId1 = (ClusterId) o;
        return Objects.equals(deploymentId, clusterId1.deploymentId) && Objects.equals(clusterId, clusterId1.clusterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deploymentId, clusterId);
    }

    @Override
    public String toString() {
        return "ClusterId{" +
                "deploymentId=" + deploymentId +
                ", clusterId=" + clusterId +
                '}';
    }
}
