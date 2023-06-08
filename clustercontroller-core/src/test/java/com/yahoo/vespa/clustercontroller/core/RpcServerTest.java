// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.testutils.LogFormatter;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitCondition;
import com.yahoo.vespa.clustercontroller.core.testutils.WaitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * Note: RpcServer is only used in unit tests
 *
 * @author humbe
 */
@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class RpcServerTest extends FleetControllerTest {

    public static Logger log = Logger.getLogger(RpcServerTest.class.getName());
    private final FakeTimer timer = new FakeTimer();

    private Supervisor supervisor;

    @BeforeEach
    public void setup() {
        supervisor = new Supervisor(new Transport());
    }

    @AfterEach
    public void teardown() {
        supervisor.transport().shutdown().join();
    }

    @Test
    void testGetSystemState() throws Exception {
        LogFormatter.initializeLogging();
        FleetControllerOptions.Builder options = defaultOptions();
        setUpFleetController(timer, options);
        setUpVdsNodes(timer);
        waitForStableSystem();

        assertTrue(nodes.get(0).isDistributor());
        log.log(Level.INFO, "Disconnecting distributor 0. Waiting for state to reflect change.");
        nodes.get(0).disconnect();
        nodes.get(19).disconnect();
        fleetController().waitForNodesInSlobrok(9, 9, timeout());
        timer.advanceTime(options.nodeStateRequestTimeoutMS() + options.maxSlobrokDisconnectGracePeriod());

        wait(new WaitCondition.StateWait(fleetController(), fleetController().getMonitor()) {
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
             }, new WaitTask() {
                 @Override
                 public boolean performWaitTask() {
                     return false;
                 }
             },
             timeout());

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("getSystemState");
        connection.invokeSync(req, timeout());
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ss"), req.toString());
        String systemState = req.returnValues().get(1).asString();
        ClusterState retrievedClusterState = new ClusterState(systemState);
        assertEquals(State.DOWN, retrievedClusterState.getNodeState(new Node(NodeType.DISTRIBUTOR, 0)).getState(), systemState);
        assertTrue(retrievedClusterState.getNodeState(new Node(NodeType.STORAGE, 9)).getState().oneOf("md"), systemState);
    }

    private void setWantedNodeState(State newState, NodeType nodeType, int nodeIndex) {
        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Node node = new Node(nodeType, nodeIndex);
        NodeState newNodeState = new NodeState(nodeType, newState);

        Request req = setNodeState("storage/cluster.mycluster/" + node.getType().toString() + "/" + node.getIndex(), newNodeState, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());
    }

    @Test
    void testGetNodeState() throws Exception {
        Set<ConfiguredNode> configuredNodes = new TreeSet<>();
        for (int i = 0; i < 10; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        FleetControllerOptions.Builder builder = defaultOptions(configuredNodes);
        builder.setMinRatioOfStorageNodesUp(0);
        builder.setMaxInitProgressTime(30000);
        builder.setStableStateTimePeriod(60000);
        setUpFleetController(timer, builder);
        setUpVdsNodes(timer);
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

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = getNodeState("distributor", 0, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        NodeState reported = NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(1).asString());
        assertTrue(reported.getState().oneOf("d-"), req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("distributor",2, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.DISTRIBUTOR, req.returnValues().get(2).asString()).getState());

        req = getNodeState("distributor", 4, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("distributor", 15, connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("No node distributor.15 exists in cluster mycluster", req.errorMessage());
        assertFalse(req.checkReturnTypes("ssss"), req.toString());

        req = getNodeState("storage", 1, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("s:i i:0.2", req.returnValues().get(0).asString());
        assertEquals("s:i i:0.2", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("storage", 2, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.DOWN, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        reported = NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(1).asString());
        assertTrue(reported.getState().oneOf("d-"), req.returnValues().get(1).asString());
        assertEquals(State.RETIRED, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());

        req = getNodeState("storage", 5, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals("", req.returnValues().get(0).asString());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals("", req.returnValues().get(2).asString());

        req = getNodeState("storage", 7, connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("ssss"), req.toString());
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(0).asString()).getState());
        assertEquals("t:946080000", req.returnValues().get(1).asString());
        assertEquals(State.MAINTENANCE, NodeState.deserialize(NodeType.STORAGE, req.returnValues().get(2).asString()).getState());
    }

    @Test
    void testGetNodeStateWithConfiguredRetired() throws Exception {
        List<ConfiguredNode> configuredNodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            configuredNodes.add(new ConfiguredNode(i, false));
        configuredNodes.add(new ConfiguredNode(4, true)); // Last node is configured retired
        FleetControllerOptions.Builder builder = defaultOptions(configuredNodes)
                .setMinRatioOfStorageNodesUp(0)
                .setMaxInitProgressTime(30000)
                .setStableStateTimePeriod(60000);
        setUpFleetController(timer, builder);
        setUpVdsNodes(timer, false, configuredNodes);
        waitForState("version:\\d+ distributor:5 storage:5 .4.s:r");

        setWantedNodeState(State.DOWN, NodeType.DISTRIBUTOR, 2);
        setWantedNodeState(State.RETIRED, NodeType.STORAGE, 2);
        setWantedNodeState(State.MAINTENANCE, NodeType.STORAGE, 3);
        waitForCompleteCycle();
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(0).disconnect();
        nodes.get(3).disconnect();
        nodes.get(5).disconnect();
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:m .2.s:m .3.s:m .4.s:r");
        timer.advanceTime(1000000);
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:d .2.s:d .3.s:m .4.s:r");
        timer.advanceTime(1000000);
        waitForCompleteCycle(); // Make fleet controller notice that time has changed before any disconnects
        nodes.get(3).setNodeState(new NodeState(nodes.get(3).getType(), State.INITIALIZING).setInitProgress(0.2f));
        nodes.get(3).connect();
        waitForState("version:\\d+ distributor:5 .0.s:d .2.s:d storage:5 .1.s:i .1.i:0.2 .2.s:d .3.s:m .4.s:r");
    }

    @Test
    void testGetNodeStateWithConfigurationChangeToRetiredWhileNodeDown() throws Exception {
        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions(configuredNodes)
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            setUpFleetController(timer, builder);
            setUpVdsNodes(timer, false, configuredNodes);
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
            setUpVdsNodes(timer, false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            var builder = FleetControllerOptions.Builder.copy(fleetController().getOptions())
                    .setNodes(configuredNodes);
            fleetController().updateOptions(builder.build());
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
            var builder = FleetControllerOptions.Builder.copy(fleetController().getOptions())
                    .setNodes(configuredNodes);
            fleetController().updateOptions(builder.build());
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
    void testGetNodeStateWithConfigurationChangeToRetired() throws Exception {
        { // Configuration: 5 nodes, all normal
            List<ConfiguredNode> configuredNodes = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            FleetControllerOptions.Builder builder = defaultOptions(configuredNodes)
                    .setMaxInitProgressTime(30000)
                    .setStableStateTimePeriod(60000);
            options = builder.build();
            setUpFleetController(timer, builder);
            setUpVdsNodes(timer, false, configuredNodes);
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, false));
            var builder = FleetControllerOptions.Builder.copy(fleetController().getOptions())
                    .setNodes(configuredNodes);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:5 storage:5");
        }

        { // Configuration change: Add 2 new nodes and retire the 5 existing ones
            setUpVdsNodes(timer, false, 2);
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            var builder = FleetControllerOptions.Builder.copy(fleetController().getOptions())
                    .setNodes(configuredNodes);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Reconfigure with the same state
            Set<ConfiguredNode> configuredNodes = new TreeSet<>();
            for (int i = 0; i < 5; i++)
                configuredNodes.add(new ConfiguredNode(i, true));
            configuredNodes.add(new ConfiguredNode(5, false));
            configuredNodes.add(new ConfiguredNode(6, false));
            var builder = FleetControllerOptions.Builder.copy(fleetController().getOptions())
                    .setNodes(configuredNodes);
            fleetController().updateOptions(builder.build());
            waitForState("version:\\d+ distributor:7 storage:7 .0.s:r .1.s:r .2.s:r .3.s:r .4.s:r");
        }

        { // Configuration change: Remove the previously retired nodes
            /*
        TODO: Verify current result: version:23 distributor:7 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d storage:7 .0.s:m .1.s:m .2.s:m .3.s:m .4.s:m
        TODO: Make this work without stopping/disconnecting (see StateChangeHandler.setNodes
        Set<ConfiguredNode> configuredNodes = new TreeSet<>();
        configuredNodes.add(new ConfiguredNode(5, false));
        configuredNodes.add(new ConfiguredNode(6, false));
        FleetControllerOptions.Builder builder = new FleetControllerOptions.Builder("mycluster", configuredNodes)
        .setSlobrokConnectionSpecs(options.slobrokConnectionSpecs())
        .setMaxInitProgressTimeMs(30000)
        .setStableStateTimePeriod(60000);
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
    void testSetNodeState() throws Exception {
        Set<Integer> nodeIndexes = new TreeSet<>(List.of(4, 6, 9, 10, 14, 16, 21, 22, 23, 25));
        Set<ConfiguredNode> configuredNodes = nodeIndexes.stream().map(i -> new ConfiguredNode(i, false)).collect(Collectors.toSet());
        FleetControllerOptions.Builder options = defaultOptions(configuredNodes);
        //options.setStorageDistribution(new Distribution(getDistConfig(nodeIndexes)));
        setUpFleetController(timer, options);
        setUpVdsNodes(timer, false, nodeIndexes);
        waitForState("version:\\d+ distributor:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d storage:26 .0.s:d .1.s:d .2.s:d .3.s:d .5.s:d .7.s:d .8.s:d .11.s:d .12.s:d .13.s:d .15.s:d .17.s:d .18.s:d .19.s:d .20.s:d .24.s:d");

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = setNodeState("storage/cluster.mycluster/storage/14", "s:r", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r .*");

        req = setNodeState("storage/cluster.mycluster/storage/16", "s:m", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());

        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
        nodes.get(5 * 2 + 1).disconnect();
        waitForCompleteCycle();
        timer.advanceTime(100000000);
        waitForCompleteCycle();
        assertEquals(State.MAINTENANCE, fleetController().getSystemState().getNodeState(new Node(NodeType.STORAGE, 16)).getState());

        nodes.get(4 * 2 + 1).disconnect();
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:m.* .16.s:m .*");
        nodes.get(4 * 2 + 1).connect();
        timer.advanceTime(100000000);
        // Might need to pass more actual time while waiting below?
        waitForState("version:\\d+ distributor:26 .* storage:26 .* .14.s:r.* .16.s:m .*");
    }

    @Test
    void testSetNodeStateOutOfRange() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(2, 10)));
        setUpFleetController(timer, options);
        setUpVdsNodes(timer);
        waitForStableSystem();

        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = setNodeState("storage/cluster.mycluster/storage/10", "s:m", connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("Cannot set wanted state of node storage.10. Index does not correspond to a configured node.", req.errorMessage(), req.toString());

        req = setNodeState("storage/cluster.mycluster/distributor/10", "s:m", connection);
        assertEquals(ErrorCode.METHOD_FAILED, req.errorCode(), req.toString());
        assertEquals("Cannot set wanted state of node distributor.10. Index does not correspond to a configured node.", req.errorMessage(), req.toString());

        req = setNodeState("storage/cluster.mycluster/storage/9", "s:m", connection);
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());

        waitForState("version:\\d+ distributor:10 storage:10 .9.s:m");
    }

    private Request setNodeState(String node, NodeState newNodeState, Target connection) {
        return setNodeState(node, newNodeState.serialize(true), connection);
    }

    private Request setNodeState(String node, String newNodeState, Target connection) {
        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue(node));
        req.parameters().add(new StringValue(newNodeState));
        connection.invokeSync(req, timeout());
        return req;
    }

    private Request getNodeState(String nodeType, int nodeIndex, Target connection) {
        Request req = new Request("getNodeState");
        req.parameters().add(new StringValue(nodeType));
        req.parameters().add(new Int32Value(nodeIndex));
        connection.invokeSync(req, timeout());
        return req;
    }

}
