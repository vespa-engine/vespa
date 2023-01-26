// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.rpc;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.net.HostName;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.MasterElectionHandler;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.listeners.NodeListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO: RpcServer is only used in unit tests, should be removed
public class RpcServer {

    private static final Logger log = Logger.getLogger(RpcServer.class.getName());

    private final Object monitor;
    private final String clusterName;
    private final int fleetControllerIndex;
    private String[] slobrokConnectionSpecs;
    private int port = 0;
    private Supervisor supervisor;
    private Acceptor acceptor;
    private Register register;
    private final List<Request> rpcRequests = new LinkedList<>();
    private MasterElectionHandler masterHandler;
    private final BackOffPolicy slobrokBackOffPolicy;

    public RpcServer(Object monitor, String clusterName, int fleetControllerIndex, BackOffPolicy bop) {
        this.monitor = monitor;
        this.clusterName = clusterName;
        this.fleetControllerIndex = fleetControllerIndex;
        this.slobrokBackOffPolicy = bop;
    }

    public void setMasterElectionHandler(MasterElectionHandler handler) { this.masterHandler = handler; }

    public int getPort() {
        if (acceptor == null) return -1;
        return acceptor.port();
    }

    public void shutdown() {
        disconnect();
    }

    public String getSlobrokName() {
        return "storage/cluster." + clusterName + "/fleetcontroller/" + fleetControllerIndex;
    }

    public void setSlobrokConnectionSpecs(String[] slobrokConnectionSpecs, int port) {
        if (this.slobrokConnectionSpecs == null
                || !Arrays.equals(this.slobrokConnectionSpecs, slobrokConnectionSpecs)
                || this.port != port) {
            this.slobrokConnectionSpecs = slobrokConnectionSpecs;
            this.port = port;
            disconnect();
            connect();
        }
    }

    public boolean isConnected() {
        return (register != null);
    }

    public void connect() {
        disconnect();
        supervisor = new Supervisor(new Transport("rpc" + port)).setDropEmptyBuffers(true);
        addMethods();
        log.log(Level.FINE, () -> "Fleetcontroller " + fleetControllerIndex + ": RPC server attempting to bind to port " + port);
        try {
            acceptor = supervisor.listen(new Spec(port));
        } catch (ListenFailedException e) {
            throw new RuntimeException(e);
        }
        log.log(Level.FINE, () -> "Fleetcontroller " + fleetControllerIndex + ": RPC server listening to port " + acceptor.port());
        SlobrokList slist = new SlobrokList();
        slist.setup(slobrokConnectionSpecs);
        Spec spec = new Spec(HostName.getLocalhost(), acceptor.port());
        log.log(Level.INFO, "Registering " + spec + " with slobrok at " + String.join(" ", slobrokConnectionSpecs));
        if (slobrokBackOffPolicy != null) {
            register = new Register(supervisor, slist, spec, slobrokBackOffPolicy);
        } else {
            register = new Register(supervisor, slist, spec);
        }
        register.registerName(getSlobrokName());
    }

    public void disconnect() {
        if (register != null) {
            log.log(Level.FINE, () -> "Fleetcontroller " + fleetControllerIndex + ": Disconnecting RPC server.");
            register.shutdown();
            register = null;
        }
        if (acceptor != null) {
            acceptor.shutdown().join();
            acceptor = null;
        }
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
            supervisor = null;
        }
    }

    public void addMethods() {
        Method m = new Method("getMaster", "", "is", this::queueRpcRequest);
        m.methodDesc("Get index of current fleetcontroller master");
        m.returnDesc(0, "masterindex", "The index of the current master according to this node, or -1 if there is none.");
        m.returnDesc(1, "description", "A textual field, used for additional information, such as why there is no master.");
        supervisor.addMethod(m);

        m = new Method("getNodeList", "", "SS", this::queueRpcRequest);
        m.methodDesc("Get list of connection-specs to all nodes in the system");
        m.returnDesc(0, "distributors", "connection-spec of all distributor-nodes (empty string for unknown nodes)");
        m.returnDesc(1, "storagenodes", "connection-spec of all storage-nodes, (empty string for unknown nodes)");
        supervisor.addMethod(m);

        m = new Method("getSystemState", "", "ss", this::queueRpcRequest);
        m.methodDesc("Get nodeState of all nodes and the system itself");
        m.returnDesc(0, "systemstate", "nodeState string of system");
        m.returnDesc(1, "nodestate", "nodeState-string for distributor and storage-nodes");
        supervisor.addMethod(m);

        m = new Method("getNodeState", "si", "ssss", this::queueRpcRequest);
        m.methodDesc("Get nodeState of a node");
        m.paramDesc(0, "nodeType", "Type of node. Should be 'storage' or 'distributor'");
        m.paramDesc(1, "nodeIndex", "The node index");
        m.returnDesc(0, "systemState", "This nodes state in the current system state");
        m.returnDesc(1, "nodeState", "This nodes state as it reports itself. (Or down if we can't reach it)");
        m.returnDesc(2, "wantedState", "This nodes wanted state");
        m.returnDesc(3, "rpcAddress", "This nodes RPC server address");
        supervisor.addMethod(m);

        m = new Method("setNodeState", "ss", "s", this::queueRpcRequest);
        m.methodDesc("Set nodeState of a node");
        m.paramDesc(0, "slobrokAddress", "Slobrok address of node");
        m.paramDesc(1, "nodeState", "Desired nodeState of the node (complete nodeState string - [key:value ]*)");
        m.returnDesc(0, "status", "success/failure");
        supervisor.addMethod(m);
    }

    // Called by rpc
    private void queueRpcRequest(Request req) {
        synchronized(monitor) {
            req.detach();
            rpcRequests.add(req);
            monitor.notifyAll();
        }
    }

    public boolean handleRpcRequests(ContentCluster cluster, ClusterState systemState, NodeListener changeListener) {
        if (!isConnected())
            connect();

        boolean handledAnyRequests = false;
        for (int j=0; j<10; ++j) { // Max perform 10 RPC requests per cycle.
            Request req;
            synchronized(monitor) {
                if (rpcRequests.isEmpty()) break;
                Iterator<Request> it = rpcRequests.iterator();
                req = it.next();
                it.remove();
                handledAnyRequests = true;
            }
            try{
                if (req.methodName().equals("getMaster")) {
                    log.log(Level.FINE, "Resolving RPC getMaster request");
                    Integer master = masterHandler.getMaster();
                    String masterReason = masterHandler.getMasterReason();
                    req.returnValues().add(new Int32Value(master == null ? -1 : master));
                    req.returnValues().add(new StringValue(masterReason == null ? "No reason given" : masterReason));
                    req.returnRequest();
                    continue;
                }
                if (!masterHandler.isMaster()) {
                    throw new IllegalStateException("Refusing to answer RPC calls as we are not the master fleetcontroller.");
                }
                if (req.methodName().equals("getNodeList")) {
                    log.log(Level.FINE, "Resolving RPC getNodeList request");
                    List<String> slobrok = new ArrayList<>();
                    List<String> rpc = new ArrayList<>();
                    for(NodeInfo node : cluster.getNodeInfos()) {
                        String s1 = node.getSlobrokAddress();
                        String s2 = node.getRpcAddress();
                        assert(s1 != null);
                        slobrok.add(s1);
                        rpc.add(s2 == null ? "" : s2);
                    }
                    req.returnValues().add(new StringArray(slobrok.toArray(new String[0])));
                    req.returnValues().add(new StringArray(rpc.toArray(new String[0])));
                    req.returnRequest();
                } else if (req.methodName().equals("getSystemState")) {
                    log.log(Level.FINE, "Resolving RPC getSystemState request");
                    req.returnValues().add(new StringValue(""));
                    req.returnValues().add(new StringValue(systemState.toString(true)));
                    req.returnRequest();
                } else if (req.methodName().equals("getNodeState")) {
                    log.log(Level.FINE, "Resolving RPC getNodeState request");

                    NodeType nodeType = NodeType.get(req.parameters().get(0).asString());
                    int nodeIndex = req.parameters().get(1).asInt32();
                    Node node = new Node(nodeType, nodeIndex);
                    req.returnValues().add(new StringValue(systemState.getNodeState(node).serialize()));
                    // Second parameter is state node is reporting
                    NodeInfo nodeInfo = cluster.getNodeInfo(node);
                    if (nodeInfo == null) throw new RuntimeException("No node " + node + " exists in cluster " + cluster.getName());
                    NodeState fromNode = nodeInfo.getReportedState();
                    req.returnValues().add(new StringValue(fromNode == null ? "unknown" : fromNode.serialize()));
                    // Third parameter is state node has been requested to be in
                    req.returnValues().add(new StringValue(nodeInfo.getWantedState().serialize()));
                    // Fourth parameter is RPC address of node
                    req.returnValues().add(new StringValue(nodeInfo.getRpcAddress() == null ? "" : nodeInfo.getRpcAddress()));
                    req.returnRequest();
                } else if (req.methodName().equals("setNodeState")) {
                    String slobrokAddress = req.parameters().get(0).asString();
                    int lastSlash = slobrokAddress.lastIndexOf('/');
                    int nextButLastSlash = slobrokAddress.lastIndexOf('/', lastSlash - 1);
                    if (lastSlash == -1 || nextButLastSlash == -1) {
                        throw new IllegalStateException("Invalid slobrok address '" + slobrokAddress + "'.");
                    }
                    NodeType nodeType = NodeType.get(slobrokAddress.substring(nextButLastSlash + 1, lastSlash));
                    int nodeIndex = Integer.parseInt(slobrokAddress.substring(lastSlash + 1));
                    NodeInfo node = cluster.getNodeInfo(new Node(nodeType, nodeIndex));
                    if (node == null)
                        throw new IllegalStateException("Cannot set wanted state of node " + new Node(nodeType, nodeIndex) + ". Index does not correspond to a configured node.");
                    NodeState nodeState = NodeState.deserialize(nodeType, req.parameters().get(1).asString());
                    if (nodeState.getDescription().equals("") && !nodeState.getState().equals(State.UP) && !nodeState.getState().equals(State.RETIRED)) {
                        nodeState.setDescription("Set by remote RPC client");
                    }
                    NodeState oldState = node.getUserWantedState();
                    String message = (nodeState.getState().equals(State.UP)
                            ? "Clearing wanted nodeState for node " + node
                            : "New wantedstate '" + nodeState + "' stored for node " + node);
                    if (!oldState.equals(nodeState) || !oldState.getDescription().equals(nodeState.getDescription())) {
                        if (!nodeState.getState().validWantedNodeState(nodeType)) {
                            throw new IllegalStateException("State " + nodeState.getState()
                                    + " can not be used as wanted state for node of type " + nodeType);
                        }
                        node.setWantedState(nodeState);
                        changeListener.handleNewWantedNodeState(node, nodeState);
                    } else {
                        message = "Node " + node + " already had wanted state " + nodeState;
                        log.log(Level.FINE, message);
                    }
                    req.returnValues().add(new StringValue(message));
                    req.returnRequest();
                    if (nodeState.getState() == State.UP && node.getPrematureCrashCount() > 0) {
                        log.log(Level.INFO, "Clearing premature crash count of " + node.getPrematureCrashCount() + " as wanted state was set to up");
                        node.setPrematureCrashCount(0);
                    }
                }
            } catch (Exception e) {
                if (log.isLoggable(Level.FINE)) {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    log.log(Level.FINE, "Failed RPC Request: " + sw);
                }
                String errorMsg = e.getMessage();
                if (errorMsg == null) { errorMsg = e.toString(); }
                req.setError(ErrorCode.METHOD_FAILED, errorMsg);
                req.returnRequest();
            }
        }
        return handledAnyRequests;
    }

}
