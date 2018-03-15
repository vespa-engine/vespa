// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.restapiv2;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.hostinfo.HostInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeAddedOrRemovedListener;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeStateOrHostInfoChangeHandler;

public class ClusterControllerMock implements RemoteClusterControllerTaskScheduler {
    public RemoteClusterControllerTask.Context context = new RemoteClusterControllerTask.Context();

    public int fleetControllerIndex;
    public Integer fleetControllerMaster;
    public StringBuilder events = new StringBuilder();

    public ClusterControllerMock(ContentCluster cluster, ClusterState state,
                                 ClusterStateBundle publishedClusterStateBundle,
                                 int fcIndex, Integer fcMaster) {
        this.fleetControllerIndex = fcIndex;
        this.fleetControllerMaster = fcMaster;
        context.cluster = cluster;
        context.currentConsolidatedState = state;
        context.publishedClusterStateBundle = publishedClusterStateBundle;
        context.masterInfo = new MasterInterface() {
            @Override
            public boolean isMaster() {
                return (fleetControllerMaster != null &&
                        fleetControllerMaster == fleetControllerIndex);
            }

            @Override
            public Integer getMaster() {
                return fleetControllerMaster;
            }
        };
        context.nodeStateOrHostInfoChangeHandler = new NodeStateOrHostInfoChangeHandler() {

            @Override
            public void handleNewNodeState(NodeInfo currentInfo, NodeState newState) {
                events.append("newNodeState(").append(currentInfo.getNode()).append(": ").append(newState).append("\n");
            }

            @Override
            public void handleNewWantedNodeState(NodeInfo node, NodeState newState) {
                events.append("newWantedNodeState(").append(node.getNode()).append(": ").append(newState).append("\n");
            }

            @Override
            public void handleUpdatedHostInfo(NodeInfo node, HostInfo newHostInfo) {
                events.append("updatedHostInfo(").append(node.getNode()).append(": ")
                        .append(newHostInfo).append(")\n");
            }

        };
        context.nodeAddedOrRemovedListener = new NodeAddedOrRemovedListener() {

            @Override
            public void handleNewNode(NodeInfo node) {
                events.append("newNode(").append(node.getNode()).append(")\n");
            }

            @Override
            public void handleMissingNode(NodeInfo node) {
                events.append("newMissingNode(").append(node.getNode()).append("\n");
            }

            @Override
            public void handleNewRpcAddress(NodeInfo node) {
                events.append("newRpcAddress(").append(node.getNode()).append("\n");
            }

            @Override
            public void handleReturnedRpcAddress(NodeInfo node) {
                events.append("returnedRpcAddress(").append(node.getNode()).append(")\n");
            }

        };
    }

    @Override
    public void schedule(RemoteClusterControllerTask task) {
        task.doRemoteFleetControllerTask(context);
        task.notifyCompleted();
    }
}
