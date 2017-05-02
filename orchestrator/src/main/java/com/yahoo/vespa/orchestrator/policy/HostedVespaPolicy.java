// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceCluster;
import com.yahoo.vespa.applicationmodel.ServiceInstance;
import com.yahoo.vespa.orchestrator.NodeGroup;
import com.yahoo.vespa.orchestrator.VespaModelUtil;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClient;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerState;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerStateResponse;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.MutableStatusRegistry;
import com.yahoo.vespa.service.monitor.ServiceMonitorStatus;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author oyving
 */

public class HostedVespaPolicy implements Policy {

    public static final String ENOUGH_SERVICES_UP_CONSTRAINT = "enough-services-up";
    public static final String SET_NODE_STATE_CONSTRAINT = "controller-set-node-state";
    public static final String CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT = "controller-available";

    private static final Logger log = Logger.getLogger(HostedVespaPolicy.class.getName());

    private final HostedVespaClusterPolicy clusterPolicy;
    private final ClusterControllerClientFactory clusterControllerClientFactory;

    public HostedVespaPolicy(HostedVespaClusterPolicy clusterPolicy, ClusterControllerClientFactory clusterControllerClientFactory) {
        this.clusterPolicy = clusterPolicy;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
    }

    private static long numContentServiceClusters(Set<? extends ServiceCluster<?>> serviceClustersOnHost) {
        return serviceClustersOnHost.stream().filter(VespaModelUtil::isContent).count();
    }


    @Override
    public void grantSuspensionRequest(ApplicationInstance<ServiceMonitorStatus> applicationInstance,
                                       HostName hostName,
                                       MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance);
        nodeGroup.addNode(hostName);
        ApplicationApi applicationApi = new ApplicationApiImpl(applicationInstance, nodeGroup, hostStatusService);
        grantSuspensionRequest(applicationApi);
    }

    @Override
    public void grantSuspensionRequest(ApplicationApi application)
            throws HostStateChangeDeniedException {
        // Apply per-cluster policy
        for (ClusterApi cluster : application.getClustersThatAreOnAtLeastOneNodeInGroup()) {
            clusterPolicy.verifyGroupGoingDownIsFineForCluster(cluster);
        }

        // Ask Cluster Controller to set UP storage nodes in maintenance.
        for (HostName storageNode : application.getUpStorageNodesInGroupInClusterOrder()) {
            ApplicationInstance<?> applicationInstance = application.getApplicationInstance();
            setNodeStateInController(applicationInstance, storageNode, ClusterControllerState.MAINTENANCE);
            log.log(LogLevel.INFO, "The storage node on " + storageNode + " has been set to MAINTENANCE");
        }

        // Ensure all nodes in the group are marked as allowed to be down
        for (HostName hostName : application.getNodesInGroupNotAllowedToBeDown()) {
            application.setHostState(hostName, HostStatus.ALLOWED_TO_BE_DOWN);
            log.log(LogLevel.INFO, hostName + " is now allowed to be down (suspended)");
        }
    }

    @Override
    public void releaseSuspensionGrant(ApplicationApi application) throws HostStateChangeDeniedException {
        ApplicationInstance<?> applicationInstance = application.getApplicationInstance();

        // Always defer to Cluster Controller whether it's OK to resume storage node
        for (HostName storageNode : application.getStorageNodesWithNoRemarksInGroupInReverseOrder()) {
            setNodeStateInController(applicationInstance, storageNode, ClusterControllerState.UP);
            log.log(LogLevel.INFO, "The storage node on " + storageNode + " has been set to UP");
        }

        for (HostName hostName : application.getNodesInGroupWithNoRemarks()) {
            application.setHostState(hostName, HostStatus.NO_REMARKS);
            log.log(LogLevel.INFO, hostName + " is no longer allowed to be down (resumed)");
        }
    }

    @Override
    public void releaseSuspensionGrant(
            ApplicationInstance<ServiceMonitorStatus> applicationInstance,
            HostName hostName,
            MutableStatusRegistry hostStatusService) throws HostStateChangeDeniedException {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostName);
        ApplicationApi applicationApi = new ApplicationApiImpl(applicationInstance, nodeGroup, hostStatusService);
        releaseSuspensionGrant(applicationApi);
    }

    private void setNodeStateInController(ApplicationInstance<?> application,
                                          HostName hostName,
                                          ClusterControllerState nodeState) throws HostStateChangeDeniedException {
        ClusterId contentClusterId = VespaModelUtil.getContentClusterName(application, hostName);
        Set<? extends ServiceInstance<?>> clusterControllers = VespaModelUtil.getClusterControllerInstances(application, contentClusterId);
        ClusterControllerClient client = clusterControllerClientFactory.createClient(
                clusterControllers,
                contentClusterId.s());
        int nodeIndex = VespaModelUtil.getStorageNodeIndex(application, hostName);

        log.log(LogLevel.DEBUG,
                "application " + application.applicationInstanceId() +
                ", host " + hostName +
                ", cluster name " + contentClusterId +
                ", node index " + nodeIndex +
                ", node state " + nodeState);

        ClusterControllerStateResponse response;
        try {
            response = client.setNodeState(nodeIndex, nodeState);
        } catch (IOException e) {
            throw new HostStateChangeDeniedException(
                    hostName,
                    CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT,
                    VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
                    "Failed to communicate with cluster controllers " + clusterControllers + ": " + e,
                    e);
        }

        if ( ! response.wasModified) {
            throw new HostStateChangeDeniedException(
                    hostName,
                    SET_NODE_STATE_CONSTRAINT,
                    VespaModelUtil.CLUSTER_CONTROLLER_SERVICE_TYPE,
                    "Failed to set state to " + nodeState + " in controller: " + response.reason);
        }
    }

}
