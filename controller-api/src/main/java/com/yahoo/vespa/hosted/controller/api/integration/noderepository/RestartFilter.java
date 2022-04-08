// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;

import java.util.Optional;

/**
 * Attributes to filter when restarting nodes in a deployment.
 * If all attributes are empty, all nodes are restarted.
 * Used in {@link ConfigServer#restart(DeploymentId, RestartFilter)}
 *
 * @author olaa
 */
public class RestartFilter {

    private Optional<HostName> hostName = Optional.empty();
    private Optional<ClusterSpec.Type> clusterType = Optional.empty();
    private Optional<ClusterSpec.Id> clusterId = Optional.empty();

    public Optional<HostName> getHostName() {
        return hostName;
    }

    public Optional<ClusterSpec.Type> getClusterType() {
        return clusterType;
    }

    public Optional<ClusterSpec.Id> getClusterId() {
        return clusterId;
    }

    public RestartFilter withHostName(Optional<HostName> hostName) {
        this.hostName = hostName;
        return this;
    }

    public RestartFilter withClusterType(Optional<ClusterSpec.Type> clusterType) {
        this.clusterType = clusterType;
        return this;
    }

    public RestartFilter withClusterId(Optional<ClusterSpec.Id> clusterId) {
        this.clusterId = clusterId;
        return this;
    }

}
