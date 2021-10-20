// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertTrue;

public class NodeSlobrokConfigurationMembershipTest extends FleetControllerTest {

    private final Set<Integer> nodeIndices = asIntSet(0, 1, 2, 3);
    private final int foreignNode = 6;

    private void setUpClusterWithForeignNode(Set<Integer> validIndices, final int foreignNodeIndex) throws Exception {
        final Set<ConfiguredNode> configuredNodes = asConfiguredNodes(validIndices);
        FleetControllerOptions options = optionsForConfiguredNodes(configuredNodes);
        setUpFleetController(true, options);
        Set<Integer> nodesWithStranger = new TreeSet<>(validIndices);
        nodesWithStranger.add(foreignNodeIndex);
        setUpVdsNodes(true, new DummyVdsNodeOptions(), false, nodesWithStranger);
    }

    private FleetControllerOptions optionsForConfiguredNodes(Set<ConfiguredNode> configuredNodes) {
        FleetControllerOptions options = defaultOptions("mycluster", configuredNodes);
        options.maxSlobrokDisconnectGracePeriod = 60 * 1000;
        options.nodeStateRequestTimeoutMS = 10000 * 60 * 1000;
        options.maxTransitionTime = transitionTimes(0);
        return options;
    }

    @Test
    public void testSlobrokNodeOutsideConfiguredIndexSetIsNotIncludedInCluster() throws Exception {
        setUpClusterWithForeignNode(nodeIndices, foreignNode);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 storage:4", asIntSet(foreignNode));
    }

    @Test
    public void testNodeSetReconfigurationForcesFreshSlobrokFetch() throws Exception {
        setUpClusterWithForeignNode(nodeIndices, foreignNode);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 storage:4", asIntSet(foreignNode));

        // If we get a configuration with the node present, we have to accept it into
        // cluster. If we do not re-fetch state from slobrok we risk racing
        nodeIndices.add(foreignNode);
        options.nodes = asConfiguredNodes(nodeIndices);
        fleetController.updateOptions(options, 0);
        // Need to treat cluster as having 6 nodes due to ideal state algo semantics.
        // Note that we do not use subsetWaiter here since we want node 6 included.
        waitForState("version:\\d+ distributor:7 .4.s:d .5.s:d storage:7 .4.s:d .5.s:d");
    }

    @Test
    public void test_removed_retired_node_is_not_included_in_state() throws Exception {
        final Set<ConfiguredNode> configuredNodes = asConfiguredNodes(nodeIndices);
        FleetControllerOptions options = optionsForConfiguredNodes(configuredNodes);
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions(), false, nodeIndices);

        waitForState("version:\\d+ distributor:4 storage:4");

        // Update options with 1 node config-retired
        assertTrue(configuredNodes.remove(new ConfiguredNode(0, false)));
        configuredNodes.add(new ConfiguredNode(0, true));
        options.nodes = configuredNodes;
        fleetController.updateOptions(options, 0);

        waitForState("version:\\d+ distributor:4 storage:4 .0.s:r");

        // Now remove the retired node entirely from config
        assertTrue(configuredNodes.remove(new ConfiguredNode(0, true)));
        fleetController.updateOptions(options, 0);

        // The previously retired node should now be marked as down, as it no longer
        // exists from the point of view of the content cluster. We have to use a subset
        // state waiter, as the controller will not send the new state to node 0.
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 .0.s:d storage:4 .0.s:d", asIntSet(0));

        // Ensure it remains down for subsequent cluster state versions as well.
        nodes.get(3).disconnect();
        waitForStateExcludingNodeSubset("version:\\d+ distributor:4 .0.s:d storage:4 .0.s:d .1.s:d", asIntSet(0, 1));
    }

}
