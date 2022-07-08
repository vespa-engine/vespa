// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.orchestrator.OrchestratorContext;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerClientFactory;
import com.yahoo.vespa.orchestrator.controller.ClusterControllerNodeState;
import com.yahoo.vespa.orchestrator.model.ApplicationApi;
import com.yahoo.vespa.orchestrator.model.ApplicationApiFactory;
import com.yahoo.vespa.orchestrator.model.ClusterApi;
import com.yahoo.vespa.orchestrator.model.NodeGroup;
import com.yahoo.vespa.orchestrator.model.StorageNode;
import com.yahoo.vespa.orchestrator.status.ApplicationInstanceStatus;
import com.yahoo.vespa.orchestrator.status.HostStatus;
import com.yahoo.vespa.orchestrator.status.ApplicationLock;

/**
 * @author oyving
 */
public class HostedVespaPolicy implements Policy {

    public static final String APPLICATION_SUSPENDED_CONSTRAINT = "application-suspended";
    public static final String ENOUGH_SERVICES_UP_CONSTRAINT = "enough-services-up";
    public static final String UNKNOWN_SERVICE_STATUS = "unknown-service-status";
    public static final String SET_NODE_STATE_CONSTRAINT = "controller-set-node-state";
    public static final String CLUSTER_CONTROLLER_AVAILABLE_CONSTRAINT = "controller-available";
    public static final String DEADLINE_CONSTRAINT = "deadline";

    private final HostedVespaClusterPolicy clusterPolicy;
    private final ClusterControllerClientFactory clusterControllerClientFactory;
    private final ApplicationApiFactory applicationApiFactory;
    private final BooleanFlag keepStorageNodeUpFlag;

    public HostedVespaPolicy(HostedVespaClusterPolicy clusterPolicy,
                             ClusterControllerClientFactory clusterControllerClientFactory,
                             ApplicationApiFactory applicationApiFactory,
                             FlagSource flagSource) {
        this.clusterPolicy = clusterPolicy;
        this.clusterControllerClientFactory = clusterControllerClientFactory;
        this.applicationApiFactory = applicationApiFactory;
        this.keepStorageNodeUpFlag = Flags.KEEP_STORAGE_NODE_UP.bindTo(flagSource);
    }

    @Override
    public SuspensionReasons grantSuspensionRequest(OrchestratorContext context, ApplicationApi application)
            throws HostStateChangeDeniedException {
        var suspensionReasons = new SuspensionReasons();

        // Apply per-cluster policy
        for (ClusterApi cluster : application.getClusters()) {
            suspensionReasons.mergeWith(clusterPolicy.verifyGroupGoingDownIsFine(cluster));
        }

        // Ask Cluster Controller to set storage nodes in maintenance, unless the node is already allowed
        // to be down (or permanently down) in case they are guaranteed to be in maintenance already.
        for (StorageNode storageNode : application.getNoRemarksStorageNodesInGroupInClusterOrder()) {
            storageNode.setStorageNodeState(context, ClusterControllerNodeState.MAINTENANCE);
        }

        // Ensure all nodes in the group are marked as allowed to be down
        for (HostName hostName : application.getNodesInGroupWithStatus(HostStatus.NO_REMARKS)) {
            application.setHostState(context, hostName, HostStatus.ALLOWED_TO_BE_DOWN);
        }

        return suspensionReasons;
    }

    @Override
    public void releaseSuspensionGrant(OrchestratorContext context, ApplicationApi application)
            throws HostStateChangeDeniedException {
        // Always defer to Cluster Controller whether it's OK to resume storage node
        for (StorageNode storageNode : application.getSuspendedStorageNodesInGroupInReverseClusterOrder()) {
            storageNode.setStorageNodeState(context, ClusterControllerNodeState.UP);
        }

        // In particular, we're not modifying the state of PERMANENTLY_DOWN nodes.
        for (HostName hostName : application.getNodesInGroupWithStatus(HostStatus.ALLOWED_TO_BE_DOWN)) {
            application.setHostState(context, hostName, HostStatus.NO_REMARKS);
        }
    }

    @Override
    public void acquirePermissionToRemove(OrchestratorContext context, ApplicationApi applicationApi)
            throws HostStateChangeDeniedException {
        ApplicationInstanceStatus applicationStatus = applicationApi.getApplicationStatus();
        if (applicationStatus == ApplicationInstanceStatus.ALLOWED_TO_BE_DOWN) {
            throw new HostStateChangeDeniedException(
                    applicationApi.getNodeGroup(),
                    HostedVespaPolicy.APPLICATION_SUSPENDED_CONSTRAINT,
                    "Unable to test availability constraints as the application " +
                            applicationApi.applicationId() + " is allowed to be down");
        }

        // Apply per-cluster policy
        for (ClusterApi cluster : applicationApi.getClusters()) {
            clusterPolicy.verifyGroupGoingDownPermanentlyIsFine(cluster);
        }

        boolean keepStorageNodeUp = keepStorageNodeUpFlag
                .with(FetchVector.Dimension.APPLICATION_ID, applicationApi.applicationId().serializedForm())
                .value();

        // Get permission from the Cluster Controller to remove the content nodes.
        for (StorageNode storageNode : applicationApi.getStorageNodesInGroupInClusterOrder()) {
            if (keepStorageNodeUp) {
                storageNode.setStorageNodeState(context.createSubcontextForSingleAppOp(true), ClusterControllerNodeState.DOWN);
                storageNode.forceDistributorState(context, ClusterControllerNodeState.DOWN);
            } else {
                storageNode.setStorageNodeState(context, ClusterControllerNodeState.DOWN);
            }
        }

        // Ensure all nodes in the group are marked as permanently down
        for (HostName hostName : applicationApi.getNodesInGroupWith(status -> status != HostStatus.PERMANENTLY_DOWN)) {
            applicationApi.setHostState(context, hostName, HostStatus.PERMANENTLY_DOWN);
        }
    }

    // TODO: Remove later - currently used for backward compatibility testing
    @Override
    public void releaseSuspensionGrant(
            OrchestratorContext context,
            ApplicationInstance applicationInstance,
            HostName hostName,
            ApplicationLock lock) throws HostStateChangeDeniedException {
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostName);
        ApplicationApi applicationApi = applicationApiFactory.create(nodeGroup, lock, clusterControllerClientFactory);
        releaseSuspensionGrant(context, applicationApi);
    }

}
