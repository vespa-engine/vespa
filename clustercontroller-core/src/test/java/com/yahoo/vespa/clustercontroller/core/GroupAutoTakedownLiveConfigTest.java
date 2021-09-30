// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertFalse;

public class GroupAutoTakedownLiveConfigTest extends FleetControllerTest {

    private long mockConfigGeneration = 1;

    private static FleetControllerOptions createOptions(DistributionBuilder.GroupBuilder groupBuilder, double minNodeRatio) {
        FleetControllerOptions options = defaultOptions("mycluster");
        options.setStorageDistribution(DistributionBuilder.forHierarchicCluster(groupBuilder));
        options.nodes = new HashSet<>(DistributionBuilder.buildConfiguredNodes(groupBuilder.totalNodeCount()));
        options.minNodeRatioPerGroup = minNodeRatio;
        options.maxTransitionTime = transitionTimes(0);
        return options;
    }

    private void updateConfigLive(FleetControllerOptions newOptions) {
        ++mockConfigGeneration;
        this.fleetController.updateOptions(newOptions, mockConfigGeneration);
    }

    private void reconfigureWithMinNodeRatio(double minNodeRatio) {
        FleetControllerOptions newOptions = this.options.clone();
        newOptions.minNodeRatioPerGroup = minNodeRatio;
        updateConfigLive(newOptions);
    }

    private void reconfigureWithDistribution(DistributionBuilder.GroupBuilder groupBuilder) {
        FleetControllerOptions newOptions = this.options.clone();
        newOptions.nodes = new HashSet<>(DistributionBuilder.buildConfiguredNodes(groupBuilder.totalNodeCount()));
        newOptions.storageDistribution = DistributionBuilder.forHierarchicCluster(groupBuilder);
        updateConfigLive(newOptions);
    }

    private void setUp3x3ClusterWithMinNodeRatio(double minNodeRatio) throws Exception {
        FleetControllerOptions options = createOptions(
                        DistributionBuilder.withGroups(3).eachWithNodeCount(3),
                minNodeRatio);
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions(), false, 9);
        waitForState("version:\\d+ distributor:9 storage:9");
    }

    private void takeDownContentNode(int index) {
        // nodes list contains both distributors and storage nodes, with distributors
        // in even positions and storage nodes in odd positions.
        final int arrayIndex = index*2 + 1;
        assertFalse(nodes.get(arrayIndex).isDistributor());
        nodes.get(arrayIndex).disconnect();
    }

    @Test
    public void bootstrap_min_ratio_option_is_propagated_to_group_availability_logic() throws Exception {
        setUp3x3ClusterWithMinNodeRatio(0.67);
        takeDownContentNode(0);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .0.s:d .1.s:d .2.s:d", asIntSet(0));
    }

    @Test
    public void min_ratio_live_reconfig_immediately_takes_effect() throws Exception {
        // Initially, arbitrarily many nodes may be down in a group.
        setUp3x3ClusterWithMinNodeRatio(0.0);
        takeDownContentNode(3);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d", asIntSet(3));

        reconfigureWithMinNodeRatio(0.67);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d .4.s:d .5.s:d", asIntSet(3));

        reconfigureWithMinNodeRatio(0.0);
        // Aaaand back up again!
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d", asIntSet(3));
    }

    @Test
    public void live_distribution_config_changes_trigger_cluster_state_change() throws Exception {
        setUp3x3ClusterWithMinNodeRatio(0.65);
        takeDownContentNode(6);

        // Not enough nodes down to trigger group take-down yet
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .6.s:d", asIntSet(6));
        // Removing a node from the same group as node 6 will dip it under the configured threshold,
        // taking down the entire group. In this case we configure out node 8.
        reconfigureWithDistribution(DistributionBuilder.withGroupNodes(3, 3, 2));
        waitForStateExcludingNodeSubset("version:\\d+ distributor:8 storage:6", asIntSet(6, 8));
    }
}
