// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.collections.Pair;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DummyCommunicator implements Communicator, NodeLookup {

    List<Node> newNodes;
    private final Timer timer;
    private boolean shouldDeferDistributorClusterStateAcks = false;
    private final List<Pair<Waiter<SetClusterStateRequest>, DummySetClusterStateRequest>> deferredClusterStateAcks = new ArrayList<>();

    void setShouldDeferDistributorClusterStateAcks(boolean shouldDeferDistributorClusterStateAcks) {
        this.shouldDeferDistributorClusterStateAcks = shouldDeferDistributorClusterStateAcks;
    }

    static class DummyGetNodeStateRequest extends GetNodeStateRequest {
        Waiter<GetNodeStateRequest> waiter;

        DummyGetNodeStateRequest(NodeInfo nodeInfo, Waiter<GetNodeStateRequest> waiter) {
            super(nodeInfo);

            this.waiter = waiter;
        }

        @Override
        public void abort() {

        }
    }

    public static class DummySetClusterStateRequest extends SetClusterStateRequest {

        DummySetClusterStateRequest(NodeInfo nodeInfo, ClusterState state) {
            super(nodeInfo, state.getVersion());
        }

    }

    public static class DummyActivateClusterStateVersionRequest extends ActivateClusterStateVersionRequest {

        DummyActivateClusterStateVersionRequest(NodeInfo nodeInfo, int stateVersion) {
            super(nodeInfo, stateVersion);
        }

    }

    private final Map<Node, DummyGetNodeStateRequest> getNodeStateRequests = new TreeMap<>();

    DummyCommunicator(List<Node> nodeList, Timer timer) {
        this.newNodes = nodeList;
        this.timer = timer;
    }

    @Override
    public synchronized void getNodeState(NodeInfo node, Waiter<GetNodeStateRequest> waiter) {
        DummyGetNodeStateRequest req = new DummyGetNodeStateRequest(node, waiter);
        getNodeStateRequests.put(node.getNode(), req);
        node.setCurrentNodeStateRequest(req, timer.getCurrentTimeInMillis());
        notifyAll();
    }

    public void propagateOptions(final FleetControllerOptions options) {

    }

    public boolean setNodeState(Node node, State state, String description) {
        return setNodeState(node, new NodeState(node.getType(), state).setDescription(description), "");
    }

    public boolean setNodeState(Node node, NodeState state, String hostInfo) {
        DummyGetNodeStateRequest req = getNodeStateRequests.remove(node);

        if (req == null) {
            throw new IllegalStateException("Premature set node state - wait for fleet controller to request first: " + node);
        }

        GetNodeStateRequest.Reply reply = new GetNodeStateRequest.Reply(state.serialize(), hostInfo);
        req.setReply(reply);

        req.waiter.done(req);

        return true;
    }

    @Override
    public void setSystemState(ClusterStateBundle stateBundle, NodeInfo node, Waiter<SetClusterStateRequest> waiter) {
        ClusterState baselineState = stateBundle.getBaselineClusterState();
        DummySetClusterStateRequest req = new DummySetClusterStateRequest(node, baselineState);
        node.setClusterStateVersionBundleSent(stateBundle);
        req.setReply(new SetClusterStateRequest.Reply());
        if (node.isStorage() || !shouldDeferDistributorClusterStateAcks) {
            waiter.done(req);
        } else {
            deferredClusterStateAcks.add(new Pair<>(waiter, req));
        }
    }

    @Override
    public void activateClusterStateVersion(int clusterStateVersion, NodeInfo node, Waiter<ActivateClusterStateVersionRequest> waiter) {
        var req = new DummyActivateClusterStateVersionRequest(node, clusterStateVersion);
        req.setReply(ActivateClusterStateVersionRequest.Reply.withActualVersion(clusterStateVersion));
        waiter.done(req);
    }

    void sendAllDeferredDistributorClusterStateAcks() {
        deferredClusterStateAcks.forEach(reqAndWaiter -> reqAndWaiter.getFirst().done(reqAndWaiter.getSecond()));
        deferredClusterStateAcks.clear();
    }

    void sendPartialDeferredDistributorClusterStateAcks() {
        if (deferredClusterStateAcks.isEmpty()) {
            throw new IllegalStateException("Tried to send partial ACKs with no ACKs deferred");
        }
        List<Pair<Waiter<SetClusterStateRequest>, DummySetClusterStateRequest>> toAck =
                deferredClusterStateAcks.subList(0, deferredClusterStateAcks.size() - 1);
        toAck.forEach(reqAndWaiter -> reqAndWaiter.getFirst().done(reqAndWaiter.getSecond()));
        deferredClusterStateAcks.removeAll(toAck);
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean updateCluster(ContentCluster cluster, SlobrokListener listener) {
        if (newNodes != null) {
            List<Node> tmp = newNodes;

            for (Node node : tmp)
                cluster.clusterInfo().setRpcAddress(node, "foo");

            for (NodeInfo info : cluster.getNodeInfos()) {
                if (!tmp.contains(info.getNode())) {
                    info.markRpcAddressOutdated(timer);
                    listener.handleMissingNode(info);
                }
            }

            newNodes = null;
            return true;
        }

        return false;
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
