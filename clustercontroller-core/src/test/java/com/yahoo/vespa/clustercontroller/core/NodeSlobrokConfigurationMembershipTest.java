// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.state.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
@Timeout(30)
public class NodeSlobrokConfigurationMembershipTest extends FleetControllerTest {

    private final Timer timer = new FakeTimer();
    private final Set<Integer> nodeIndices = asIntSet(0, 1, 2, 3);
    private final int foreignNodeIndex = 6;

    private FleetControllerOptions setUpClusterWithForeignNode(Set<Integer> validIndices) throws Exception {
        Set<ConfiguredNode> configuredNodes = asConfiguredNodes(validIndices);
        FleetControllerOptions.Builder options = optionsForConfiguredNodes(configuredNodes);
        setUpFleetController(timer, options);
        Set<Integer> nodesWithStranger = new TreeSet<>(validIndices);
        nodesWithStranger.add(foreignNodeIndex);
        setUpVdsNodes(timer, false, nodesWithStranger);
        return options.build();
    }

    private FleetControllerOptions.Builder optionsForConfiguredNodes(Set<ConfiguredNode> configuredNodes) {
        return defaultOptions("mycluster", configuredNodes)
                .setMaxSlobrokDisconnectGracePeriod(60 * 1000)
                .setNodeStateRequestTimeoutMS(10000 * 60 * 1000)
                .setMaxTransitionTime(NodeType.DISTRIBUTOR, 0)
                .setMaxTransitionTime(NodeType.STORAGE, 0);
    }

    @Test
    void testSlobrokNodeOutsideConfiguredIndexSetIsNotIncludedInCluster() throws Exception {
        setUpClusterWithForeignNode(nodeIndices);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 storage:4", asIntSet(foreignNodeIndex), timer);
    }

    @Test
    void testNodeSetReconfigurationForcesFreshSlobrokFetch() throws Exception {
        var options = setUpClusterWithForeignNode(nodeIndices);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 storage:4", asIntSet(foreignNodeIndex), timer);

        // If we get a configuration with the node present, we have to accept it into
        // cluster. If we do not re-fetch state from slobrok we risk racing
        nodeIndices.add(foreignNodeIndex);
        var a = FleetControllerOptions.Builder.copy(options);
        a.setNodes(asConfiguredNodes(nodeIndices));
        options = a.build();
        fleetController().updateOptions(options);
        // Need to treat cluster as having 6 nodes due to ideal state algo semantics.
        // Note that we do not use subsetWaiter here since we want node 6 included.
        waitForState("version:\\d+ distributor:7 .4.s:d .5.s:d storage:7 .4.s:d .5.s:d");
    }

    @Test
    void test_removed_retired_node_is_not_included_in_state() throws Exception {
        Set<ConfiguredNode> configuredNodes = asConfiguredNodes(nodeIndices);
        FleetControllerOptions.Builder builder = optionsForConfiguredNodes(configuredNodes);
        options = setUpFleetController(timer, builder);
        setUpVdsNodes(timer, false, nodeIndices);

        waitForState("version:\\d+ distributor:4 storage:4");

        // Update options with 1 node config-retired
        assertTrue(configuredNodes.remove(new ConfiguredNode(0, false)));
        configuredNodes.add(new ConfiguredNode(0, true));

        builder = FleetControllerOptions.Builder.copy(options);
        builder.setNodes(configuredNodes);
        options = builder.build();
        fleetController().updateOptions(options);

        waitForState("version:\\d+ distributor:4 storage:4 .0.s:r");

        // Now remove the retired node entirely from config
        assertTrue(configuredNodes.remove(new ConfiguredNode(0, true)));
        builder = FleetControllerOptions.Builder.copy(options);
        builder.setNodes(configuredNodes);
        options = builder.build();
        fleetController().updateOptions(options);

        // The previously retired node should now be marked as down, as it no longer
        // exists from the point of view of the content cluster. We have to use a subset
        // state waiter, as the controller will not send the new state to node 0.
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 .0.s:d storage:4 .0.s:d", asIntSet(0), timer);

        // Ensure it remains down for subsequent cluster state versions as well.
        nodes.get(3).disconnect();
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 .0.s:d storage:4 .0.s:d .1.s:d", asIntSet(0, 1), timer);
    }

}
