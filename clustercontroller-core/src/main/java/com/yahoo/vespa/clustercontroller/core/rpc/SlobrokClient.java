// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.FleetControllerContext;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.NodeLookup;
import com.yahoo.vespa.clustercontroller.core.Timer;
import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SlobrokClient implements NodeLookup {

    public static final Logger log = Logger.getLogger(SlobrokClient.class.getName());

    private final FleetControllerContext context;
    private final Timer timer;
    private String[] connectionSpecs;
    private Mirror mirror;
    private Supervisor supervisor;
    private boolean freshMirror = false;

    public SlobrokClient(FleetControllerContext context, Timer timer, String[] connectionSpecs) {
        this.context = context;
        this.timer = timer;
        this.connectionSpecs = connectionSpecs;
        setup();
    }

    public boolean equalsExistingSpec(String[] spec) {
        if (spec == null && connectionSpecs == null) return true;
        if (spec == null) return false;
        if (connectionSpecs == null) return false;
        if (spec.length != connectionSpecs.length) return false;
        for (int i = 0, n = spec.length; i < n; ++i) {
            if (!spec[i].equals(connectionSpecs[i])) return false;
        }
        return true;
    }

    public void setSlobrokConnectionSpecs(String[] slobrokConnectionSpecs) {
        if (equalsExistingSpec(slobrokConnectionSpecs)) return;

        this.connectionSpecs = slobrokConnectionSpecs;
        shutdown();
        setup();
    }

    private void setup() {
        supervisor = new Supervisor(new Transport("slobrok-client"));
        supervisor.setDropEmptyBuffers(true);
        SlobrokList slist = new SlobrokList();
        slist.setup(connectionSpecs);
        mirror = new Mirror(supervisor, slist);
        freshMirror = true;
    }

    @Override
    public void shutdown() {
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
        }
    }

    public Mirror getMirror() { return mirror; }

    @Override
    public boolean isReady() {
        return mirror != null && mirror.ready();
    }

    @Override
    public boolean updateCluster(ContentCluster cluster, SlobrokListener listener) {
        if (mirror == null) return false;

        int mirrorVersion = mirror.updates();
        if (freshMirror) {
            freshMirror = false;
        } else if (cluster.getSlobrokGenerationCount() == mirrorVersion) {
            context.log(log, Level.FINEST, () -> "Slobrok still at generation count " + cluster.getSlobrokGenerationCount() + ". Not updating.");
            return false;
        }

        cluster.setSlobrokGenerationCount(0); // Set to unused value until we are done processing info.
        Map<Node, SlobrokData> distributorRpc = getSlobrokData(
                "storage/cluster." + cluster.getName() + "/distributor/*");
        Map<Node, SlobrokData> distributorMbus = getSlobrokData(
                "storage/cluster." + cluster.getName() + "/distributor/*/default");
        Map<Node, SlobrokData> storageRpc = getSlobrokData("storage/cluster." + cluster.getName() + "/storage/*");
        Map<Node, SlobrokData> storageMbus = getSlobrokData(
                "storage/cluster." + cluster.getName() + "/storage/*/default");

        Map<Node, SlobrokData> slobrokNodes = new TreeMap<>();
        for (SlobrokData data : distributorRpc.values()) {
            if (distributorMbus.containsKey(data.node)) {
                slobrokNodes.put(data.node, data);
            }
        }
        for (SlobrokData data : storageRpc.values()) {
            if (storageMbus.containsKey(data.node)) {
                slobrokNodes.put(data.node, data);
            }
        }

        List<SlobrokData> newNodes = new LinkedList<>();
        List<NodeInfo> missingNodeInfos = new LinkedList<>();
        List<SlobrokData> alteredRpcAddressNodes = new LinkedList<>();
        List<NodeInfo> returningNodeInfos = new LinkedList<>();
        detectNewAndMissingNodes(
                cluster,
                slobrokNodes,
                newNodes,
                missingNodeInfos,
                alteredRpcAddressNodes,
                returningNodeInfos);
        for (SlobrokData data : newNodes) {
            // XXX we really would like to cross-check the actual RPC address against what's configured,
            // but this information does not seem to be available to the cluster controller currently.
            NodeInfo nodeInfo = cluster.clusterInfo().getNodeInfo(data.node);
            if (nodeInfo == null) continue; // slobrok may contain non-configured nodes during state transitions
            cluster.clusterInfo().setRpcAddress(data.node, data.rpcAddress);
            if (listener != null)
                listener.handleNewNode(nodeInfo); // TODO: We'll never add new nodes here, move this to where clusterInfo.setNodes is called?
        }
        for (NodeInfo nodeInfo : missingNodeInfos) {
            nodeInfo.markRpcAddressOutdated(timer);
            if (listener != null)
                listener.handleMissingNode(nodeInfo);
        }
        for (SlobrokData data : alteredRpcAddressNodes) {
            // TODO: Abort the current node state requests? See NodeInfo.abortCurrentNodeStateRequests()
            NodeInfo nodeInfo = cluster.clusterInfo().setRpcAddress(data.node, data.rpcAddress);
            if (listener != null) {
                listener.handleNewRpcAddress(nodeInfo);  // TODO: We'll never add new nodes here, move this to where clusterInfo.setNodes is called?
            }
        }
        for (NodeInfo nodeInfo : returningNodeInfos) {
            nodeInfo.markRpcAddressLive();
            nodeInfo.abortCurrentNodeStateRequests();
            if (listener != null) {
                listener.handleReturnedRpcAddress(nodeInfo);
            }
        }
        cluster.setSlobrokGenerationCount(mirrorVersion);
        for (NodeInfo nodeInfo : cluster.getNodeInfos()) {
            if (slobrokNodes.containsKey(nodeInfo.getNode()) && nodeInfo.isNotInSlobrok()) {
                context.log(log,
                            Level.WARNING,
                            "Node " + nodeInfo
                            + " was tagged NOT in slobrok even though it is. It was in the following lists:"
                            + (newNodes.contains(nodeInfo.getNode()) ? " newNodes" : "")
                            + (missingNodeInfos.contains(nodeInfo) ? " missingNodes" : "")
                            + (alteredRpcAddressNodes.contains(nodeInfo.getNode()) ? " alteredNodes" : "")
                            + (returningNodeInfos.contains(nodeInfo) ? " returningNodes" : ""));
                nodeInfo.markRpcAddressLive();
            }
        }
        context.log(log, Level.FINEST, () -> "Slobrok information updated to generation " + cluster.getSlobrokGenerationCount());
        return true;
    }

    private void detectNewAndMissingNodes(
            ContentCluster oldCluster,
            Map<Node, SlobrokData> slobrokNodes,
            List<SlobrokData> newNodes,
            List<NodeInfo> missingNodeInfos,
            List<SlobrokData> alteredRpcAddress,
            List<NodeInfo> returningRpcAddressNodeInfos)
    {
        Iterator<NodeInfo> oldIt = oldCluster.getNodeInfos().iterator();
        Iterator<SlobrokData> newIt = slobrokNodes.values().iterator();
        NodeInfo oldNext = null;
        SlobrokData newNext = null;
        while (true) {
            if (oldNext == null && oldIt.hasNext()) { oldNext = oldIt.next(); }
            if (newNext == null && newIt.hasNext()) { newNext = newIt.next(); }
            if (oldNext == null && newNext == null) { break; }
            if (oldNext == null || (newNext != null && oldNext.getNode().compareTo(newNext.node) > 0)) {
                newNodes.add(newNext);
                newNext = null;
            } else if (newNext == null || newNext.node.compareTo(oldNext.getNode()) > 0) {
                assert(slobrokNodes.get(oldNext.getNode()) == null);
                if (oldNext.isInSlobrok() && oldNext.getRpcAddress() != null) {
                    missingNodeInfos.add(oldNext);
                }
                oldNext = null;
            } else {
                assert(newNext.rpcAddress != null);
                if (oldNext.getRpcAddress() == null || !oldNext.getRpcAddress().equals(newNext.rpcAddress)) {
                    alteredRpcAddress.add(newNext);
                } else if (oldNext.isNotInSlobrok()) {
                    returningRpcAddressNodeInfos.add(oldNext);
                }
                oldNext = null;
                newNext = null;
            }
        }
    }

    private Map<Node, SlobrokData> getSlobrokData(String pattern) {
        Map<Node, SlobrokData> result = new TreeMap<>();
        List<Mirror.Entry> entries = mirror.lookup(pattern);
        context.log(log, Level.FINEST, () -> "Looking for slobrok entries with pattern '" + pattern + "'. Found " + entries.size() + " entries.");
        for (Mirror.Entry entry : entries) {
            StringTokenizer st = new StringTokenizer(entry.getName(), "/");
            String addressType = st.nextToken();
            String cluster = st.nextToken(); // skip
            NodeType nodeType = NodeType.get(st.nextToken());
            Integer nodeIndex = Integer.valueOf(st.nextToken());
            String service = (st.hasMoreTokens() ? st.nextToken() : ""); // skip
            assert(addressType.equals("storage"));
            Node n = new Node(nodeType, nodeIndex);
            result.put(n, new SlobrokData(n, entry.getSpecString()));
        }
        return result;
    }

    private static class SlobrokData {

        public Node node;
        String rpcAddress;

        SlobrokData(Node node, String rpcAddress) {
            this.node = node;
            this.rpcAddress = rpcAddress;
        }
    }

}
