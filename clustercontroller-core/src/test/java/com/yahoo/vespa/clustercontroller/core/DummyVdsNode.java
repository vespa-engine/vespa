// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import java.util.logging.Level;
import com.yahoo.vdslib.state.*;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCUtil;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 * Used to fake a node in VDS, such that we can test the fleetcontroller without dummy interface for talking to
 * VDS nodes.
 */
public class DummyVdsNode {

    public static Logger log = Logger.getLogger(DummyVdsNode.class.getName());

    private final String[] slobrokConnectionSpecs;
    private final String clusterName;
    private final NodeType type;
    private final int index;
    private NodeState nodeState;
    private String hostInfo = "{}";
    private Supervisor supervisor;
    private Acceptor acceptor;
    private Register register;
    private final int stateCommunicationVersion;
    private boolean negotiatedHandle = false;
    private final Timer timer;
    private boolean failSetSystemStateRequests = false;
    private boolean resetTimestampOnReconnect = false;
    private final Map<Node, Long> highestStartTimestamps = new TreeMap<Node, Long>();
    int timedOutStateReplies = 0;
    int outdatedStateReplies = 0;
    int immediateStateReplies = 0;
    int setNodeStateReplies = 0;
    private boolean registeredInSlobrok = false;

    static class Req {
        Request request;
        long timeout;

        Req(Request r, long timeout) {
            request = r;
            this.timeout = timeout;
        }
    }
    static class BackOff implements BackOffPolicy {
        public void reset() {}
        public double get() { return 0.01; }
        public boolean shouldWarn(double v) { return false; }
    }
    private final List<Req> waitingRequests = new LinkedList<>();

    /**
     * History of received cluster states.
     * Any access to this list or to its members must be synchronized on the timer variable.
     */
    private final List<ClusterStateBundle> clusterStateBundles = new LinkedList<>();
    private int activatedClusterStateVersion = 0;

    private final Thread messageResponder = new Thread() {
        public void run() {
            log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": starting message reponder thread");
            while (true) {
                synchronized (timer) {
                    if (isInterrupted()) break;
                    long currentTime = timer.getCurrentTimeInMillis();
                    for (Iterator<Req> it = waitingRequests.iterator(); it.hasNext(); ) {
                        Req r = it.next();
                        if (r.timeout <= currentTime) {
                            log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": Responding to node state request at time " + currentTime);
                            r.request.returnValues().add(new StringValue(nodeState.serialize()));
                            if (r.request.methodName().equals("getnodestate3")) {
                                r.request.returnValues().add(new StringValue("No host info in dummy implementation"));
                            }
                            r.request.returnRequest();
                            it.remove();
                            ++timedOutStateReplies;
                        }
                    }
                    try{
                        timer.wait(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
            log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": shut down message reponder thread");
        }
    };

    public DummyVdsNode(Timer timer, DummyVdsNodeOptions options, String[] slobrokConnectionSpecs, String clusterName, boolean distributor, int index) throws Exception {
        this.timer = timer;
        this.slobrokConnectionSpecs = slobrokConnectionSpecs;
        this.clusterName = clusterName;
        type = distributor ? NodeType.DISTRIBUTOR : NodeType.STORAGE;
        this.index = index;
        this.nodeState = new NodeState(type, State.UP);
        this.stateCommunicationVersion = options.stateCommunicationVersion;
        messageResponder.start();
        nodeState.setStartTimestamp(timer.getCurrentTimeInMillis() / 1000);
    }

    int getPendingNodeStateCount() { return waitingRequests.size(); }

    public void shutdown() {
        messageResponder.interrupt();
        try{ messageResponder.join(); } catch (InterruptedException e) { /* ignore */ }
        disconnect();
    }

    public int connect() throws ListenFailedException {
        if (resetTimestampOnReconnect) {
            long startTimestamp = timer.getCurrentTimeInMillis() / 1000;
            nodeState.setStartTimestamp(startTimestamp);
            resetTimestampOnReconnect = false;
        }
        supervisor = new Supervisor(new Transport());
        addMethods();
        acceptor = supervisor.listen(new Spec(0));
        SlobrokList slist = new SlobrokList();
        slist.setup(slobrokConnectionSpecs);
        register = new Register(supervisor, slist, new Spec("localhost", acceptor.port()), new BackOff());
        registerSlobrok();
        negotiatedHandle = false;
        return acceptor.port();
    }

    public boolean isConnected() {
        return (registeredInSlobrok && supervisor != null);
    }

    void registerSlobrok() {
        register.registerName(getSlobrokName());
        register.registerName(getSlobrokName() + "/default");
        registeredInSlobrok = true;
    }

    void disconnectSlobrok() {
        register.unregisterName(getSlobrokName());
        register.unregisterName(getSlobrokName() + "/default");
        registeredInSlobrok = false;
    }

    void disconnect() { disconnectImmediately(); }
    void disconnectImmediately() { disconnect(false, 0, false);  }
    void disconnectBreakConnection() { disconnect(true, FleetControllerTest.timeoutMS, false); }
    void disconnectAsShutdown() { disconnect(true, FleetControllerTest.timeoutMS, true); }
    private void disconnect(boolean waitForPendingNodeStateRequest, long timeoutms, boolean setStoppingStateFirst) {
        log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": Breaking connection." + (waitForPendingNodeStateRequest ? " Waiting for pending state first." : ""));
        if (waitForPendingNodeStateRequest) {
            this.waitForPendingGetNodeStateRequest(timeoutms);
        }
        if (setStoppingStateFirst) {
            NodeState newState = nodeState.clone();
            newState.setState(State.STOPPING);
            // newState.setDescription("Received signal 15 (SIGTERM - Termination signal)");
            // Altered in storageserver implementation. Updating now to fit
            newState.setDescription("controlled shutdown");
            setNodeState(newState);
            // Sleep a bit in hopes of answer being written before shutting down socket
            try{ Thread.sleep(100); } catch (InterruptedException e) { /* ignore */ }
        }
        if (supervisor == null) return;
        register.shutdown();
        acceptor.shutdown().join();
        supervisor.transport().shutdown().join();
        supervisor = null;
        log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": Done breaking connection.");
    }

    public String toString() {
        return type + "." + index;
    }

    public boolean isDistributor() { return type.equals(NodeType.DISTRIBUTOR); }
    public NodeType getType() { return type; }

    public Node getNode() {
        return new Node(type, index);
    }

    public int getStateCommunicationVersion() { return stateCommunicationVersion; }

    void waitForSystemStateVersion(int version, long timeout) {
        try {
            long startTime = System.currentTimeMillis();
            while (getLatestSystemStateVersion().orElse(-1) < version) {
                if ( (System.currentTimeMillis() - startTime) > timeout)
                    throw new RuntimeException("Timed out waiting for state version " + version + " in " + this);
                Thread.sleep(10);
            }
        } catch (InterruptedException e) { /* ignore */ }
    }

    /** Returns the latest system state version received, or empty if none are received yet. */
    private Optional<Integer> getLatestSystemStateVersion() {
        synchronized(timer) {
            if (clusterStateBundles.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(clusterStateBundles.get(0).getVersion());
        }
    }

    public boolean hasPendingGetNodeStateRequest() {
        synchronized (timer) {
            return !waitingRequests.isEmpty();
        }
    }

    private void waitForPendingGetNodeStateRequest(long timeout) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeout;
        log.log(Level.FINE, "Dummy node " + this + " waiting for pending node state request.");
        while (true) {
            synchronized(timer) {
                if (!waitingRequests.isEmpty()) {
                    log.log(Level.FINE, "Dummy node " + this + " has pending request, returning.");
                    return;
                }
                try{
                    log.log(Level.FINE, "Dummy node " + this + " waiting " + (endTime - startTime) + " ms for pending request.");
                    timer.wait(endTime - startTime);
                } catch (InterruptedException e) { /* ignore */ }
                log.log(Level.FINE, "Dummy node " + this + " woke up to recheck.");
            }
            startTime = System.currentTimeMillis();
            if (startTime >= endTime) {
                log.log(Level.FINE, "Dummy node " + this + " timeout passed. Don't have pending request.");
                if (!waitingRequests.isEmpty()) {
                    log.log(Level.FINE, "Dummy node " + this + ". Non-empty set of waiting requests");
                }
                throw new IllegalStateException("Timeout. No pending get node state request pending after waiting " + timeout + " milliseconds.");
            }
        }
    }

    void replyToPendingNodeStateRequests() {
        for(Req req : waitingRequests) {
            log.log(Level.FINE, "Dummy node " + this + " answering pending node state request.");
            req.request.returnValues().add(new StringValue(nodeState.serialize()));
            if (req.request.methodName().equals("getnodestate3")) {
                req.request.returnValues().add(new StringValue(hostInfo));
            }
            req.request.returnRequest();
            ++setNodeStateReplies;
        }
        waitingRequests.clear();
    }

    public void setNodeState(NodeState state, String hostInfo) {
        log.log(Level.FINE, "Dummy node " + this + " got new state: " + state);
        synchronized(timer) {
            this.nodeState = state;
            this.hostInfo = hostInfo;
            replyToPendingNodeStateRequests();
        }
    }

    public void setNodeState(NodeState state) {
        setNodeState(state, "{}");
    }

    public void setNodeState(State state) {
        setNodeState(new NodeState(type, state));
    }

    public NodeState getNodeState() {
        synchronized(timer) {
            return nodeState;
        }
    }

    List<ClusterState> getSystemStatesReceived() {
        synchronized(timer) {
            return clusterStateBundles.stream()
                    .map(ClusterStateBundle::getBaselineClusterState)
                    .collect(Collectors.toList());
        }
    }

    public ClusterStateBundle getClusterStateBundle() {
        synchronized(timer) {
            // In a two-phase state activation scenario, bundles are added to `clusterStateBundles` _before_
            // the version has been activated. Since we want this method to only return _activated_ bundles
            // we filter out versions that are not yet activated. In a non two-phase scenario the activated
            // version is implicitly the same as the most recently received bundle, so the filter is a no-op.
            return clusterStateBundles.stream()
                    .filter(b -> b.getVersion() <= activatedClusterStateVersion)
                    .findFirst() // Most recent cluster state bundle first in list
                    .orElse(null);
        }
    }

    public ClusterState getClusterState() {
        return Optional.ofNullable(getClusterStateBundle())
                .map(ClusterStateBundle::getBaselineClusterState)
                .orElse(null);
    }

    String getSlobrokName() {
        return "storage/cluster." + clusterName + "/" + type + "/" + index;
    }

    private void addMethods() {
        Method m;

        m = new Method("vespa.storage.connect", "s", "i", this::rpc_storageConnect);
        m.methodDesc("Binds connection to a storage API handle");
        m.paramDesc(0, "somearg", "Argument looking like slobrok address of the ones we're asking for some reason");
        m.returnDesc(0, "returnCode", "Returncode of request. Should be 0 = OK");
        supervisor.addMethod(m);

        m = new Method("getnodestate", "", "issi", this::rpc_getNodeState);
        m.methodDesc("Get nodeState of a node");
        m.returnDesc(0, "returnCode", "Returncode of request. Should be 1 = OK");
        m.returnDesc(1, "returnMessage", "Textual error message if returncode is not ok.");
        m.returnDesc(2, "nodeState", "The node state of the given node");
        m.returnDesc(3, "progress", "Progress in percent of node initialization");
        supervisor.addMethod(m);

        m = new Method("setsystemstate", "s", "is", this::rpc_setSystemState);
        m.methodDesc("Set system state of entire system");
        m.paramDesc(0, "systemState", "new systemstate");
        m.returnDesc(0, "returnCode", "Returncode of request. Should be 1 = OK");
        m.returnDesc(1, "returnMessage", "Textual error message if returncode is not ok.");
        supervisor.addMethod(m);

        if (stateCommunicationVersion > 0) {
            m = new Method("getnodestate2", "si", "s", this::rpc_getNodeState2);
            m.methodDesc("Get nodeState of a node, answer when state changes from given state.");
            m.paramDesc(0, "nodeStateIn", "The node state of the given node");
            m.paramDesc(1, "timeout", "Time timeout in milliseconds set by the state requester.");
            m.returnDesc(0, "nodeStateOut", "The node state of the given node");
            supervisor.addMethod(m);

            m = new Method("setsystemstate2", "s", "", this::rpc_setSystemState2);
            m.methodDesc("Set system state of entire system");
            m.paramDesc(0, "systemState", "new systemstate");
            supervisor.addMethod(m);

            if (stateCommunicationVersion > 1) {
                m = new Method("getnodestate3", "sii", "ss", this::rpc_getNodeState2);
                m.methodDesc("Get nodeState of a node, answer when state changes from given state.");
                m.paramDesc(0, "nodeStateIn", "The node state of the given node");
                m.paramDesc(1, "timeout", "Time timeout in milliseconds set by the state requester.");
                m.returnDesc(0, "nodeStateOut", "The node state of the given node");
                m.returnDesc(1, "hostinfo", "Information on the host node is running on");
                supervisor.addMethod(m);
            }
        }
        if (stateCommunicationVersion >= RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_VERSION) {
            m = new Method(RPCCommunicator.SET_DISTRIBUTION_STATES_RPC_METHOD_NAME, "bix", "", this::rpc_setDistributionStates);
            m.methodDesc("Set distribution states for cluster and bucket spaces");
            m.paramDesc(0, "compressionType", "Compression type for payload");
            m.paramDesc(1, "uncompressedSize", "Uncompressed size of payload");
            m.paramDesc(2, "payload", "Slime format payload");
            supervisor.addMethod(m);
        }
        if (stateCommunicationVersion >= RPCCommunicator.ACTIVATE_CLUSTER_STATE_VERSION_RPC_VERSION) {
            m = new Method(RPCCommunicator.ACTIVATE_CLUSTER_STATE_VERSION_RPC_METHOD_NAME, "i", "i", this::rpc_activateClusterStateVersion);
            m.methodDesc("Activate a given cluster state version");
            m.paramDesc(0, "stateVersion", "Cluster state version to activate");
            m.returnDesc(0, "actualVersion", "Actual cluster state version on node");
            supervisor.addMethod(m);
        }
    }

    private void rpc_storageConnect(Request req) {
        synchronized(timer) {
            log.log(Level.FINEST, "Dummy node " + this + " got old type handle connect message.");
            req.returnValues().add(new Int32Value(0));
            negotiatedHandle = true;
        }
    }

    private void rpc_getNodeState(Request req) {
        synchronized(timer) {
            if (!negotiatedHandle) {
                req.setError(75000, "Connection not bound to a handle");
                return;
            }
            String stateString = nodeState.serialize(-1, true);
            log.log(Level.FINE, "Dummy node " + this + " got old type get node state request, answering: " + stateString);
            req.returnValues().add(new Int32Value(1));
            req.returnValues().add(new StringValue(""));
            req.returnValues().add(new StringValue(stateString));
            req.returnValues().add(new Int32Value(0));
        }
    }

    boolean sendGetNodeStateReply(int index) {
        for (Iterator<Req> it = waitingRequests.iterator(); it.hasNext(); ) {
             Req r = it.next();
             if (r.request.parameters().size() > 2 && r.request.parameters().get(2).asInt32() == index) {
                 log.log(Level.FINE, "Dummy node " + DummyVdsNode.this.toString() + ": Responding to node state reply from controller " + index + " as we received new one");
                 r.request.returnValues().add(new StringValue(nodeState.serialize()));
                 r.request.returnValues().add(new StringValue("No host info from dummy implementation"));
                 r.request.returnRequest();
                 it.remove();
                 ++outdatedStateReplies;
                 return true;
             }
        }
        return false;
    }

    private void rpc_getNodeState2(Request req) {
        log.log(Level.FINE, "Dummy node " + this + ": Got " + req.methodName() + " request");
        try{
            String oldState = req.parameters().get(0).asString();
            int timeout = req.parameters().get(1).asInt32();
            int index = -1;
            if (req.parameters().size() > 2) {
                index = req.parameters().get(2).asInt32();
            }
            synchronized(timer) {
                boolean sentReply = sendGetNodeStateReply(index);
                NodeState givenState = (oldState.equals("unknown") ? null : NodeState.deserialize(type, oldState));
                if (givenState != null && (givenState.equals(nodeState) || sentReply)) {
                    log.log(Level.FINE, "Dummy node " + this + ": Has same state as reported " + givenState + ". Queing request. Timeout is " + timeout + " ms. "
                            + "Will be answered at time " + (timer.getCurrentTimeInMillis() + timeout * 800l / 1000));
                    req.detach();
                    waitingRequests.add(new Req(req, timer.getCurrentTimeInMillis() + timeout * 800l / 1000));
                    log.log(Level.FINE, "Dummy node " + this + " has now " + waitingRequests.size() + " entries and is " + (waitingRequests.isEmpty() ? "empty" : "not empty"));
                    timer.notifyAll();
                } else {
                    log.log(Level.FINE, "Dummy node " + this + ": Request had " + (givenState == null ? "no state" : "different state(" + givenState +")") + ". Answering with " + nodeState);
                    req.returnValues().add(new StringValue(nodeState.serialize()));
                    if (req.methodName().equals("getnodestate3")) {
                        req.returnValues().add(new StringValue("Dummy node host info"));
                    }
                    ++immediateStateReplies;
                }
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Dummy node " + this + ": An error occurred when answering " + req.methodName() + " request: " + e.getMessage());
            e.printStackTrace(System.err);
            req.setError(ErrorCode.METHOD_FAILED, e.getMessage());
        }
    }

    private void updateStartTimestamps(ClusterState state) {
        for(int i=0; i<2; ++i) {
            NodeType nodeType = (i == 0 ? NodeType.DISTRIBUTOR : NodeType.STORAGE);
            for (int j=0, n=state.getNodeCount(nodeType); j<n; ++j) {
                Node node = new Node(nodeType, j);
                NodeState ns = state.getNodeState(node);
                if (ns.getStartTimestamp() != 0) {
                    Long oldValue = highestStartTimestamps.get(node);
                    if (oldValue != null && oldValue > ns.getStartTimestamp()) {
                        throw new Error("Somehow start timestamp of node " + node + " has gone down");
                    }
                    highestStartTimestamps.put(node, ns.getStartTimestamp());
                }
            }
        }
    }

    void failSetSystemState(boolean failSystemStateRequests) {
        synchronized (timer) {
            this.failSetSystemStateRequests = failSystemStateRequests;
        }
    }

    private boolean shouldFailSetSystemStateRequests() {
        synchronized (timer) {
            return failSetSystemStateRequests;
        }
    }

    private void rpc_setSystemState(Request req) {
        try{
            if (shouldFailSetSystemStateRequests()) {
                req.setError(ErrorCode.GENERAL_ERROR, "Dummy node configured to fail setSystemState() calls");
                return;
            }
            if (!negotiatedHandle) {
                req.setError(75000, "Connection not bound to a handle");
                return;
            }
            ClusterState newState = new ClusterState(req.parameters().get(0).asString());
            synchronized(timer) {
                updateStartTimestamps(newState);
                clusterStateBundles.add(0, ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.withoutAnnotations(newState)));
                timer.notifyAll();
            }
            req.returnValues().add(new Int32Value(1));
            req.returnValues().add(new StringValue("OK"));
            log.log(Level.FINE, "Dummy node " + this + ": Got new system state (through old setsystemstate call) " + newState);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Dummy node " + this + ": An error occurred when answering setsystemstate request: " + e.getMessage());
            e.printStackTrace(System.err);
            req.returnValues().add(new Int32Value(ErrorCode.METHOD_FAILED));
            req.returnValues().add(new StringValue(e.getMessage()));
        }
    }

    private void rpc_setSystemState2(Request req) {
        try{
            if (shouldFailSetSystemStateRequests()) {
                req.setError(ErrorCode.GENERAL_ERROR, "Dummy node configured to fail setSystemState2() calls");
                return;
            }
            ClusterState newState = new ClusterState(req.parameters().get(0).asString());
            synchronized(timer) {
                updateStartTimestamps(newState);
                clusterStateBundles.add(0, ClusterStateBundle.ofBaselineOnly(AnnotatedClusterState.withoutAnnotations(newState)));
                if (stateCommunicationVersion < RPCCommunicator.ACTIVATE_CLUSTER_STATE_VERSION_RPC_VERSION) {
                    activatedClusterStateVersion = newState.getVersion(); // Simulate node that does not know of activation
                }
                timer.notifyAll();
            }
            log.log(Level.FINE, "Dummy node " + this + ": Got new system state " + newState);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Dummy node " + this + ": An error occurred when answering setsystemstate request: " + e.getMessage());
            e.printStackTrace(System.err);
            req.setError(ErrorCode.METHOD_FAILED, e.getMessage());
        }
    }

    private void rpc_setDistributionStates(Request req) {
        try {
            if (shouldFailSetSystemStateRequests()) {
                req.setError(ErrorCode.GENERAL_ERROR, "Dummy node configured to fail setDistributionStates() calls");
                return;
            }
            ClusterStateBundle stateBundle = RPCUtil.decodeStateBundleFromSetDistributionStatesRequest(req);
            synchronized(timer) {
                updateStartTimestamps(stateBundle.getBaselineClusterState());
                clusterStateBundles.add(0, stateBundle);
                if (stateCommunicationVersion < RPCCommunicator.ACTIVATE_CLUSTER_STATE_VERSION_RPC_VERSION) {
                    activatedClusterStateVersion = stateBundle.getVersion(); // Simulate node that does not know of activation
                }
                timer.notifyAll();
            }
            log.log(Level.FINE, "Dummy node " + this + ": Got new cluster state " + stateBundle);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Dummy node " + this + ": An error occurred when answering setdistributionstates request: " + e.getMessage());
            e.printStackTrace(System.err);
            req.setError(ErrorCode.METHOD_FAILED, e.getMessage());
        }
    }

    private void rpc_activateClusterStateVersion(Request req) {
        try {
            if (shouldFailSetSystemStateRequests()) {
                // We assume that failing setDistributionStates also implies failing version activations
                req.setError(ErrorCode.GENERAL_ERROR, "Dummy node configured to fail activateClusterStateVersion() calls");
                return;
            }
            int activateVersion = req.parameters().get(0).asInt32();
            synchronized(timer) {
                int actualVersion = getLatestSystemStateVersion().orElse(0);
                req.returnValues().add(new Int32Value(actualVersion));
                if (activateVersion == actualVersion) {
                    activatedClusterStateVersion = activateVersion;
                    timer.notifyAll();
                } else {
                    log.log(Level.FINE, () -> String.format("Dummy node %s: got a mismatching activation (request version %d, " +
                            "actual %d), not marking version as active", this, activateVersion, actualVersion));
                }
            }
            log.log(Level.FINE, "Dummy node " + this + ": Activating cluster state version " + activateVersion);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Dummy node " + this + ": An error occurred when answering activate_cluster_state_version request: " + e.getMessage());
            e.printStackTrace(System.err);
            req.setError(ErrorCode.METHOD_FAILED, e.getMessage());
        }
    }
}
