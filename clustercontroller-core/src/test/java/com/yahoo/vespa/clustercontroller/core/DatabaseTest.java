// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class DatabaseTest extends FleetControllerTest {

    private static final Logger log = Logger.getLogger(DatabaseTest.class.getName());
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
    void testWantedStatesInZooKeeper() throws Exception {
        startingTest("DatabaseTest::testWantedStatesInZooKeeper");
        FleetControllerOptions.Builder builder = defaultOptions("mycluster");
        builder.setZooKeeperServerAddress("127.0.0.1");
        setUpFleetController(true, builder);
        setUpVdsNodes(true);
        log.info("WAITING FOR STABLE SYSTEM");
        waitForStableSystem();


        log.info("VALIDATE STARTING WANTED STATES");
        Map<Node, NodeState> wantedStates = new TreeMap<>();
        for (DummyVdsNode node : nodes) {
            wantedStates.put(node.getNode(), new NodeState(node.getType(), State.UP));
        }
        assertWantedStates(wantedStates);

        log.info("SET A WANTED STATE AND SEE THAT IT GETS PROPAGATED");
        setWantedState(new Node(NodeType.STORAGE, 3), new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("Yoo"), wantedStates);
        waitForState("version:\\d+ distributor:10 storage:10 .3.s:m");
        assertWantedStates(wantedStates);

        log.info("SET ANOTHER WANTED STATE AND SEE THAT IT GETS PROPAGATED");
        setWantedState(new Node(NodeType.DISTRIBUTOR, 2), new NodeState(NodeType.DISTRIBUTOR, State.DOWN), wantedStates);
        waitForState("version:\\d+ distributor:10 .2.s:d storage:10 .3.s:m");
        assertWantedStates(wantedStates);

        log.info("SET YET ANOTHER WANTED STATE AND SEE THAT IT GETS PROPAGATED");
        setWantedState(new Node(NodeType.STORAGE, 7), new NodeState(NodeType.STORAGE, State.RETIRED).setDescription("We wanna replace this node"), wantedStates);
        waitForState("version:\\d+ distributor:10 .2.s:d storage:10 .3.s:m .7.s:r");
        assertWantedStates(wantedStates);

        log.info("CHECK THAT WANTED STATES PERSIST FLEETCONTROLLER RESTART");
        stopFleetController();
        startFleetController(false);

        waitForState("version:\\d+ distributor:10 .2.s:d storage:10 .3.s:m .7.s:r");
        assertWantedStates(wantedStates);

        log.info("CLEAR WANTED STATE");
        setWantedState(new Node(NodeType.STORAGE, 7), new NodeState(NodeType.STORAGE, State.UP), wantedStates);
        assertWantedStates(wantedStates);

        setWantedState(new Node(NodeType.DISTRIBUTOR, 5), new NodeState(NodeType.DISTRIBUTOR, State.DOWN), wantedStates);
        assertWantedStates(wantedStates);

        setWantedState(new Node(NodeType.DISTRIBUTOR, 2), new NodeState(NodeType.DISTRIBUTOR, State.UP), wantedStates);
        assertWantedStates(wantedStates);

        setWantedState(new Node(NodeType.STORAGE, 9), new NodeState(NodeType.STORAGE, State.DOWN), wantedStates);
        assertWantedStates(wantedStates);
    }

    @Test
    void testWantedStateOfUnknownNode() throws Exception {
        startingTest("DatabaseTest::testWantedStatesOfUnknownNode");
        FleetControllerOptions.Builder builder = defaultOptions("mycluster")
                .setMinRatioOfDistributorNodesUp(0)
                .setMinRatioOfStorageNodesUp(0)
                .setZooKeeperServerAddress("localhost");
        setUpFleetController(true, builder);
        setUpVdsNodes(true);
        waitForStableSystem();

        // Populate map of wanted states we should have
        Map<Node, NodeState> wantedStates = new TreeMap<>();
        for (DummyVdsNode node : nodes) {
            wantedStates.put(node.getNode(), new NodeState(node.getType(), State.UP));
        }

        assertWantedStates(wantedStates);

        setWantedState(new Node(NodeType.STORAGE, 1), new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("Yoo"), wantedStates);
        waitForState("version:\\d+ distributor:10 storage:10 .1.s:m");
        assertWantedStates(wantedStates);

        // This should not show up, as it is down
        setWantedState(new Node(NodeType.DISTRIBUTOR, 8), new NodeState(NodeType.DISTRIBUTOR, State.DOWN), wantedStates);
        waitForState("version:\\d+ distributor:10 .8.s:d storage:10 .1.s:m");
        assertWantedStates(wantedStates);

        // This should show up, as down nodes can be turned to maintenance
        setWantedState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("foobar"), wantedStates);
        waitForState("version:\\d+ distributor:10 .8.s:d storage:10 .1.s:m .6.s:m");
        assertWantedStates(wantedStates);

        // This should not show up, as we cannot turn a down node retired
        setWantedState(new Node(NodeType.STORAGE, 7), new NodeState(NodeType.STORAGE, State.RETIRED).setDescription("foobar"), wantedStates);
        waitForState("version:\\d+ distributor:10 .8.s:d storage:10 .1.s:m .6.s:m .7.s:r");
        assertWantedStates(wantedStates);

        // This should not show up, as it is down
        setWantedState(new Node(NodeType.STORAGE, 8), new NodeState(NodeType.STORAGE, State.DOWN).setDescription("foobar"), wantedStates);
        waitForState("version:\\d+ distributor:10 .8.s:d storage:10 .1.s:m .6.s:m .7.s:r .8.s:d");
        assertWantedStates(wantedStates);

        stopFleetController();
        for (int i = 6; i < nodes.size(); ++i) nodes.get(i).disconnect();
        startFleetController(false);

        waitForState("version:\\d+ distributor:3 storage:7 .1.s:m .3.s:d .4.s:d .5.s:d .6.s:m");

        setWantedState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.UP), wantedStates);
        waitForState("version:\\d+ distributor:3 storage:3 .1.s:m");

        for (int i = 6; i < nodes.size(); ++i) nodes.get(i).connect();
        waitForState("version:\\d+ distributor:10 .8.s:d storage:10 .1.s:m .7.s:r .8.s:d");
        assertWantedStates(wantedStates);
    }

    private void assertWantedStates(Map<Node, NodeState> wantedStates) {
        for (DummyVdsNode node : nodes) {
            assertEquals(wantedStates.get(node.getNode()), fleetController().getWantedNodeState(node.getNode()), node.getNode().toString());
        }
    }

    // Note: different semantics than FleetControllerTest.setWantedState
    private void setWantedState(Node n, NodeState ns, Map<Node, NodeState> wantedStates) {
        int rpcPort = fleetController().getRpcPort();
        Target connection = supervisor.connect(new Spec("localhost", rpcPort));
        assertTrue(connection.isValid());

        Request req = new Request("setNodeState");
        req.parameters().add(new StringValue("storage/cluster.mycluster/" + n.getType().toString() + "/" + n.getIndex()));
        req.parameters().add(new StringValue(ns.serialize(true)));
        connection.invokeSync(req, timeout());
        assertEquals(ErrorCode.NONE, req.errorCode(), req.toString());
        assertTrue(req.checkReturnTypes("s"), req.toString());
        wantedStates.put(n, ns);
    }

}
