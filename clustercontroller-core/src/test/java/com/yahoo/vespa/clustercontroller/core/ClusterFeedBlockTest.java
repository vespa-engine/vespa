// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.database.DatabaseHandler;
import com.yahoo.vespa.clustercontroller.core.database.ZooKeeperDatabaseFactory;
import com.yahoo.vespa.clustercontroller.utils.util.NoMetricReporter;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.mapOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.usage;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.createResourceUsageJson;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterFeedBlockTest extends FleetControllerTest {

    private static final int NODE_COUNT = 3;

    // TODO dedupe fixture and setup stuff with other tests
    private Supervisor supervisor;
    private FleetController ctrl;
    private DummyCommunicator communicator;
    private EventLog eventLog;
    private int dummyConfigGeneration = 2;

    @Before
    public void setUp() {
        supervisor = new Supervisor(new Transport());
    }

    private void initialize(FleetControllerOptions options) throws Exception {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < options.nodes.size(); ++i) {
            nodes.add(new Node(NodeType.STORAGE, i));
            nodes.add(new Node(NodeType.DISTRIBUTOR, i));
        }

        communicator = new DummyCommunicator(nodes, timer);
        MetricUpdater metricUpdater = new MetricUpdater(new NoMetricReporter(), options.fleetControllerIndex);
        eventLog = new EventLog(timer, metricUpdater);
        ContentCluster cluster = new ContentCluster(options.clusterName, options.nodes, options.storageDistribution,
                options.minStorageNodesUp, options.minRatioOfStorageNodesUp);
        NodeStateGatherer stateGatherer = new NodeStateGatherer(timer, timer, eventLog);
        DatabaseHandler database = new DatabaseHandler(new ZooKeeperDatabaseFactory(), timer, options.zooKeeperServerAddress, options.fleetControllerIndex, timer);
        StateChangeHandler stateGenerator = new StateChangeHandler(timer, eventLog, metricUpdater);
        SystemStateBroadcaster stateBroadcaster = new SystemStateBroadcaster(timer, timer);
        MasterElectionHandler masterElectionHandler = new MasterElectionHandler(options.fleetControllerIndex, options.fleetControllerCount, timer, timer);
        ctrl = new FleetController(timer, eventLog, cluster, stateGatherer, communicator, null, null, communicator, database, stateGenerator, stateBroadcaster, masterElectionHandler, metricUpdater, options);

        ctrl.tick();
        markAllNodesAsUp(options);
        ctrl.tick();
    }

    private void markAllNodesAsUp(FleetControllerOptions options) throws Exception {
        for (int i = 0; i < options.nodes.size(); ++i) {
            communicator.setNodeState(new Node(NodeType.STORAGE, i), State.UP, "");
            communicator.setNodeState(new Node(NodeType.DISTRIBUTOR, i), State.UP, "");
        }
        ctrl.tick();
    }

    public void tearDown() throws Exception {
        if (supervisor != null) {
            supervisor.transport().shutdown().join();
            supervisor = null;
        }
        super.tearDown();
    }

    private static FleetControllerOptions createOptions(Map<String, Double> feedBlockLimits) {
        FleetControllerOptions options = defaultOptions("mycluster");
        options.setStorageDistribution(DistributionBuilder.forFlatCluster(NODE_COUNT));
        options.nodes = new HashSet<>(DistributionBuilder.buildConfiguredNodes(NODE_COUNT));
        options.clusterFeedBlockEnabled = true;
        options.clusterFeedBlockLimit = Map.copyOf(feedBlockLimits);
        return options;
    }

    private void reportResourceUsageFromNode(int nodeIndex, Map<String, Double> resourceUsages) throws Exception {
        String hostInfo = createResourceUsageJson(resourceUsages);
        communicator.setNodeState(new Node(NodeType.STORAGE, nodeIndex), new NodeState(NodeType.STORAGE, State.UP), hostInfo);
        ctrl.tick();
    }

    // TODO some form of hysteresis
    @Test
    public void cluster_feed_can_be_blocked_and_unblocked_by_single_node() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        // Too much cheese in use, must block feed!
        reportResourceUsageFromNode(1, mapOf(usage("cheese", 0.8), usage("wine", 0.3)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
        // TODO check desc?

        // Wine usage has gone up too, we should remain blocked
        reportResourceUsageFromNode(1, mapOf(usage("cheese", 0.8), usage("wine", 0.5)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
        // TODO check desc?

        // Back to normal wine and cheese levels
        reportResourceUsageFromNode(1, mapOf(usage("cheese", 0.6), usage("wine", 0.3)));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
    }

    @Test
    public void cluster_feed_block_state_is_recomputed_when_options_are_updated() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, mapOf(usage("cheese", 0.8), usage("wine", 0.3)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        // Increase cheese allowance. Should now automatically unblock since reported usage is lower.
        ctrl.updateOptions(createOptions(mapOf(usage("cheese", 0.9), usage("wine", 0.4))), dummyConfigGeneration);
        ctrl.tick(); // Options propagation
        ctrl.tick(); // State recomputation
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
    }

}
