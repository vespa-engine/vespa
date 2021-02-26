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
import java.util.Set;

import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.mapOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.setOf;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.usage;
import static com.yahoo.vespa.clustercontroller.core.FeedBlockUtil.createResourceUsageJson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterFeedBlockTest extends FleetControllerTest {

    private static final int NODE_COUNT = 3;

    // TODO dedupe fixture and setup stuff with other tests
    private Supervisor supervisor;
    private FleetController ctrl;
    private DummyCommunicator communicator;

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
        EventLog eventLog = new EventLog(timer, metricUpdater);
        ContentCluster cluster = new ContentCluster(options.clusterName, options.nodes, options.storageDistribution);
        NodeStateGatherer stateGatherer = new NodeStateGatherer(timer, timer, eventLog);
        DatabaseHandler database = new DatabaseHandler(new ZooKeeperDatabaseFactory(), timer, options.zooKeeperServerAddress, options.fleetControllerIndex, timer);
        StateChangeHandler stateGenerator = new StateChangeHandler(timer, eventLog);
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

    private static FleetControllerOptions createOptions(Map<String, Double> feedBlockLimits,
                                                        double clusterFeedBlockNoiseLevel) {
        FleetControllerOptions options = defaultOptions("mycluster");
        options.setStorageDistribution(DistributionBuilder.forFlatCluster(NODE_COUNT));
        options.nodes = new HashSet<>(DistributionBuilder.buildConfiguredNodes(NODE_COUNT));
        options.clusterFeedBlockEnabled = true;
        options.clusterFeedBlockLimit = Map.copyOf(feedBlockLimits);
        options.clusterFeedBlockNoiseLevel = clusterFeedBlockNoiseLevel;
        return options;
    }

    private static FleetControllerOptions createOptions(Map<String, Double> feedBlockLimits) {
        return createOptions(feedBlockLimits, 0.0);
    }

    private void reportResourceUsageFromNode(int nodeIndex, State nodeState, Set<FeedBlockUtil.UsageDetails> resourceUsages) throws Exception {
        String hostInfo = createResourceUsageJson(resourceUsages);
        communicator.setNodeState(new Node(NodeType.STORAGE, nodeIndex), new NodeState(NodeType.STORAGE, nodeState), hostInfo);
        ctrl.tick();
    }

    private void reportResourceUsageFromNode(int nodeIndex, Set<FeedBlockUtil.UsageDetails> resourceUsages) throws Exception {
        reportResourceUsageFromNode(nodeIndex, State.UP, resourceUsages);
    }

    @Test
    public void cluster_feed_can_be_blocked_and_unblocked_by_single_node() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        // Too much cheese in use, must block feed!
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.3)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
        // TODO check desc?

        // Wine usage has gone up too, we should remain blocked
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.5)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
        // TODO check desc?

        // Back to normal wine and cheese levels
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.6), usage("wine", 0.3)));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
    }

    @Test
    public void cluster_feed_block_state_is_recomputed_when_options_are_updated() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.3)));
        assertTrue(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        // Increase cheese allowance. Should now automatically unblock since reported usage is lower.
        int dummyConfigGeneration = 2;
        ctrl.updateOptions(createOptions(mapOf(usage("cheese", 0.9), usage("wine", 0.4))), dummyConfigGeneration);
        ctrl.tick(); // Options propagation
        ctrl.tick(); // State recomputation
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
    }

    @Test
    public void cluster_feed_block_state_is_recomputed_when_resource_block_set_differs() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.3)));
        var bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.800 > 0.700)", bundle.getFeedBlock().get().getDescription());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.5)));
        bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.800 > 0.700), " +
                     "wine on node 1 [unknown hostname] (0.500 > 0.400)",
                     bundle.getFeedBlock().get().getDescription());
    }

    @Test
    public void cluster_feed_block_state_is_not_recomputed_when_only_resource_usage_levels_differ() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4))));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.8), usage("wine", 0.3)));
        var bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.800 > 0.700)", bundle.getFeedBlock().get().getDescription());

        // 80% -> 90%, should not trigger new state.
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.9), usage("wine", 0.3)));
        bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.800 > 0.700)", bundle.getFeedBlock().get().getDescription());
    }

    @Test
    public void cluster_feed_block_state_is_recomputed_when_usage_enters_hysteresis_range() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4)), 0.1));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.75), usage("wine", 0.3)));
        var bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.750 > 0.700)", bundle.getFeedBlock().get().getDescription());

        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.68), usage("wine", 0.3)));
        bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        // FIXME Effective limit is modified by hysteresis but due to how we check state deltas this
        // is not discovered here. Still correct in terms of what resources are blocked or not, but
        // the description is not up to date here.
        assertEquals("cheese on node 1 [unknown hostname] (0.750 > 0.700)",
                     bundle.getFeedBlock().get().getDescription());

        // Trigger an explicit recompute by adding a separate resource exhaustion
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.67), usage("wine", 0.5)));
        bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.670 > 0.600), " +
                     "wine on node 1 [unknown hostname] (0.500 > 0.400)", // Not under hysteresis
                     bundle.getFeedBlock().get().getDescription());

        // Wine usage drops beyond hysteresis range, should be unblocked immediately.
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.61), usage("wine", 0.2)));
        bundle = ctrl.getClusterStateBundle();
        assertTrue(bundle.clusterFeedIsBlocked());
        assertEquals("cheese on node 1 [unknown hostname] (0.610 > 0.600)",
                     bundle.getFeedBlock().get().getDescription());

        // Cheese now drops below hysteresis range, should be unblocked as well.
        reportResourceUsageFromNode(1, setOf(usage("cheese", 0.59), usage("wine", 0.2)));
        bundle = ctrl.getClusterStateBundle();
        assertFalse(bundle.clusterFeedIsBlocked());
    }

    @Test
    public void unavailable_nodes_are_not_considered_when_computing_feed_blocked_state() throws Exception {
        initialize(createOptions(mapOf(usage("cheese", 0.7), usage("wine", 0.4)), 0.1));
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());

        reportResourceUsageFromNode(1, State.DOWN, setOf(usage("cheese", 0.8), usage("wine", 0.5)));
        // Not blocked, node with exhaustion is marked as Down
        assertFalse(ctrl.getClusterStateBundle().clusterFeedIsBlocked());
    }

    // FIXME implicit changes in limits due to hysteresis adds spurious exhaustion remove+add node event pair

}
