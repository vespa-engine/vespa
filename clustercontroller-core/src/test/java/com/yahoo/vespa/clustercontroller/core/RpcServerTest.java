// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.rpc.RpcServer;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitCondition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author humbe
 */
public class RpcServerTest extends FleetControllerTest {

    public static Logger log = Logger.getLogger(RpcServerTest.class.getName());

    private Supervisor supervisor;

    public void tearDown() throws Exception {
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
        }
        super.tearDown();
    }

    @Test
    public void testRebinding() throws Exception {
        startingTest("RpcServerTest::testRebinding");
        Slobrok slobrok = new Slobrok();
        String[] slobrokConnectionSpecs = new String[1];
        slobrokConnectionSpecs[0] = "tcp/localhost:" + slobrok.port();
        RpcServer server = new RpcServer(timer, new Object(), "mycluster", 0, new BackOff());
        server.setSlobrokConnectionSpecs(slobrokConnectionSpecs, 18347);
        int portUsed = server.getPort();
        server.setSlobrokConnectionSpecs(slobrokConnectionSpecs, portUsed);
        server.disconnect();
        server.disconnect();
        server.connect();
        server.connect();
        server.disconnect();
        server.connect();
        server.shutdown();
        slobrok.stop();
    }

    /**
     * For some reason, the first test trying to set up a stable system here occasionally times out.
     * The theory is that some test run before it does something that is not cleaned up in time.
     * Trying to add a test that should provoke the failure, but not fail due to it to see if we can verify that
     * assumption.
     *
     * (testRebinding() does not seem to be that test. Tests in StateChangeTest that runs before this test tests very
     * similar things, so strange if it should be from them too though. Maybe last test there.
     */
    @Test
    public void testFailOccasionallyAndIgnoreToSeeIfOtherTestsThenWork() {
        try{
            startingTest("RpcServerTest::testFailOccasionallyAndIgnoreToSeeIfOtherTestsThenWork");
            setUpFleetController(true, defaultOptions("mycluster"));
            setUpVdsNodes(true, new DummyVdsNodeOptions());
            waitForStableSystem();
        } catch (Throwable t) {}
    }

    @Test
    public void testGetSystemState() throws Exception {
        LogFormatter.initializeLogging();
        startingTest("RpcServerTest::testGetSystemState");
        FleetControllerOptions options = defaultOptions("mycluster");
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        log.log(Level.INFO, "Disconnecting distributor 0. Waiting for state to reflect change.");
        nodes.get(0).disconnect();
        nodes.get(19).disconnect();
        fleetController.waitForNodesInSlobrok(9, 9, timeoutMS);
        timer.advanceTime(options.nodeStateRequestTimeoutMS + options.maxSlobrokDisconnectGracePeriod);

        wait(new WaitCondition.StateWait(fleetController, fleetController.getMonitor()) {
            @Override
            public String isConditionMet() {
                if (currentState == null) {
                    return "No cluster state defined yet";
                }
                NodeState distState = currentState.getNodeState(new Node(NodeType.DISTRIBUTOR, 0));
                if (distState.getState() != State.DOWN) {
                    return "Distributor not detected down yet: " + currentState.toString();
                }
                NodeState storState = currentState.getNodeState(new Node(NodeType.STORAGE, 9));
                if (!storState.getState().oneOf("md")) {
                    return "Storage node not detected down yet: " + currentState.toString();
                }
                return null;
            }
        }, null, timeoutMS);

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getSystemState");
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ss"));
        String systemState = req.returnValues().get(1).asString();
        ClusterState retrievedClusterState = new ClusterState(systemState);
        assertEquals(systemState, State.DOWN, retrievedClusterState.getNodeState(new Node(NodeType.DISTRIBUTOR, 0)).getState());
        assertTrue(systemState, retrievedClusterState.getNodeState(new Node(NodeType.STORAGE, 9)).getState().oneOf("md"));
    }

    private void setWantedNodeState(State newState, NodeType nodeType, int nodeIndex) {
        int rpcPort = fleetController.getRpcPort();
        if (supervisor == null) {
            supervisor = new Supervisor(new Transport());
        }
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Node node = new Node(nodeType, nodeIndex);
        NodeState newNodeState = new NodeState(nodeType, newState);

        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/" + node.getType().toString() + "/" + node.getIndex()));
        req.parameters().add(new StringValue(newNodeState.serialize(true)));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("s"));
    }

    @Test
    public void testGetNodeState() throws Exception {
        startingTest("RpcServerTest::testGetNodeState");
        Set<ConfiguredNode> configuredNodes = new TreeSet<>();
        for (int i = 0; i < 10; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
        options.minRatioOfStorageNodesUp = 0;
        options.maxInitProgressTime = 30000;
        options.stableStateTimePeriod = 60000;
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        setWantedNodeState(State.DOWN, NodeType.DISTRIBUTOR, 2);
        setWantedNodeState(State.RETIRED, NodeType.STORAGE, 2);
        setWantedNodeState(State.MAINTENANCE, NodeType.STORAGE, 7);
        waitForCompleteCycle();
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(0).disconnect();
        nodes.get(3).disconnect();
        nodes.get(5).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:m .2.s:m .7.s:m");
        timer.advanceTime(1000000);
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:d .2.s:d .7.s:m");
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(3).setNodeState(new NodeState(nodes.get(3).getType(), State.INITIALIZING).setInitProgress(0.2f));
        nodes.get(3).connect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:i .1.i:0.2 .2.s:d .7.s:m");

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getNodeState");
        req.parameters().add(new StringValue("distributor"));
        req.parameters().add(new Int32Value(0));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        NodeState reported = NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(1).asString());
        assertTrue(req.returnValues().get(1).asString(), reported.getState().oneOf("d-"));
        assertEquals("", req.returnValues().get(2).asString());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("distributor"));
        req.parameters().add(new Int32Value(2));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(2).asString()).getState());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("distributor"));
        req.parameters().add(new Int32Value(4));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("distributor"));
        req.parameters().add(new Int32Value(15));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.METHOD_FAILED, req.errorCode());
        assertEquals("No node distributor.15 exists in cluster mycluster", req.errorMessage());
        assertFalse(req.toString(), req.checkReturnTypes("ssss"));

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("storage"));
        req.parameters().add(new Int32Value(1));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals("s:i i:0.2", req.returnValues().get(0).asString());
        assertEquals("s:i i:0.2", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("storage"));
        req.parameters().add(new Int32Value(2));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        reported = NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(1).asString());
        assertTrue(req.returnValues().get(1).asString(), reported.getState().oneOf("d-"));
        assertEquals(State.RETIRED, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("storage"));
        req.parameters().add(new Int32Value(5));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = new Request("getNodeState");
        req.parameters().add(new StringValue("storage"));
        req.parameters().add(new Int32Value(7));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("ssss"));
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());
    }

    @Test
    public void testGetNodeStateWithConfiguredRetired() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfiguredRetired");
        List<ConfiguredNode> configuredNodes = new ArrayList<>();
        for (int i = 0; i < 9; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        configuredNodes.add(new ConfiguredNode(9, true)); // Last node is configured retired
        FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
        options.minRatioOfStorageNodesUp = 0;
        options.maxInitProgressTime = 30000;
        options.stableStateTimePeriod = 60000;
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions(), false, configuredNodes);
        waitForState("version:\\d+ distributor:10 storage:10 .9.s:r");

        setWantedNodeState(State.DOWN, NodeType.DISTRIBUTOR, 2);
        setWantedNodeState(State.RETIRED, NodeType.STORAGE, 2);
        setWantedNodeState(State.MAINTENANCE, NodeType.STORAGE, 7);
        waitForCompleteCycle();
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(0).disconnect();
        nodes.get(3).disconnect();
        nodes.get(5).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:m .2.s:m .7.s:m .9.s:r");
        timer.advanceTime(1000000);
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:d .2.s:d .7.s:m .9.s:r");
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(3).setNodeState(new NodeState(nodes.get(3).getType(), State.INITIALIZING).setInitProgress(0.2f));
        nodes.get(3).connect();
        waitForState("version:\\d+ distributor:10 .0.s:d .2.s:d storage:10 .1.s:i .1.i:0.2 .2.s:d .7.s:m .9.s:r");
    }

    @Test
    public void testGetNodeStateWithConfigurationChangeToRetiredWhileNodeDown() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfigurationChangeToRetiredWhileNodeDown");

        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.maxInitProgressTime = 30000;
            options.stableStateTimePeriod = 60000;
            setUpFleetController(true, options);
            setUpVdsNodes(true, new DummyVdsNodeOptions(), false, configuredNodes);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // 2 first storage nodes go down (0 and 2 are the corresponding distributors)
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).disconnectImmediately();
            nodes.get(3).disconnectImmediately();
            waitForState("version:\\d+ distributor:5 storage:5 .0.s:m .1.s:m");
        }

        { // Configuration change: Add 2 new nodes and retire the 5 existing ones
            setUpVdsNodes(true, new DummyVdsNodeOptions(), false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTime = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m .2.s:r .3.s:r .4.s:r");
        }

        { // 2 storage nodes down come up, should go to state retired
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).connect();
            nodes.get(3).connect();
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // 2 first storage nodes go down again
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).disconnectImmediately();
            nodes.get(3).disconnectImmediately();
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m .2.s:r .3.s:r .4.s:r");
        }

        { // Configuration change: Unretire the nodes
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 7; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTime = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:m .1.s:m");
        }

        { // 2 storage nodes down come up, should go to state up
            waitForCompleteCycle();
            timer.advanceTime(1000000);
            waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
            nodes.get(1).connect();
            nodes.get(3).connect();
            waitForState("version:\\d+ distributor:7 storage:7");
        }

    }

    @Test
    public void testGetNodeStateWithConfigurationChangeToRetired() throws Exception {
        startingTest("RpcServerTest::testGetNodeStateWithConfigurationChangeToRetired");

        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.maxInitProgressTime = 30000;
            options.stableStateTimePeriod = 60000;
            setUpFleetController(true, options);
            setUpVdsNodes(true, new DummyVdsNodeOptions(), false, configuredNodes);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTime = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Configuration change: Add 2 new nodes and retire the 5 existing ones
            setUpVdsNodes(true, new DummyVdsNodeOptions(), false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTime = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTime = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Configuration change: Remove the previously retired nodes
            /*
            TODO: Verify current result: version:23 distributor:7 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d storage:7 .0.s:m .1.s:m .2.s:m .3.s:m .4.s:m
            TODO: Make this work without stopping/disconnecting (see StateChangeHandler.setNodes
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            FleetControllerOptions options = new FleetControllerOptions("mycluster", configuredNodes);
            options.slobrokConnectionSpecs = this.options.slobrokConnectionSpecs;
            this.options.maxInitProgressTimeMs = 30000;
            this.options.stableStateTimePeriod = 60000;
            fleetController.updateOptions(options, 0);
            for (int i = 0; i < 5*2; i++) {
                nodes.get(i).disconnectSlobrok();
                nodes.get(i).disconnect();
            }
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d");
            */
        }
    }

    @Test
    public void testSetNodeState() throws Exception {
        startingTest("RpcServerTest::testSetNodeState");
        Set<Integer> nodeIndexes = new TreeSet<>(List.of(4, 6, 9, 10, 14, 16, 21, 22, 23, 25));
        Set<ConfiguredNode> configuredNodes = nodeIndexes.stream().map(i -> new ConfiguredNode(i, false)).collect(Collectors.toSet());
        FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
        //options.setStorageDistribution(new Distribution(getDistConfig(nodeIndexes)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions(), false, nodeIndexes);
        waitForState("version:\\d+ distributor:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d storage:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d");

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/storage/14"));
        req.parameters().add(new StringValue("s:r"));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("s"));

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r .*");

        req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/storage/16"));
        req.parameters().add(new StringValue("s:m"));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());
        assertTrue(req.toString(), req.checkReturnTypes("s"));

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
        nodes.get(5 * 2 + 1).disconnect();
        waitForCompleteCycle();
        timer.advanceTime(100000000);
        waitForCompleteCycle();
        assertEquals(State.MAINTENANCE, fleetController.getSystemState().getNodeState(new Node(NodeType.STORAGE, 16)).getState());

        nodes.get(4 * 2 + 1).disconnect();
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:m.* .16.s:m .*");
        nodes.get(4 * 2 + 1).connect();
        timer.advanceTime(100000000);
        // Might need to pass more actual time while waiting below?
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
    }

    @Test
    public void testSetNodeStateOutOfRange() throws Exception {
        startingTest("RpcServerTest::testSetNodeStateOutOfRange");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(2, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/storage/10"));
        req.parameters().add(new StringValue("s:m"));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.METHOD_FAILED, req.errorCode());
        assertEquals(req.toString(), "Cannot set wanted state of node storage.10. Index does not correspond to a configured node.", req.errorMessage());

        req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/distributor/10"));
        req.parameters().add(new StringValue("s:m"));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.METHOD_FAILED, req.errorCode());
        assertEquals(req.toString(), "Cannot set wanted state of node distributor.10. Index does not correspond to a configured node.", req.errorMessage());

        req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/storage/9"));
        req.parameters().add(new StringValue("s:m"));
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), ErrorCode.NONE, req.errorCode());

        waitForState("version:\\d+ distributor:10 storage:10 .9.s:m");
    }

    @Test
    public void testGetMaster() throws Exception {
        startingTest("RpcServerTest::testGetMaster");
        FleetControllerOptions options = defaultOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(2, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getMaster");
        connection.invokeSync(req, timeoutS);
        assertEquals(req.toString(), 0, req.returnValues().get(0).asInt32());
        assertEquals(req.toString(), "All 1 nodes agree that 0 is current master.", req.returnValues().get(1).asString());

        // Note that this feature is tested better in MasterElectionTest.testGetMaster as it has multiple fleetcontrollers
    }

    @Test
    public void testGetNodeList() throws Exception {
        startingTest("RpcServerTest::testGetNodeList");
        setUpFleetController(true, defaultOptions("mycluster"));
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        nodes.get(0).disconnect();
        waitForState("version:\\d+ distributor:10 .0.s:d storage:10");

        int rpcPort = fleetController.getRpcPort();
        supervisor = new Supervisor(new Transport());
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        // Possibly do request multiple times if we haven't lost slobrok contact first times yet.
        for (int j=0; j<=10; ++j) {
            Request req = new Request("getNodeList");
            connection.invokeSync(req, timeoutS);
            assertEquals(req.errorMessage(), ErrorCode.NONE, req.errorCode());
            assertTrue(req.toString(), req.checkReturnTypes("SS"));
            String[] slobrok = req.returnValues().get(0).asStringArray().clone();
            String[] rpc = req.returnValues().get(1).asStringArray().clone();

            assertEquals(20, slobrok.length);
            assertEquals(20, rpc.length);

            // Verify that we can connect to all addresses returned.
            for (int i=0; i<20; ++i) {
                if (slobrok[i].equals("storage/cluster.mycluster/distributor/0")) {
                    if (i < 10 && !"".equals(rpc[i])) {
                        continue;
                    }
                    assertEquals(slobrok[i], "", rpc[i]);
                    continue;
                }
                assertNotEquals("", rpc[i]);
                Request req2 = new Request("getnodestate2");
                req2.parameters().add(new StringValue("unknown"));
                Target connection2 = supervisor.connect(new Spec(rpc[i]));
                connection2.invokeSync(req2, timeoutS);
                assertEquals(req2.toString(), ErrorCode.NONE, req.errorCode());
            }
            break;
        }
    }

}
