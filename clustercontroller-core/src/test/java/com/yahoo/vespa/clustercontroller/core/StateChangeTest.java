// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.testutils.StateWaiter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class StateChangeTest extends FleetControllerTest {

    private final FakeTimer timer = new FakeTimer();

    private FleetController ctrl;
    private DummyCommunicator communicator;

    private void initialize(FleetControllerOptions.Builder builder) throws Exception {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < builder.nodes().size(); ++i) {
            nodes.add(new Node(NodeType.STORAGE, i));
            nodes.add(new Node(NodeType.DISTRIBUTOR, i));
        }

        setUpZooKeeperServer(builder);
        communicator = new DummyCommunicator(nodes, timer);
        boolean start = false;
        FleetControllerOptions options = builder.build();
        var context = new TestFleetControllerContext(options);
        ctrl = createFleetController(timer, options, context, communicator, communicator, null, start);

        ctrl.tick();
        if (options.fleetControllerCount() == 1) {
            markAllNodesAsUp(options);
        }
    }

    private void markAllNodesAsUp(FleetControllerOptions options) throws Exception {
        for (int i = 0; i < options.nodes().size(); ++i) {
            communicator.setNodeState(new Node(NodeType.STORAGE, i), State.UP, "");
            communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, i), State.UP, "");
        }

        ctrl.tick();
    }

    private void verifyNodeEvents(Node n, String correct) {
        String actual = "";
        for (NodeEvent e : ctrl.getEventLog().getNodeEvents(n)) {
            actual += e.toString() + "\n";
        }

        assertEquals(correct, actual);
    }

    @Test
    void testNormalStartup() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxInitProgressTime(50000);

        initialize(options);

        // Should now pick up previous node states
        ctrl.tick();


        for (int j = 0; j < 10; ++j) {
            communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, j), new NodeState(NodeType.DISTRIBUTOR, State.INITIALIZING).setInitProgress(0.0f), "");
        }

        for (int i = 0; i < 100; i += 10) {
            timer.advanceTime(options.maxInitProgressTime() / 20);
            ctrl.tick();
            for (int j = 0; j < 10; ++j) {
                communicator.setNodeState(new Node(NodeType.STORAGE, j), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(i / 100.0f), "");
            }
        }

        // Now, fleet controller should have generated a new cluster state.
        ctrl.tick();

        // Regular init progress does not update the cluster state until the node is done initializing (or goes down,
        // whichever comes first).
        assertEquals("version:5 distributor:10 .0.s:i .0.i:0.0 .1.s:i .1.i:0.0 .2.s:i .2.i:0.0 .3.s:i .3.i:0.0 " +
                ".4.s:i .4.i:0.0 .5.s:i .5.i:0.0 .6.s:i .6.i:0.0 .7.s:i .7.i:0.0 .8.s:i .8.i:0.0 " +
                ".9.s:i .9.i:0.0 storage:10 .0.s:i .0.i:0.1 .1.s:i .1.i:0.1 .2.s:i .2.i:0.1 .3.s:i .3.i:0.1 " +
                ".4.s:i .4.i:0.1 .5.s:i .5.i:0.1 .6.s:i .6.i:0.1 .7.s:i .7.i:0.1 .8.s:i .8.i:0.1 .9.s:i .9.i:0.1",
                ctrl.consolidatedClusterState().toString());

        timer.advanceTime(options.maxInitProgressTime() / 20);
        ctrl.tick();

        for (int i = 0; i < 10; ++i) {
            communicator.setNodeState(new Node(NodeType.STORAGE, i), new NodeState(NodeType.STORAGE, State.UP), "");
        }

        timer.advanceTime(options.maxInitProgressTime() / 20);
        ctrl.tick();

        for (int i = 0; i < 10; ++i) {
            communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, i), new NodeState(NodeType.STORAGE, State.UP), "");
        }

        timer.advanceTime(options.maxInitProgressTime() / 20);
        ctrl.tick();

        assertEquals("version:7 distributor:10 storage:10", ctrl.getSystemState().toString());

        verifyNodeEvents(new Node(NodeType.DISTRIBUTOR, 0),
                         """
                                 Event: distributor.0: Now reporting state U
                                 Event: distributor.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: distributor.0: Now reporting state I, i 0.00
                                 Event: distributor.0: Altered node state in cluster state from 'U' to 'I, i 0.00'
                                 Event: distributor.0: Now reporting state U
                                 Event: distributor.0: Altered node state in cluster state from 'I, i 0.00' to 'U'
                                 """);

        verifyNodeEvents(new Node(NodeType.STORAGE, 0),
                         """
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.0: Now reporting state I, i 0.00 (ls)
                                 Event: storage.0: Altered node state in cluster state from 'U' to 'D'
                                 Event: storage.0: Now reporting state I, i 0.100 (read)
                                 Event: storage.0: Altered node state in cluster state from 'D' to 'I, i 0.100 (read)'
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'I, i 0.100 (read)' to 'U'
                                 """);
    }

    @Test
    void testNodeGoingDownAndUp() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setNodeStateRequestTimeoutMS(60 * 60 * 1000)
                .setMinTimeBetweenNewSystemStates(0)
                .setMaxInitProgressTime(50000)
                // This test makes very specific assumptions about the amount of work done in a single tick.
                // Two-phase cluster state activation changes this quite a bit, so disable it. At least for now.
                .enableTwoPhaseClusterStateActivation(false);

        initialize(builder);

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), State.DOWN, "Closed at other end");

        ctrl.tick();

        String desc = ctrl.getReportedNodeState(new Node(NodeType.DISTRIBUTOR, 0)).getDescription();
        assertTrue(desc.contains("Closed at other end"), desc);

        assertEquals("version:3 distributor:10 .0.s:d storage:10", ctrl.getSystemState().toString());

        timer.advanceTime(1000);

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), new NodeState(NodeType.DISTRIBUTOR, State.UP).setStartTimestamp(12345678), "");
        communicator.setNodeState(new Node(NodeType.STORAGE, 0), State.DOWN, "Closed at other end");

        ctrl.tick();

        assertEquals("version:4 distributor:10 .0.t:12345678 storage:10 .0.s:m", ctrl.getSystemState().toString());

        assert(!ctrl.getReportedNodeState(new Node(NodeType.DISTRIBUTOR, 0)).hasDescription());
        desc = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).getDescription();
        assertTrue(desc.contains("Closed at other end"), desc);

        timer.advanceTime(builder.maxTransitionTime().get(NodeType.STORAGE) + 1);

        ctrl.tick();

        assertEquals("version:5 distributor:10 .0.t:12345678 storage:10 .0.s:d", ctrl.getSystemState().toString());

        desc = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).getDescription();
        assertTrue(desc.contains("Closed at other end"), desc);

        timer.advanceTime(1000);

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setStartTimestamp(12345679), "");

        ctrl.tick();

        assertEquals("version:6 distributor:10 storage:10 .0.t:12345679", ctrl.getSystemState().toString());

        assert(!ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).hasDescription());

        verifyNodeEvents(new Node(NodeType.DISTRIBUTOR, 0),
                         """
                                 Event: distributor.0: Now reporting state U
                                 Event: distributor.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: distributor.0: Failed to get node state: D: Closed at other end
                                 Event: distributor.0: Stopped or possibly crashed after 0 ms, which is before stable state time period. Premature crash count is now 1.
                                 Event: distributor.0: Altered node state in cluster state from 'U' to 'D: Closed at other end'
                                 Event: distributor.0: Now reporting state U, t 12345678
                                 Event: distributor.0: Altered node state in cluster state from 'D: Closed at other end' to 'U, t 12345678'
                                 Event: distributor.0: Altered node state in cluster state from 'U, t 12345678' to 'U'
                                 """);

        verifyNodeEvents(new Node(NodeType.STORAGE, 0),
                         """
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.0: Failed to get node state: D: Closed at other end
                                 Event: storage.0: Stopped or possibly crashed after 1000 ms, which is before stable state time period. Premature crash count is now 1.
                                 Event: storage.0: Altered node state in cluster state from 'U' to 'M: Closed at other end'
                                 Event: storage.0: Exceeded implicit maintenance mode grace period of 5000 milliseconds. Marking node down.
                                 Event: storage.0: Altered node state in cluster state from 'M: Closed at other end' to 'D: Closed at other end'
                                 Event: storage.0: Now reporting state U, t 12345679
                                 Event: storage.0: Altered node state in cluster state from 'D: Closed at other end' to 'U, t 12345679'
                                 """);

        assertEquals(1, ctrl.getCluster().getNodeInfo(new Node(NodeType.DISTRIBUTOR, 0)).getPrematureCrashCount());
        assertEquals(1, ctrl.getCluster().getNodeInfo(new Node(NodeType.STORAGE, 0)).getPrematureCrashCount());
    }

    private void tick(int timeMs) throws Exception {
        timer.advanceTime(timeMs);
        ctrl.tick();
    }

    @Test
    void testNodeGoingDownAndUpNotifying() throws Exception {
        // Same test as above, but node manages to notify why it is going down first.
        FleetControllerOptions.Builder builder = defaultOptions()
                .setNodeStateRequestTimeoutMS(60 * 60 * 1000)
                .setMaxSlobrokDisconnectGracePeriod(100000);

        initialize(builder);

        ctrl.tick();

        tick((int) builder.stableStateTimePeriod() + 1);

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), State.DOWN, "controlled shutdown");

        ctrl.tick();

        String desc = ctrl.getReportedNodeState(new Node(NodeType.DISTRIBUTOR, 0)).getDescription();
        assertTrue(desc.contains("Received signal 15 (SIGTERM - Termination signal)")
                || desc.contains("controlled shutdown"), desc);

        tick(1000);

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), State.UP, "");
        communicator.setNodeState(new Node(NodeType.STORAGE, 0), State.DOWN, "controlled shutdown");

        tick(1000);

        assertEquals("version:4 distributor:10 storage:10 .0.s:m", ctrl.getSystemState().toString());

        assert(!ctrl.getReportedNodeState(new Node(NodeType.DISTRIBUTOR, 0)).hasDescription());
        desc = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).getDescription();
        assertTrue(desc.contains("Received signal 15 (SIGTERM - Termination signal)")
                || desc.contains("controlled shutdown"), desc);

        tick(builder.maxTransitionTime().get(NodeType.STORAGE) + 1);

        assertEquals("version:5 distributor:10 storage:10 .0.s:d", ctrl.getSystemState().toString());
        desc = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).getDescription();
        assertTrue(desc.contains("Received signal 15 (SIGTERM - Termination signal)")
                || desc.contains("controlled shutdown"), desc);

        communicator.setNodeState(new Node(NodeType.STORAGE, 0), State.UP, "");

        tick(1000);

        assertEquals("version:6 distributor:10 storage:10", ctrl.getSystemState().toString());
        assert(!ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 0)).hasDescription());

        assertEquals(0, ctrl.getCluster().getNodeInfo(new Node(NodeType.DISTRIBUTOR, 0)).getPrematureCrashCount());
        assertEquals(0, ctrl.getCluster().getNodeInfo(new Node(NodeType.STORAGE, 0)).getPrematureCrashCount());

        verifyNodeEvents(new Node(NodeType.DISTRIBUTOR, 0),
                         """
                                 Event: distributor.0: Now reporting state U
                                 Event: distributor.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: distributor.0: Failed to get node state: D: controlled shutdown
                                 Event: distributor.0: Altered node state in cluster state from 'U' to 'D: controlled shutdown'
                                 Event: distributor.0: Now reporting state U
                                 Event: distributor.0: Altered node state in cluster state from 'D: controlled shutdown' to 'U'
                                 """);

        verifyNodeEvents(new Node(NodeType.STORAGE, 0),
                         """
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.0: Failed to get node state: D: controlled shutdown
                                 Event: storage.0: Altered node state in cluster state from 'U' to 'M: controlled shutdown'
                                 Event: storage.0: Exceeded implicit maintenance mode grace period of 5000 milliseconds. Marking node down.
                                 Event: storage.0: Altered node state in cluster state from 'M: controlled shutdown' to 'D: controlled shutdown'
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'D: controlled shutdown' to 'U'
                                 """);

    }

    @Test
    void testNodeGoingDownAndUpFast() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxSlobrokDisconnectGracePeriod(60 * 1000);

        initialize(builder);

        ctrl.tick();

        // Node dropped out of slobrok
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i < 10; ++i) {
            nodes.add(new Node(NodeType.STORAGE, i));
            nodes.add(new Node(NodeType.DISTRIBUTOR, i));
        }

        communicator.newNodes = nodes;

        ctrl.tick();
        ctrl.tick();

        assertEquals("version:2 distributor:10 storage:10", ctrl.getSystemState().toString());

        nodes = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            nodes.add(new Node(NodeType.STORAGE, i));
            nodes.add(new Node(NodeType.DISTRIBUTOR, i));
        }

        communicator.newNodes = nodes;

        ctrl.tick();

        assertEquals("version:2 distributor:10 storage:10", ctrl.getSystemState().toString());

        verifyNodeEvents(new Node(NodeType.STORAGE, 0),
                         """
                                 Event: storage.0: Now reporting state U
                                 Event: storage.0: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.0: Node is no longer in slobrok, but we still have a pending state request.
                                 """);
    }

    @Test
    void testMaintenanceWhileNormalStorageNodeRestart() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxSlobrokDisconnectGracePeriod(60 * 1000);

        initialize(builder);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        NodeState ns = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 6));
        assertTrue(ns.getDescription().contains("Connection error: Closed at other end"), ns.toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.0f), "");

        ctrl.tick();

        // Still maintenance since .i progress 0.0 is really down.
        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.6f), "");

        ctrl.tick();

        // Now it's OK
        assertEquals("version:4 distributor:10 storage:10 .6.s:i .6.i:0.6", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.UP), "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10", ctrl.getSystemState().toString());
        assert(!ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 6)).hasDescription());

        verifyNodeEvents(new Node(NodeType.STORAGE, 6),
                         """
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.6: Failed to get node state: D: Connection error: Closed at other end
                                 Event: storage.6: Stopped or possibly crashed after 0 ms, which is before stable state time period. Premature crash count is now 1.
                                 Event: storage.6: Altered node state in cluster state from 'U' to 'M: Connection error: Closed at other end'
                                 Event: storage.6: Now reporting state I, i 0.00 (ls)
                                 Event: storage.6: Now reporting state I, i 0.600 (read)
                                 Event: storage.6: Altered node state in cluster state from 'M: Connection error: Closed at other end' to 'I, i 0.600 (read)'
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'I, i 0.600 (read)' to 'U'
                                 """);
    }

    @Test
    void testMaintenanceWithoutInitIfRetired() throws Exception {
        List<ConfiguredNode> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean retired = (i == 6);
            nodes.add(new ConfiguredNode(i, retired));
        }

        FleetControllerOptions.Builder builder = defaultOptions(nodes)
                .setMaxSlobrokDisconnectGracePeriod(60 * 1000);

        initialize(builder);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        NodeState ns = ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 6));
        assertTrue(ns.getDescription().contains("Connection error: Closed at other end"), ns.toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.0f), "");

        ctrl.tick();

        // Still maintenance since .i progress 0.0 is really down.
        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.6f), "");

        ctrl.tick();

        // Still maintenance since configured.
        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.UP), "");

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:r", ctrl.getSystemState().toString());
        assert(!ctrl.getReportedNodeState(new Node(NodeType.STORAGE, 6)).hasDescription());

        verifyNodeEvents(new Node(NodeType.STORAGE, 6),
                         """
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'D' to 'R'
                                 Event: storage.6: Failed to get node state: D: Connection error: Closed at other end
                                 Event: storage.6: Stopped or possibly crashed after 0 ms, which is before stable state time period. Premature crash count is now 1.
                                 Event: storage.6: Altered node state in cluster state from 'R' to 'M: Connection error: Closed at other end'
                                 Event: storage.6: Now reporting state I, i 0.00 (ls)
                                 Event: storage.6: Now reporting state I, i 0.600 (read)
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'M: Connection error: Closed at other end' to 'R'
                                 """);
    }

    @Test
    void testMaintenanceToDownIfPastTransitionTimeAndRetired() throws Exception {
        List<ConfiguredNode> nodes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean retired = (i == 6);
            nodes.add(new ConfiguredNode(i, retired));
        }

        FleetControllerOptions.Builder builder = defaultOptions(nodes)
                .setMaxSlobrokDisconnectGracePeriod(60 * 1000);
        initialize(builder);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(100000);

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());
    }

    // Test that a node that has been down for a long time (above steady state period), actually alters cluster state to
    // tell that it is initializing, rather than being ignored as a just restarted/unstable node should be.
    @Test
    void testDownNodeInitializing() throws Exception {
        // Actually report initializing state if node has been down steadily for a while
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxTransitionTime(NodeType.STORAGE, 5000)
                .setMaxInitProgressTime(5000)
                .setStableStateTimePeriod(20000)
                .setNodeStateRequestTimeoutMS(1000000)
                .setMaxSlobrokDisconnectGracePeriod(1000000);

        initialize(builder);

        timer.advanceTime(100000); // Node has been in steady state up
        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(100000); // Node has been in steady state down

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.001f), "");

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.1f), "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10 .6.s:i .6.i:0.1", ctrl.getSystemState().toString());

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10 .6.s:i .6.i:0.1", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.UP), "");

        ctrl.tick();

        assertEquals("version:6 distributor:10 storage:10", ctrl.getSystemState().toString());

        verifyNodeEvents(new Node(NodeType.STORAGE, 6),
                         """
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.6: Failed to get node state: D: Connection error: Closed at other end
                                 Event: storage.6: Altered node state in cluster state from 'U' to 'M: Connection error: Closed at other end'
                                 Event: storage.6: Exceeded implicit maintenance mode grace period of 5000 milliseconds. Marking node down.
                                 Event: storage.6: Altered node state in cluster state from 'M: Connection error: Closed at other end' to 'D: Connection error: Closed at other end'
                                 Event: storage.6: Now reporting state I, i 0.00100 (ls)
                                 Event: storage.6: Now reporting state I, i 0.100 (read)
                                 Event: storage.6: Altered node state in cluster state from 'D: Connection error: Closed at other end' to 'I, i 0.100 (read)'
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'I, i 0.100 (read)' to 'U'
                                 """);
    }

    @Test
    void testNodeInitializationStalled() throws Exception {
        // Node should eventually be marked down, and not become initializing next time, but stay down until up
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxTransitionTime(NodeType.STORAGE, 5000)
                .setMaxInitProgressTime(5000)
                .setStableStateTimePeriod(1000000)
                .setMaxSlobrokDisconnectGracePeriod(10000000);

        initialize(builder);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(1000000); // Node has been in steady state down

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.1f), "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10 .6.s:i .6.i:0.1", ctrl.getSystemState().toString());

        timer.advanceTime(builder.maxInitProgressTime() + 1);

        ctrl.tick();

        // We should now get the node marked down.
        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        tick(builder.nodeStateRequestTimeoutMS() + 1);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.0f), "");

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.1f), "");

        tick(1000);

        // Still down since it seemingly crashed during last init.
        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.UP, "");

        ctrl.tick();

        assertEquals("version:7 distributor:10 storage:10", ctrl.getSystemState().toString());

        verifyNodeEvents(new Node(NodeType.STORAGE, 6),
                         """
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.6: Failed to get node state: D: Connection error: Closed at other end
                                 Event: storage.6: Altered node state in cluster state from 'U' to 'M: Connection error: Closed at other end'
                                 Event: storage.6: Exceeded implicit maintenance mode grace period of 5000 milliseconds. Marking node down.
                                 Event: storage.6: Altered node state in cluster state from 'M: Connection error: Closed at other end' to 'D: Connection error: Closed at other end'
                                 Event: storage.6: Now reporting state I, i 0.100 (read)
                                 Event: storage.6: Altered node state in cluster state from 'D: Connection error: Closed at other end' to 'I, i 0.100 (read)'
                                 Event: storage.6: 5001 milliseconds without initialize progress. Marking node down. Premature crash count is now 1.
                                 Event: storage.6: Altered node state in cluster state from 'I, i 0.100 (read)' to 'D'
                                 Event: storage.6: Failed to get node state: D: Connection error: Closed at other end
                                 Event: storage.6: Now reporting state I, i 0.00 (ls)
                                 Event: storage.6: Now reporting state I, i 0.100 (read)
                                 Event: storage.6: Now reporting state U
                                 Event: storage.6: Altered node state in cluster state from 'D' to 'U'
                                 """);

    }

    @Test
    void testBackwardsInitializationProgress() throws Exception {
        // Same as stalled. Mark down, keep down until up
        FleetControllerOptions.Builder builder = defaultOptions();
        builder.setMaxTransitionTime(NodeType.STORAGE, 5000);
        builder.setMaxInitProgressTime(5000);
        builder.setStableStateTimePeriod(1000000);
        // Set long so we don't time out RPC requests and mark nodes down due to advancing time to get in steady state
        builder.setNodeStateRequestTimeoutMS((int) builder.stableStateTimePeriod() * 2);

        initialize(builder);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(1000000); // Node has been in steady state down

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.3f), "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10 .6.s:i .6.i:0.3", ctrl.getSystemState().toString());

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.2f), "");

        ctrl.tick();

        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());
    }

    @Test
    void testNodeGoingDownWhileInitializing() throws Exception {
        // Same as stalled. Mark down, keep down until up
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxTransitionTime(NodeType.STORAGE, 5000)
                .setMaxInitProgressTime(5000)
                .setStableStateTimePeriod(1000000)
                // Set very high so the advanceTime don't start sending state replies right before we disconnect.
                .setNodeStateRequestTimeoutMS(365 * 24 * 60 * 1000);

        initialize(builder);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(1000000); // Node has been in steady state down

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.3f), "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 storage:10 .6.s:i .6.i:0.3", ctrl.getSystemState().toString());

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.3f), "");

        ctrl.tick();

        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.UP, "");

        ctrl.tick();

        assertEquals("version:7 distributor:10 storage:10", ctrl.getSystemState().toString());
    }

    @Test
    void testContinuousCrashRightAfterInit() throws Exception {
        // If node does this too many times, take it out of service
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxTransitionTime(NodeType.STORAGE, 5000)
                .setMaxInitProgressTime(5000)
                .setMaxPrematureCrashes(2)
                .setStableStateTimePeriod(1000000)
                .setMaxSlobrokDisconnectGracePeriod(10000000);

        initialize(builder);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 storage:10 .6.s:m", ctrl.getSystemState().toString());

        timer.advanceTime(1000000); // Node has been in steady state down

        ctrl.tick();

        assertEquals("version:4 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());

        for (int j = 0; j <= builder.maxPrematureCrashes(); ++j) {
            ctrl.tick();

            tick(builder.nodeStateRequestTimeoutMS() + 1);

            communicator.setNodeState(new Node(NodeType.STORAGE, 6), State.DOWN, "Connection error: Closed at other end");

            ctrl.tick();

            tick(builder.nodeStateRequestTimeoutMS() + 1);

            communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.0f), "");

            ctrl.tick();

            tick(builder.nodeStateRequestTimeoutMS() + 1);

            communicator.setNodeState(new Node(NodeType.STORAGE, 6), new NodeState(NodeType.STORAGE, State.INITIALIZING).setInitProgress(0.1f), "");

            tick(1000);
        }

        assertEquals("version:6 distributor:10 storage:10 .6.s:d", ctrl.getSystemState().toString());
    }

    @Test
    void testClusterStateMinNodes() throws Exception {
        // If node does this too many times, take it out of service
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMaxTransitionTime(NodeType.STORAGE, 0)
                .setMaxInitProgressTime(0)
                .setMinDistributorNodesUp(6)
                .setMinStorageNodesUp(8)
                .setMinRatioOfDistributorNodesUp(0.0)
                .setMinRatioOfStorageNodesUp(0.0);

        initialize(builder);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        assertEquals("version:2 distributor:10 storage:10", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 2), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 3), State.DOWN, "Connection error: Closed at other end");

        communicator.setNodeState(new Node(NodeType.STORAGE, 0), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.STORAGE, 1), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 4), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:4 cluster:d distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 4), State.UP, "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 2), State.DOWN, "");

        ctrl.tick();

        assertEquals("version:6 cluster:d distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d .2.s:d", ctrl.getSystemState().toString());
    }

    @Test
    void testClusterStateMinFactor() throws Exception {
        // If node does this too many times, take it out of service
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxTransitionTime(NodeType.STORAGE, 0);
        options.setMaxInitProgressTime(0);
        options.setMinDistributorNodesUp(0);
        options.setMinStorageNodesUp(0);
        options.setMinRatioOfDistributorNodesUp(0.6);
        options.setMinRatioOfStorageNodesUp(0.8);

        initialize(options);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        assertEquals("version:2 distributor:10 storage:10", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 0), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 1), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 2), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 3), State.DOWN, "Connection error: Closed at other end");

        communicator.setNodeState(new Node(NodeType.STORAGE, 0), State.DOWN, "Connection error: Closed at other end");
        communicator.setNodeState(new Node(NodeType.STORAGE, 1), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:3 distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 4), State.DOWN, "Connection error: Closed at other end");

        ctrl.tick();

        assertEquals("version:4 cluster:d distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d .4.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, 4), State.UP, "");

        ctrl.tick();

        assertEquals("version:5 distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 2), State.DOWN, "");

        ctrl.tick();

        assertEquals("version:6 cluster:d distributor:10 .0.s:d .1.s:d .2.s:d .3.s:d storage:10 .0.s:d .1.s:d .2.s:d", ctrl.getSystemState().toString());
    }

    /**
     * Class for testing states of all nodes. Will fail in constructor with
     * debug message on non-expected results.
     */
    abstract static class StateMessageChecker {
        StateMessageChecker(final List<DummyVdsNode> nodes) {
            for (final DummyVdsNode node : nodes) {
                final List<ClusterState> states = node.getSystemStatesReceived();
                final StringBuilder debugString = new StringBuilder();
                debugString.append("Node ").append(node).append("\n");
                for (ClusterState state : states) {
                    debugString.append(state.toString()).append("\n");
                }
                assertEquals(expectedMessageCount(node), states.size(), debugString.toString());
            }
        }
        abstract int expectedMessageCount(final DummyVdsNode node);
    }

    @Test
    void testNoSystemStateBeforeInitialTimePeriod() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMinTimeBeforeFirstSystemStateBroadcast(3 * 60 * 1000);
        setUpFleetController(timer, builder);
        setUpVdsNodes(timer, true);
        // Leave one node down to avoid sending cluster state due to having seen all node states.
        for (int i = 0; i < nodes.size(); ++i) {
            if (i != 3) {
                nodes.get(i).connect();
            }
        }

        StateWaiter waiter = new StateWaiter(timer);
        fleetController().addSystemStateListener(waiter);

        // Ensure all nodes have been seen by fleetcontroller and that it has had enough time to possibly have sent a cluster state
        // Note: this is a candidate state and therefore NOT versioned yet
        waiter.waitForState("^distributor:10 (\\.\\d+\\.t:\\d+ )*storage:10 (\\.\\d+\\.t:\\d+ )*.1.s:d( \\.\\d+\\.t:\\d+)*", timeout());
        waitForCompleteCycle();
        new StateMessageChecker(nodes) {
            @Override
            int expectedMessageCount(final DummyVdsNode node) {
                return 0;
            }
        };

        // Pass time and see that the nodes get state
        timer.advanceTime(3 * 60 * 1000);
        waiter.waitForState("version:\\d+ distributor:10 storage:10 .1.s:d", timeout());

        int version = waiter.getCurrentSystemState().getVersion();
        fleetController().waitForNodesHavingSystemStateVersionEqualToOrAbove(version, 19, timeout());

        new StateMessageChecker(nodes) {
            @Override
            int expectedMessageCount(final DummyVdsNode node) {
                return node.getNode().equals(new Node(NodeType.STORAGE, 1)) ? 0 : 2;
            }
        };
        assertEquals(version, waiter.getCurrentSystemState().getVersion());
    }

    @Test
    void testSystemStateSentWhenNodesReplied() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions()
                .setMinTimeBeforeFirstSystemStateBroadcast(300 * 60 * 1000);
        setUpFleetController(timer, builder);
        setUpVdsNodes(timer, true);

        for (DummyVdsNode node : nodes) {
            node.connect();
        }
        // Marking one node as 'initializing' improves testing of state later on.
        nodes.get(3).setNodeState(State.INITIALIZING);

        final StateWaiter waiter = new StateWaiter(timer);

        fleetController().addSystemStateListener(waiter);
        waiter.waitForState("version:\\d+ distributor:10 storage:10 .1.s:i .1.i:1.0", timeout());
        waitForCompleteCycle();

        final int version = waiter.getCurrentSystemState().getVersion();
        fleetController().waitForNodesHavingSystemStateVersionEqualToOrAbove(version, 20, timeout());

        // The last two versions of the cluster state should be seen (all nodes up,
        // zero out timestate)
        new StateMessageChecker(nodes) {
            @Override
            int expectedMessageCount(final DummyVdsNode node) {
                return 2;
            }
        };
    }

    @Test
    void testDontTagFailingSetSystemStateOk() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        setUpFleetController(timer, options);
        setUpVdsNodes(timer);
        waitForStableSystem();

        StateWaiter waiter = new StateWaiter(timer);
        fleetController().addSystemStateListener(waiter);

        nodes.get(1).failSetSystemState(true);
        int versionBeforeChange = nodes.get(1).getSystemStatesReceived().get(0).getVersion();
        nodes.get(2).disconnect(); // cause a new state
        waiter.waitForState("version:\\d+ distributor:10 .1.s:d storage:10", timeout());
        int versionAfterChange = waiter.getCurrentSystemState().getVersion();
        assertTrue(versionAfterChange > versionBeforeChange);
        fleetController().waitForNodesHavingSystemStateVersionEqualToOrAbove(versionAfterChange, 18, timeout());

        // Assert that the failed node has not acknowledged the latest version.
        // (The version may still be larger than versionBeforeChange if the fleet controller sends a
        // "stable system" update without timestamps in the meantime
        assertTrue(fleetController().getCluster().getNodeInfo(nodes.get(1).getNode()).getClusterStateVersionBundleAcknowledged() < versionAfterChange);

        // Ensure non-concurrent access to getNewestSystemStateVersionSent
        synchronized(timer) {
            int sentVersion = fleetController().getCluster().getNodeInfo(nodes.get(1).getNode()).getNewestSystemStateVersionSent();
            assertTrue(sentVersion == -1 || sentVersion == versionAfterChange);
        }
    }

    @Test
    void testAlteringDistributionSplitCount() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setDistributionBits(17);

        initialize(options);

        timer.advanceTime(1000000); // Node has been in steady state up

        ctrl.tick();

        setMinUsedBitsForAllNodes(15);

        ctrl.tick();

        assertEquals("version:3 bits:15 distributor:10 storage:10", ctrl.getSystemState().toString());

        tick(1000);

        communicator.setNodeState(new Node(NodeType.STORAGE, 0), new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(13), "");

        ctrl.tick();

        assertEquals("version:4 bits:13 distributor:10 storage:10", ctrl.getSystemState().toString());

        tick(1000);
        setMinUsedBitsForAllNodes(16);
        ctrl.tick();

        // Don't increase dist bits until we've reached at least the wanted
        // level, in order to avoid multiple full redistributions of data.
        assertEquals("version:4 bits:13 distributor:10 storage:10", ctrl.getSystemState().toString());

        tick(1000);
        setMinUsedBitsForAllNodes(19);
        ctrl.tick();

        assertEquals("version:5 bits:17 distributor:10 storage:10", ctrl.getSystemState().toString());
    }

    private void setMinUsedBitsForAllNodes(int bits) {
        for (int i = 0; i < 10; ++i) {
            communicator.setNodeState(new Node(NodeType.STORAGE, i), new NodeState(NodeType.STORAGE, State.UP).setMinUsedBits(bits), "");
        }
    }

    @Test
    void testSetAllTimestampsAfterDowntime() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        setUpFleetController(timer, options);
        setUpVdsNodes(timer);
        waitForStableSystem();

        StateWaiter waiter = new StateWaiter(timer);
        fleetController().addSystemStateListener(waiter);

        // Simulate netsplit. Take node down without node booting
        assertTrue(nodes.get(0).isDistributor());
        nodes.get(0).disconnectImmediately();
        waiter.waitForState("version:\\d+ distributor:10 .0.s:d storage:10", timeout());

        // Add node back.
        nodes.get(0).connect();
        waitForStableSystem();

        // At this time, node taken down should have cluster states with all starting timestamps set. Others node should not.
        for (DummyVdsNode node : nodes) {
            node.waitForSystemStateVersion(waiter.getCurrentSystemState().getVersion(), timeout());
            List<ClusterState> states = node.getSystemStatesReceived();
            ClusterState lastState = states.get(0);
            StringBuilder stateHistory = new StringBuilder();
            for (ClusterState state : states) {
                stateHistory.append(state.toString()).append("\n");
            }

            if (node.getNode().equals(new Node(NodeType.DISTRIBUTOR, 0))) {
                for (ConfiguredNode i : options.nodes()) {
                    Node nodeId = new Node(NodeType.STORAGE, i.index());
                    long ts = lastState.getNodeState(nodeId).getStartTimestamp();
                    assertTrue(ts > 0, nodeId + "\n" + stateHistory + "\nWas " + ts + " should be " + fleetController().getCluster().getNodeInfo(nodeId).getStartTimestamp());
                }
            } else {
                for (ConfiguredNode i : options.nodes()) {
                    Node nodeId = new Node(NodeType.STORAGE, i.index());
                    assertEquals(0, lastState.getNodeState(nodeId).getStartTimestamp(), nodeId.toString());
                }
            }

            for (ConfiguredNode i : options.nodes()) {
                Node nodeId = new Node(NodeType.DISTRIBUTOR, i.index());
                assertEquals(0, lastState.getNodeState(nodeId).getStartTimestamp(), nodeId.toString());
            }
        }
    }

    @Test
    void consolidated_cluster_state_reflects_node_changes_when_cluster_is_down() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxTransitionTime(NodeType.STORAGE, 0);
        options.setMinStorageNodesUp(10);
        options.setMinDistributorNodesUp(10);
        initialize(options);

        ctrl.tick();
        assertThat(ctrl.consolidatedClusterState().toString(), equalTo("version:2 distributor:10 storage:10"));

        communicator.setNodeState(new Node(NodeType.STORAGE, 2), State.DOWN, "foo");
        ctrl.tick();

        assertThat(ctrl.consolidatedClusterState().toString(),
                equalTo("version:3 cluster:d distributor:10 storage:10 .2.s:d"));

        // After this point, any further node changes while the cluster is still down won't be published.
        // This is because cluster state similarity checks are short-circuited if both are Down, as no other parts
        // of the state matter. Despite this, REST API access and similar features need up-to-date information,
        // and therefore need to get a state which represents the _current_ state rather than the published state.
        // The consolidated state offers this by selectively generating the current state on-demand if the
        // cluster is down.
        communicator.setNodeState(new Node(NodeType.STORAGE, 5), State.DOWN, "bar");
        ctrl.tick();

        // NOTE: _same_ version, different node state content. Overall cluster down-state is still the same.
        assertThat(ctrl.consolidatedClusterState().toString(),
                equalTo("version:3 cluster:d distributor:10 storage:10 .2.s:d .5.s:d"));
    }

    // Related to the above test, watchTimer invocations must receive the _current_ state and not the
    // published state. Failure to ensure this would cause events to be fired non-stop, as the effect
    // of previous timer invocations (with subsequent state generation) would not be visible.
    @Test
    void timer_events_during_cluster_down_observe_most_recent_node_changes() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxTransitionTime(NodeType.STORAGE, 1000);
        options.setMinStorageNodesUp(10);
        options.setMinDistributorNodesUp(10);
        initialize(options);

        ctrl.tick();
        communicator.setNodeState(new Node(NodeType.STORAGE, 2), State.DOWN, "foo");
        timer.advanceTime(500);
        ctrl.tick();
        communicator.setNodeState(new Node(NodeType.STORAGE, 3), State.DOWN, "foo");
        ctrl.tick();
        assertThat(ctrl.consolidatedClusterState().toString(), equalTo("version:3 cluster:d distributor:10 storage:10 .2.s:m .3.s:m"));

        // Subsequent timer tick should _not_ trigger additional events. Providing published state
        // only would result in "Marking node down" events for node 2 emitted per tick.
        for (int i = 0; i < 3; ++i) {
            timer.advanceTime(5000);
            ctrl.tick();
        }

        verifyNodeEvents(new Node(NodeType.STORAGE, 2),
                         """
                                 Event: storage.2: Now reporting state U
                                 Event: storage.2: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.2: Failed to get node state: D: foo
                                 Event: storage.2: Stopped or possibly crashed after 500 ms, which is before stable state time period. Premature crash count is now 1.
                                 Event: storage.2: Altered node state in cluster state from 'U' to 'M: foo'
                                 """);
        // Note: even though max transition time has passed, events are now emitted only on cluster state
        // publish edges. These are currently suppressed when the cluster state is down, as all cluster down
        // states are considered similar to other cluster down states. This is not necessarily optimal, but
        // if the cluster is down there are bigger problems than not having some debug events logged.
    }

    @Test
    void do_not_emit_multiple_events_when_node_state_does_not_match_versioned_state() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        initialize(options);

        ctrl.tick();
        communicator.setNodeState(
                new Node(NodeType.STORAGE, 2),
                new NodeState(NodeType.STORAGE, State.INITIALIZING)
                        .setInitProgress(0.1f).setMinUsedBits(16), "");

        ctrl.tick();

        // Node 2 is in Init mode with 16 min used bits. Emulate continuous init progress reports
        // from the content node which also contains an updated min used bits. Since init progress
        // and min used bits changes do not by themselves trigger a new cluster state version,
        // deciding whether to emit new events by comparing the reported node state versus the
        // versioned cluster state will cause the code to believe there's been a change every
        // time a new message is received. This will cause a lot of "Altered min distribution
        // bit count" events to be emitted, one per init progress update from the content node.
        // There may be thousands of such updates from each node during their init sequence, so
        // this gets old really quickly.
        for (int i = 1; i < 10; ++i) {
            communicator.setNodeState(
                    new Node(NodeType.STORAGE, 2),
                    new NodeState(NodeType.STORAGE, State.INITIALIZING)
                            .setInitProgress((i * 0.1f) + 0.1f).setMinUsedBits(17), "");
            timer.advanceTime(1000);
            ctrl.tick();
        }

        // We should only get "Altered min distribution bit count" event once, not 9 times.
        verifyNodeEvents(new Node(NodeType.STORAGE, 2),
                         """
                                 Event: storage.2: Now reporting state U
                                 Event: storage.2: Altered node state in cluster state from 'D' to 'U'
                                 Event: storage.2: Now reporting state I, i 0.100 (read)
                                 Event: storage.2: Altered node state in cluster state from 'U' to 'I, i 0.100 (read)'
                                 Event: storage.2: Altered min distribution bit count from 16 to 17
                                 """);

    }

    private static abstract class MockTask extends RemoteClusterControllerTask {
        boolean invoked = false;
        Failure failure;

        boolean isInvoked() { return invoked; }

        boolean isLeadershipLost() {
            return (failure != null) && (failure.getCondition() == FailureCondition.LEADERSHIP_LOST);
        }

        boolean isDeadlineExceeded() {
            return (failure != null) && (failure.getCondition() == FailureCondition.DEADLINE_EXCEEDED);
        }

        @Override
        public boolean hasVersionAckDependency() { return true; }

        @Override
        public void handleFailure(Failure failure) {
            this.failure = failure;
        }
    }

    // We create an explicit mock task class instead of using mock() simply because of
    // the utter pain that mocking void functions (doRemoteFleetControllerTask()) is
    // when using Mockito.
    private static class MockSynchronousTaskWithSideEffects extends MockTask {
        @Override
        public void doRemoteFleetControllerTask(Context ctx) {
            // Trigger a state transition edge that requires a state to be published and ACKed
            NodeState newNodeState = new NodeState(NodeType.STORAGE, State.MAINTENANCE);
            NodeInfo nodeInfo = ctx.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
            nodeInfo.setWantedState(newNodeState);
            ctx.nodeListener.handleNewWantedNodeState(nodeInfo, newNodeState);
            invoked = true;
        }
    }

    private static class FailingMockSynchronousTaskWithSideEffects extends MockSynchronousTaskWithSideEffects {
        @Override
        public boolean isFailed() { return true; }
    }

    private static class MockNoOpSynchronousTask extends MockTask {
        @Override
        public void doRemoteFleetControllerTask(Context ctx) {
            // Tests scheduling this task shall have ensured that node storage.0 already is DOWN,
            // so this won't trigger a new state to be published
            NodeState newNodeState = new NodeState(NodeType.STORAGE, State.DOWN);
            NodeInfo nodeInfo = ctx.cluster.getNodeInfo(new Node(NodeType.STORAGE, 0));
            nodeInfo.setWantedState(newNodeState);
            ctx.nodeListener.handleNewWantedNodeState(nodeInfo, newNodeState);
            invoked = true;
        }
    }

    // TODO ideally we'd break this out so it doesn't depend on fields in the parent test instance, but
    // fleet controller tests have a _lot_ of state, so risk of duplicating a lot of that...
    class RemoteTaskFixture {
        RemoteTaskFixture(FleetControllerOptions.Builder options) throws Exception {
            initialize(options);
            ctrl.tick();
        }

        MockTask scheduleTask(MockTask task) throws Exception {
            ctrl.schedule(task);
            ctrl.tick(); // Task processing iteration
            return task;
        }

        MockTask scheduleVersionDependentTaskWithSideEffects() throws Exception {
            return scheduleTask(new MockSynchronousTaskWithSideEffects());
        }

        MockTask scheduleNoOpVersionDependentTask() throws Exception {
            return scheduleTask(new MockNoOpSynchronousTask());
        }

        MockTask scheduleFailingVersionDependentTaskWithSideEffects() throws Exception {
            return scheduleTask(new FailingMockSynchronousTaskWithSideEffects());
        }

        void markStorageNodeDown(int index) throws Exception {
            communicator.setNodeState(new Node(NodeType.STORAGE, index), State.DOWN, "foo"); // Auto-ACKed
            ctrl.tick();
        }

        void sendPartialDeferredDistributorClusterStateAcks() throws Exception {
            communicator.sendPartialDeferredDistributorClusterStateAcks();
            ctrl.tick();
        }

        void sendAllDeferredDistributorClusterStateAcks() throws Exception {
            communicator.sendAllDeferredDistributorClusterStateAcks();
            ctrl.tick(); // Process cluster state bundle ACKs
            if (ctrl.getOptions().enableTwoPhaseClusterStateActivation()) {
                ctrl.tick(); // Send activations
                ctrl.tick(); // Process activation ACKs
            }
        }

        void processScheduledTask() throws Exception {
            ctrl.tick(); // Cluster state recompute iteration and send
            if (ctrl.getOptions().enableTwoPhaseClusterStateActivation()) {
                ctrl.tick(); // Send activations
                ctrl.tick(); // Process activation ACKs
            }
            ctrl.tick(); // Iff ACKs were received, process version dependent task(s)
        }

        // Only makes sense for tests with more than 1 controller
        void winLeadership() throws Exception {
            Map<Integer, Integer> leaderVotes = new HashMap<>();
            leaderVotes.put(0, 0);
            leaderVotes.put(1, 0);
            leaderVotes.put(2, 0);
            ctrl.handleFleetData(leaderVotes);
            ctrl.tick();
        }

        void loseLeadership() throws Exception {
            // Receive leadership loss event; other nodes not voting for us anymore.
            Map<Integer, Integer> leaderVotes = new HashMap<>();
            leaderVotes.put(0, 0);
            leaderVotes.put(1, 1);
            leaderVotes.put(2, 1);
            ctrl.handleFleetData(leaderVotes);
            ctrl.tick();
        }
    }

    private static FleetControllerOptions.Builder optionsWithZeroTransitionTime() {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxTransitionTime(NodeType.STORAGE, 0);
        return options;
    }

    private static FleetControllerOptions.Builder optionsAllowingZeroNodesDown() {
        FleetControllerOptions.Builder options = optionsWithZeroTransitionTime();
        options.setMinStorageNodesUp(10);
        options.setMinDistributorNodesUp(10);
        return options;
    }

    private RemoteTaskFixture createFixtureWith(FleetControllerOptions.Builder options) throws Exception {
        return new RemoteTaskFixture(options);
    }

    private RemoteTaskFixture createDefaultFixture() throws Exception {
        return new RemoteTaskFixture(defaultOptions());
    }

    @Test
    void synchronous_remote_task_is_completed_when_state_is_acked_by_cluster() throws Exception {
        RemoteTaskFixture fixture = createDefaultFixture();
        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());
        communicator.setShouldDeferDistributorClusterStateAcks(true);

        fixture.processScheduledTask(); // New state generated, but not ACKed by nodes since we're deferring.
        assertFalse(task.isCompleted());

        fixture.sendPartialDeferredDistributorClusterStateAcks();
        assertFalse(task.isCompleted()); // Not yet acked by all nodes

        fixture.sendAllDeferredDistributorClusterStateAcks();
        assertTrue(task.isCompleted()); // Now finally acked by all nodes
    }

    @Test
    void failing_task_is_immediately_completed() throws Exception {
        RemoteTaskFixture fixture = createDefaultFixture();
        MockTask task = fixture.scheduleFailingVersionDependentTaskWithSideEffects();

        assertTrue(task.isInvoked());
        assertTrue(task.isCompleted());
    }

    @Test
    void no_op_synchronous_remote_task_can_complete_immediately_if_current_state_already_acked() throws Exception {
        RemoteTaskFixture fixture = createFixtureWith(optionsWithZeroTransitionTime());
        fixture.markStorageNodeDown(0);
        MockTask task = fixture.scheduleNoOpVersionDependentTask(); // Tries to set node 0 into Down; already in that state

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());

        fixture.processScheduledTask(); // Deferred tasks processing; should complete tasks
        assertTrue(task.isCompleted());
    }

    @Test
    void no_op_synchronous_remote_task_waits_until_current_state_is_acked() throws Exception {
        RemoteTaskFixture fixture = createFixtureWith(optionsWithZeroTransitionTime());

        communicator.setShouldDeferDistributorClusterStateAcks(true);
        fixture.markStorageNodeDown(0);
        MockTask task = fixture.scheduleNoOpVersionDependentTask(); // Tries to set node 0 into Down; already in that state

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());

        fixture.processScheduledTask(); // Deferred task processing; version not satisfied yet
        assertFalse(task.isCompleted());

        fixture.sendAllDeferredDistributorClusterStateAcks();
        assertTrue(task.isCompleted()); // Now acked by all nodes

    }

    // When the cluster is down no intermediate states will be published to the nodes
    // unless the state triggers a cluster Up edge. To avoid hanging task responses
    // for an indeterminate amount of time in this scenario, we effectively treat
    // tasks running in such a context as if they were no-ops. I.e. we only require
    // the cluster down-state to have been published.
    @Test
    void immediately_complete_sync_remote_task_when_cluster_is_down() throws Exception {
        RemoteTaskFixture fixture = createFixtureWith(optionsAllowingZeroNodesDown());
        // Controller options require 10/10 nodes up, so take one down to trigger a cluster Down edge.
        fixture.markStorageNodeDown(1);
        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());

        fixture.processScheduledTask(); // Deferred tasks processing; should complete tasks
        assertTrue(task.isCompleted());
    }

    @Test
    void multiple_tasks_may_be_scheduled_and_answered_at_the_same_time() throws Exception {
        RemoteTaskFixture fixture = createDefaultFixture();
        communicator.setShouldDeferDistributorClusterStateAcks(true);

        MockTask task1 = fixture.scheduleVersionDependentTaskWithSideEffects();
        MockTask task2 = fixture.scheduleVersionDependentTaskWithSideEffects();

        fixture.processScheduledTask();
        assertFalse(task1.isCompleted());
        assertFalse(task2.isCompleted());

        fixture.sendAllDeferredDistributorClusterStateAcks();

        assertTrue(task1.isCompleted());
        assertTrue(task2.isCompleted());
    }

    @Test
    void synchronous_task_immediately_failed_when_leadership_lost() throws Exception {
        FleetControllerOptions.Builder options = optionsWithZeroTransitionTime();
        options.setCount(3);
        RemoteTaskFixture fixture = createFixtureWith(options);

        fixture.winLeadership();
        markAllNodesAsUp(options.build());

        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());

        communicator.setShouldDeferDistributorClusterStateAcks(true);
        fixture.processScheduledTask();
        assertFalse(task.isCompleted());
        assertFalse(task.isLeadershipLost());

        fixture.loseLeadership();

        assertTrue(task.isCompleted());
        assertTrue(task.isLeadershipLost());
    }

    @Test
    void cluster_state_ack_is_not_dependent_on_state_send_grace_period() throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMinTimeBetweenNewSystemStates(10_000);
        RemoteTaskFixture fixture = createFixtureWith(options);

        // Have to increment timer here to be able to send state generated by the scheduled task
        timer.advanceTime(10_000);

        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();
        communicator.setShouldDeferDistributorClusterStateAcks(true);
        fixture.processScheduledTask();
        assertFalse(task.isCompleted()); // Not yet acked by all nodes
        // If tracking whether ACKs are received from the cluster is dependent on the system state
        // send grace period, we won't observe that the task may be completed even though all nodes
        // have ACKed. Would then have to increment timer by 10s and do another tick.
        fixture.sendAllDeferredDistributorClusterStateAcks();
        assertTrue(task.isCompleted());
    }

    @Test
    void synchronous_task_immediately_answered_when_not_leader() throws Exception {
        FleetControllerOptions.Builder builder = optionsWithZeroTransitionTime();
        builder.setCount(3);
        RemoteTaskFixture fixture = createFixtureWith(builder);

        fixture.loseLeadership();
        markAllNodesAsUp(ctrl.getOptions());

        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();

        assertTrue(task.isInvoked());
        assertTrue(task.isCompleted());
    }

    @Test
    void task_not_completed_within_deadline_is_failed_with_deadline_exceeded_error() throws Exception {
        FleetControllerOptions.Builder builder = defaultOptions();
        builder.setMaxDeferredTaskVersionWaitTime(Duration.ofSeconds(60));
        RemoteTaskFixture fixture = createFixtureWith(builder);

        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();
        communicator.setShouldDeferDistributorClusterStateAcks(true);
        fixture.processScheduledTask();

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());
        assertFalse(task.isDeadlineExceeded());

        timer.advanceTime(59_000);
        ctrl.tick();
        assertFalse(task.isCompleted());
        assertFalse(task.isDeadlineExceeded());

        timer.advanceTime(1_001);
        ctrl.tick();
        assertTrue(task.isCompleted());
        assertTrue(task.isDeadlineExceeded());
    }

    private void doTestTaskDeadlineExceeded(boolean deferredActivation, String expectedMessage) throws Exception {
        FleetControllerOptions.Builder options = defaultOptions();
        options.setMaxDeferredTaskVersionWaitTime(Duration.ofSeconds(60));
        options.enableTwoPhaseClusterStateActivation(deferredActivation);
        options.setMaxDivergentNodesPrintedInTaskErrorMessages(10);
        RemoteTaskFixture fixture = createFixtureWith(options);

        MockTask task = fixture.scheduleVersionDependentTaskWithSideEffects();
        communicator.setShouldDeferDistributorClusterStateAcks(true);
        fixture.processScheduledTask();

        assertTrue(task.isInvoked());
        assertFalse(task.isCompleted());
        assertFalse(task.isDeadlineExceeded());
        timer.advanceTime(60_001);
        ctrl.tick();
        assertTrue(task.isCompleted());
        assertTrue(task.isDeadlineExceeded());
        // If we're not using two-phase activation for this test, all storage nodes have ACKed
        // the bundle, but the distributors are explicitly deferred. If we used two-phase activation,
        // all distributors and storage nodes will be listed here.
        assertEquals(expectedMessage, task.failure.getMessage());
    }

    @Test
    void task_not_completed_within_deadline_lists_nodes_not_converged_in_error_message() throws Exception {
        doTestTaskDeadlineExceeded(false, "the following nodes have not converged to " +
                "at least version 3: distributor.0, distributor.1, distributor.2, distributor.3, " +
                "distributor.4, distributor.5, distributor.6, distributor.7, distributor.8, distributor.9");
    }

    @Test
    void task_not_completed_within_deadline_with_deferred_activation_checks_activation_version() throws Exception {
        doTestTaskDeadlineExceeded(true, "the following nodes have not converged to " +
                "at least version 3: distributor.0, distributor.1, distributor.2, distributor.3, " +
                "distributor.4, distributor.5, distributor.6, distributor.7, distributor.8, distributor.9 " +
                "(... and 10 more)");
    }

}
