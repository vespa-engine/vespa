// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.log.LogSetup;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabaseFactory;
import com.yahoo.vespa.clustercontroller.core.rpc.RPCCommunicator;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.rpc.SlobrokClient;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServerInterface;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitCondition;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitTask;
import com.yahoo.vespa.clustercontroller.core.testutils.Waiter;
import com.yahoo.vespa.clustercontroller.utils.util.NoMetricReporter;
import org.junit.After;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.fail;

/**
 * @author Håkon Humberset
 */
public abstract class FleetControllerTest implements Waiter {

    private static final Logger log = Logger.getLogger(FleetControllerTest.class.getName());
    private static final int DEFAULT_NODE_COUNT = 10;

    Supervisor supervisor;
    protected final FakeTimer timer = new FakeTimer();
    boolean usingFakeTimer = false;
    protected Slobrok slobrok;
    protected FleetControllerOptions options;
    ZooKeeperTestServer zooKeeperServer;
    protected FleetController fleetController;
    protected List<DummyVdsNode> nodes = new ArrayList<>();
    private String testName;

    final static int timeoutS;
    final static int timeoutMS;
    private final Waiter waiter = new Waiter.Impl(new DataRetriever() {
        @Override
        public Object getMonitor() { return timer; }
        @Override
        public FleetController getFleetController() { return fleetController; }
        @Override
        public List<DummyVdsNode> getDummyNodes() { return nodes; }
        @Override
        public int getTimeoutMS() { return timeoutMS; }
    });

    static {
        LogSetup.initVespaLogging("fleetcontroller");
        timeoutS = 120;
        timeoutMS = timeoutS * 1000;
    }

    static class BackOff implements BackOffPolicy {
        private int counter = 0;
        public void reset() { counter = 0; }
        public double get() { ++counter; return 0.01; }
        public boolean shouldWarn(double v) { return ((counter % 1000) == 10); }
    }

    protected class CleanupZookeeperLogsOnSuccess extends TestWatcher {
        @Override
        protected void failed(Throwable e, Description description) {
            System.err.println("TEST FAILED - NOT cleaning up zookeeper directory");
            shutdownZooKeeper(false);
        }

        @Override
        protected void succeeded(Description description) {
            System.err.println("TEST SUCCEEDED - cleaning up zookeeper directory");
            shutdownZooKeeper(true);
        }

        private void shutdownZooKeeper(boolean cleanupZooKeeperDir) {
            if (zooKeeperServer != null) {
                zooKeeperServer.shutdown(cleanupZooKeeperDir);
                zooKeeperServer = null;
            }
        }
    }

    @Rule
    public TestRule cleanupZookeeperLogsOnSuccess = new CleanupZookeeperLogsOnSuccess();

    protected void startingTest(String name) {
        System.err.println("STARTING TEST: " + name);
        testName = name;
    }

    static protected FleetControllerOptions defaultOptions(String clusterName) {
        return defaultOptions(clusterName, IntStream.range(0, DEFAULT_NODE_COUNT)
                                                    .mapToObj(i -> new ConfiguredNode(i, false))
                                                    .collect(Collectors.toSet()));
    }

    static protected FleetControllerOptions defaultOptions(String clusterName, Collection<ConfiguredNode> nodes) {
        var opts = new FleetControllerOptions(clusterName, nodes);
        opts.enableTwoPhaseClusterStateActivation = true; // Enable by default, tests can explicitly disable.
        return opts;
    }

    void setUpSystem(boolean useFakeTimer, FleetControllerOptions options) throws Exception {
        log.log(Level.FINE, "Setting up system");
        slobrok = new Slobrok();
        this.options = options;
        if (options.zooKeeperServerAddress != null) {
            zooKeeperServer = new ZooKeeperTestServer();
            this.options.zooKeeperServerAddress = zooKeeperServer.getAddress();
            log.log(Level.FINE, "Set up new zookeeper server at " + this.options.zooKeeperServerAddress);
        }
        this.options.slobrokConnectionSpecs = new String[1];
        this.options.slobrokConnectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        this.usingFakeTimer = useFakeTimer;
    }

    FleetController createFleetController(boolean useFakeTimer, FleetControllerOptions options, boolean startThread, StatusPageServerInterface status) throws Exception {
        Objects.requireNonNull(status, "status server cannot be null");
        Timer timer = useFakeTimer ? this.timer : new RealTimer();
        MetricUpdater metricUpdater = new MetricUpdater(new NoMetricReporter(), options.fleetControllerIndex);
        EventLog log = new EventLog(timer, metricUpdater);
        ContentCluster cluster = new ContentCluster(
                options.clusterName,
                options.nodes,
                options.storageDistribution);
        NodeStateGatherer stateGatherer = new NodeStateGatherer(timer, timer, log);
        Communicator communicator = new RPCCommunicator(
                RPCCommunicator.createRealSupervisor(),
                timer,
                options.fleetControllerIndex,
                options.nodeStateRequestTimeoutMS,
                options.nodeStateRequestTimeoutEarliestPercentage,
                options.nodeStateRequestTimeoutLatestPercentage,
                options.nodeStateRequestRoundTripTimeMaxSeconds);
        SlobrokClient lookUp = new SlobrokClient(timer);
        lookUp.setSlobrokConnectionSpecs(new String[0]);
        RpcServer rpcServer = new RpcServer(timer, timer, options.clusterName, options.fleetControllerIndex, options.slobrokBackOffPolicy);
        DatabaseHandler database = new DatabaseHandler(new ZooKeeperDatabaseFactory(), timer, options.zooKeeperServerAddress, options.fleetControllerIndex, timer);
        StateChangeHandler stateGenerator = new StateChangeHandler(timer, log);
        SystemStateBroadcaster stateBroadcaster = new SystemStateBroadcaster(timer, timer);
        MasterElectionHandler masterElectionHandler = new MasterElectionHandler(options.fleetControllerIndex, options.fleetControllerCount, timer, timer);
        FleetController controller = new FleetController(timer, log, cluster, stateGatherer, communicator, status, rpcServer, lookUp, database, stateGenerator, stateBroadcaster, masterElectionHandler, metricUpdater, options);
        if (startThread) {
            controller.start();
        }
        return controller;
    }

    protected void setUpFleetController(boolean useFakeTimer, FleetControllerOptions options) throws Exception {
        setUpFleetController(useFakeTimer, options, true);
    }

    protected void setUpFleetController(boolean useFakeTimer, FleetControllerOptions options, boolean startThread) throws Exception {
        setUpFleetController(useFakeTimer, options, startThread, new StatusHandler.ContainerStatusPageServer());
    }
    protected void setUpFleetController(boolean useFakeTimer, FleetControllerOptions options, boolean startThread, StatusPageServerInterface status) throws Exception {
        if (slobrok == null) setUpSystem(useFakeTimer, options);
        if (fleetController == null) {
            fleetController = createFleetController(useFakeTimer, options, startThread, status);
        } else {
            throw new Exception("called setUpFleetcontroller but it was already setup");
        }
    }

    void stopFleetController() throws Exception {
        if (fleetController != null) {
            fleetController.shutdown();
            fleetController = null;
        }
    }

    void startFleetController() throws Exception {
        if (fleetController == null) {
            fleetController = createFleetController(usingFakeTimer, options, true, new StatusHandler.ContainerStatusPageServer());
        } else {
            log.log(Level.WARNING, "already started fleetcontroller, not starting another");
        }
    }

    protected void setUpVdsNodes(boolean useFakeTimer, DummyVdsNodeOptions options) throws Exception {
        setUpVdsNodes(useFakeTimer, options, false);
    }
    protected void setUpVdsNodes(boolean useFakeTimer, DummyVdsNodeOptions options, boolean startDisconnected) throws Exception {
        setUpVdsNodes(useFakeTimer, options, startDisconnected, DEFAULT_NODE_COUNT);
    }
    protected void setUpVdsNodes(boolean useFakeTimer, DummyVdsNodeOptions options, boolean startDisconnected, int nodeCount) throws Exception {
        TreeSet<Integer> nodeIndexes = new TreeSet<>();
        for (int i = 0; i < nodeCount; ++i)
            nodeIndexes.add(this.nodes.size()/2 + i); // divide by 2 because there are 2 nodes (storage and distributor) per index
        setUpVdsNodes(useFakeTimer, options, startDisconnected, nodeIndexes);
    }
    protected void setUpVdsNodes(boolean useFakeTimer, DummyVdsNodeOptions options, boolean startDisconnected, Set<Integer> nodeIndexes) throws Exception {
        String[] connectionSpecs = new String[1];
        connectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        for (int nodeIndex : nodeIndexes) {
            nodes.add(new DummyVdsNode(useFakeTimer ? timer : new RealTimer(), options, connectionSpecs, this.options.clusterName, true, nodeIndex));
            if ( ! startDisconnected) nodes.get(nodes.size() - 1).connect();
            nodes.add(new DummyVdsNode(useFakeTimer ? timer : new RealTimer(), options, connectionSpecs, this.options.clusterName, false, nodeIndex));
            if ( ! startDisconnected) nodes.get(nodes.size() - 1).connect();
        }
    }
    // TODO: Replace all usages of the above setUp methods with this one, and remove the nodes field

    /**
     * Creates dummy vds nodes for the list of configured nodes and returns them.
     * As two dummy nodes are created for each configured node - one distributor and one storage node -
     * the returned list is twice as large as configuredNodes.
     */
    protected List<DummyVdsNode> setUpVdsNodes(boolean useFakeTimer, DummyVdsNodeOptions options, boolean startDisconnected, List<ConfiguredNode> configuredNodes) throws Exception {
        String[] connectionSpecs = new String[1];
        connectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        nodes = new ArrayList<>();
        final boolean distributor = true;
        for (ConfiguredNode configuredNode : configuredNodes) {
            nodes.add(new DummyVdsNode(useFakeTimer ? timer : new RealTimer(), options, connectionSpecs, this.options.clusterName, distributor, configuredNode.index()));
            if ( ! startDisconnected) nodes.get(nodes.size() - 1).connect();
            nodes.add(new DummyVdsNode(useFakeTimer ? timer : new RealTimer(), options, connectionSpecs, this.options.clusterName, !distributor, configuredNode.index()));
            if ( ! startDisconnected) nodes.get(nodes.size() - 1).connect();
        }
        return nodes;
    }

    static Set<Integer> asIntSet(Integer... idx) {
        return new HashSet<>(Arrays.asList(idx));
    }

    static Set<ConfiguredNode> asConfiguredNodes(Set<Integer> indices) {
        return indices.stream().map(idx -> new ConfiguredNode(idx, false)).collect(Collectors.toSet());
    }

    void waitForStateExcludingNodeSubset(String expectedState, Set<Integer> excludedNodes) throws Exception {
        // Due to the implementation details of the test base, this.waitForState() will always
        // wait until all nodes added in the test have received the latest cluster state. Since we
        // want to entirely ignore node #6, it won't get a cluster state at all and the test will
        // fail unless otherwise handled. We thus use a custom waiter which filters out nodes with
        // the sneaky index (storage and distributors with same index are treated as different nodes
        // in this context).
        Waiter subsetWaiter = new Waiter.Impl(new DataRetriever() {
            @Override
            public Object getMonitor() { return timer; }
            @Override
            public FleetController getFleetController() { return fleetController; }
            @Override
            public List<DummyVdsNode> getDummyNodes() {
                return nodes.stream()
                        .filter(n -> !excludedNodes.contains(n.getNode().getIndex()))
                        .collect(Collectors.toList());
            }
            @Override
            public int getTimeoutMS() { return timeoutMS; }
        });
        subsetWaiter.waitForState(expectedState);
    }

    static Map<NodeType, Integer> transitionTimes(int milliseconds) {
        Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        maxTransitionTime.put(NodeType.DISTRIBUTOR, milliseconds);
        maxTransitionTime.put(NodeType.STORAGE, milliseconds);
        return maxTransitionTime;
    }

    protected void tearDownSystem() throws Exception {
        if (testName != null) {
            //log.log(Level.INFO, "STOPPING TEST " + testName);
            System.err.println("STOPPING TEST " + testName);
            testName = null;
        }
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
        }
        if (fleetController != null) {
            fleetController.shutdown();
            fleetController = null;
        }
        if (nodes != null) for (DummyVdsNode node : nodes) {
            node.shutdown();
            nodes = null;
        }
        if (slobrok != null) {
            slobrok.stop();
            slobrok = null;
        }
    }

    @After
    public void tearDown() throws Exception {
        tearDownSystem();
    }

    public ClusterState waitForStableSystem() throws Exception { return waiter.waitForStableSystem(); }
    public ClusterState waitForStableSystem(int nodeCount) throws Exception { return waiter.waitForStableSystem(nodeCount); }
    public ClusterState waitForState(String state) throws Exception { return waiter.waitForState(state); }
    public ClusterState waitForStateInAllSpaces(String state) throws Exception { return waiter.waitForStateInAllSpaces(state); }
    public ClusterState waitForStateInSpace(String space, String state) throws Exception { return waiter.waitForStateInSpace(space, state); }
    public ClusterState waitForState(String state, int timeoutMS) throws Exception { return waiter.waitForState(state, timeoutMS); }
    public ClusterState waitForInitProgressPassed(Node n, double progress) { return waiter.waitForInitProgressPassed(n, progress); }
    public ClusterState waitForClusterStateIncludingNodesWithMinUsedBits(int bitcount, int nodecount) { return waiter.waitForClusterStateIncludingNodesWithMinUsedBits(bitcount, nodecount); }

    public void wait(WaitCondition c, WaitTask wt, int timeoutMS) {
        waiter.wait(c, wt, timeoutMS);
    }

    void waitForCompleteCycle() {
        fleetController.waitForCompleteCycle(timeoutMS);
    }

    private static class ExpectLine {
        Pattern regex;
        int matchedCount = 0;
        int minCount = 1;
        int maxCount = 1;
        boolean repeatable() { return (maxCount == 0 || maxCount > matchedCount); }
        boolean optional()   { return (matchedCount >= minCount); }

        boolean matches(String event) {
            if (event == null) return false;
            boolean m = regex.matcher(event).matches();
            if (m) ++matchedCount;
            return m;
        }

        ExpectLine(String pattern) {
            if (pattern.charAt(0) == '?') {
                pattern = pattern.substring(1);
                minCount = 0;
            } else if (pattern.charAt(0) == '*') {
                pattern = pattern.substring(1);
                minCount = 0;
                maxCount = 0;
            } else if (pattern.charAt(0) == '+') {
                pattern = pattern.substring(1);
                maxCount = 0;
            }
            regex = Pattern.compile(pattern);
	}

        public String toString() {
            return "{"+minCount+","+maxCount+"}("+matchedCount+") " + regex;
        }
    }

    /**
     * Verifies that node event list is equal to some expected value.
     * The format of the expected values is as follows:
     *   <ul>
     *   <li>Each line in the exp string specifies a pattern to match one or more events.
     *   <li>A line starting with ? * or + means that the line can match 0 or 1, 0 or more or 1 or more respectively.
     *   <li>The rest of the line is a regular expression.
     *   </ul>
     */
    protected void verifyNodeEvents(Node n, String exp) {
        List<NodeEvent> events = fleetController.getNodeEvents(n);
        String[] expectLines = exp.split("\n");
        List<ExpectLine> expected = new ArrayList<>();
        for (String line : expectLines) {
            expected.add(new ExpectLine(line));
        }

        boolean mismatch = false;
        StringBuilder errors = new StringBuilder();

        int gotno = 0;
        int expno = 0;

        while (gotno < events.size() || expno < expected.size()) {
            String eventLine = null;
            if (gotno < events.size()) {
                NodeEvent e = events.get(gotno);
                eventLine = e.toString();
            }

            ExpectLine pattern = null;
            if (expno < expected.size()) {
                pattern = expected.get(expno);
            }

            if (pattern == null) {
                errors.append("Exhausted expected list before matching event " + gotno
                              + ": '" + eventLine + "'.");
                mismatch = true;
                break;
            }

            if (pattern.matches(eventLine)) {
                if (! pattern.repeatable()) {
                    ++expno;
                }
                ++gotno;
            } else {
                if (pattern.optional()) {
                    ++expno;
                } else {
                    errors.append("Event " + gotno + ": '" + eventLine
                                  + "' did not match regex " + expno + ": " + pattern);
                    mismatch = true;
                    break;
                }
            }
        }
        if (!mismatch && expno < expected.size()) {
            errors.append("Too few entries in event log (only matched "
                          + expno + " of " + expected.size() + ")");
            mismatch = true;
        }
        if (mismatch) {
            StringBuilder eventsGotten = new StringBuilder();
            for (Event e : events) {
                String eventLine = e.toString();
                eventsGotten.append(eventLine).append("\n");
            }
            errors.append("\nExpected events matching:\n" + exp + "\n");
            errors.append("but got the following events:\n" + eventsGotten.toString());
            fail(errors.toString());
        }
    }

    public static Set<ConfiguredNode> toNodes(Integer ... indexes) {
        return Arrays.stream(indexes)
                .map(i -> new ConfiguredNode(i, false))
                .collect(Collectors.toSet());
    }

    void setWantedState(DummyVdsNode node, State state, String reason) {
        if (supervisor == null) {
            supervisor = new Supervisor(new Transport());
        }
        NodeState ns = new NodeState(node.getType(), state);
        if (reason != null) ns.setDescription(reason);
        Target connection = supervisor.connect(new Spec("localhost", fleetController.getRpcPort()));
        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue(node.getSlobrokName()));
        req.parameters().add(new StringValue(ns.serialize()));
        connection.invokeSync(req, timeoutS);
        if (req.isError()) {
            fail("Failed to invoke setNodeState(): " + req.errorCode() + ": " + req.errorMessage());
        }
        if (!req.checkReturnTypes("s")) {
            fail("Failed to invoke setNodeState(): Invalid return types.");
        }
    }

}
