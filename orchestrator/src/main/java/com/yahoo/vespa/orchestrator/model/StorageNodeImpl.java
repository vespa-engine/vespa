// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerStateResponse;
import com.yahoo.vespa.orchestrator.policy.HostStateChangeDeniedException;
import com.yahoo.vespa.orchestrator.policy.HostedVespaPolicy;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class StorageNodeImpl implements StorageNode {
    private static final Logger logger = Logger.getLogger(StorageNodeImpl.class.getName());

    private final ApplicationInstance applicationInstance;
    private final ClusterId clusterId;
    private final ServiceInstance storageService;
    private final ClusterControllerClientFactory clusterControllerClientFactory;

    StorageNodeImpl(ApplicationInstance applicationInstance,
                    ClusterId clusterId,
                    ServiceInstance storageService,
                    ClusterControllerClientFactory clusterControllerClientFactory) {
        this.applicationInstance = applicationInstance;
        this.clusterId = clusterId;
        this.storageService = storageService;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
    }

    @Override
    public HostName hostName() {
        return storageService.hostName();
    }

    @Override
    public void setNodeState(OrchestratorContext context, ClusterControllerNodeState wantedNodeState)
            throws HostStateChangeDeniedException {
        // The "cluster name" used by the Cluster Controller IS the cluster ID.
        String clusterId = this.clusterId.s();

        List<HostName> clusterControllers = VespaModelUtil.getClusterControllerInstancesInOrder(applicationInstance, this.clusterId);

        ClusterControllerClient client = clusterControllerClientFactory.createClient(
                clusterControllers,
                clusterId);

        ConfigId configId = storageService.configId();
        int nodeIndex = VespaModelUtil.getStorageNodeIndex(configId);

        logger.log(LogLevel.DEBUG, () -> "Setting cluster controller state for " +
                "application " + applicationInstance.reference().asString() +
                ", host " + hostName() +
                ", cluster name " + clusterId +
                ", node index " + nodeIndex +
                ", node state " + wantedNodeState);

        ClusterControllerStateResponse response;
        try {
            response = client.setNodeState(context, nodeIndex, wantedNodeState);
        } catch (IOException e) {
            throw new HostStateChangeDeniedException(
                    hostName(),
                    HostedVespaPolicy.CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT,
                    "Failed to communicate with cluster controllers " + clusterControllers + ": " + e,
                    e);
        } catch (UncheckedTimeoutException e) {
            throw new HostStateChangeDeniedException(
                    hostName(),
                    HostedVespaPolicy.DEADLINE_CONSTRAINT,
                    "Timeout while waiting for setNodeState(" + nodeIndex + ", " + wantedNodeState +
                            ") against " + clusterControllers + ": " + e.getMessage(),
                    e);
        }

        if ( ! response.wasModified) {
            throw new HostStateChangeDeniedException(
                    hostName(),
                    HostedVespaPolicy.SET_NODE_STATE_CONSTRAINT,
                    "Failed to set state to " + wantedNodeState + " in cluster controller: " + response.reason);
        }
    }

    @Override
    public String toString() {
        return "StorageNodeImpl{" +
                "applicationInstance=" + applicationInstance.reference() +
                ", clusterId=" + clusterId +
                ", storageService=" + storageService +
                '}';
    }

    /** Only base it on the service instance, e.g. the cluster ID is included in its equals(). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageNodeImpl)) return false;
        StorageNodeImpl that = (StorageNodeImpl) o;
        return Objects.equals(storageService, that.storageService);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageService);
    }

    @Override
    public int compareTo(StorageNode otherStorageNode) {
        if (!(otherStorageNode instanceof StorageNodeImpl)) {
            throw new IllegalArgumentException("Unable to compare our class to any StorageNode object");
        }
        StorageNodeImpl that = (StorageNodeImpl) otherStorageNode;

        // We're guaranteed there's only one storage service per node.
        return this.storageService.hostName().compareTo(that.storageService.hostName());
    }
}
