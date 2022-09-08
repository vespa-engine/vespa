// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(CleanupZookeeperLogsOnSuccess.class)
public class GroupAutoTakedownLiveConfigTest extends FleetControllerTest {

    private static FleetControllerOptions.Builder createOptions(DistributionBuilder.GroupBuilder groupBuilder, double minNodeRatio) {
        return defaultOptions("mycluster")
                .setStorageDistribution(DistributionBuilder.forHierarchicCluster(groupBuilder))
                .setNodes(new HashSet<>(DistributionBuilder.buildConfiguredNodes(groupBuilder.totalNodeCount())))
                .setMinNodeRatioPerGroup(minNodeRatio)
                .setMaxTransitionTime(NodeType.DISTRIBUTOR, 0)
                .setMaxTransitionTime(NodeType.STORAGE, 0);
    }

    private void updateConfigLive(FleetControllerOptions newOptions) {
        fleetController().updateOptions(newOptions);
    }

    private void reconfigureWithMinNodeRatio(FleetControllerOptions options, double minNodeRatio) {
        FleetControllerOptions.Builder newOptions = FleetControllerOptions.Builder.copy(options);
        newOptions.setMinNodeRatioPerGroup(minNodeRatio);
        updateConfigLive(newOptions.build());
    }

    private void reconfigureWithDistribution(FleetControllerOptions options, DistributionBuilder.GroupBuilder groupBuilder) {
        FleetControllerOptions.Builder builder =
                FleetControllerOptions.Builder.copy(options)
                                              .setNodes(new HashSet<>(DistributionBuilder.buildConfiguredNodes(groupBuilder.totalNodeCount())))
                                              .setStorageDistribution(DistributionBuilder.forHierarchicCluster(groupBuilder));
        updateConfigLive(builder.build());
    }

    private FleetControllerOptions setUp3x3ClusterWithMinNodeRatio(double minNodeRatio) throws Exception {
        FleetControllerOptions.Builder options = createOptions(DistributionBuilder.withGroups(3).eachWithNodeCount(3), minNodeRatio);
        setUpFleetController(true, options);
        setUpVdsNodes(true, false, 9);
        waitForState("version:\\d+ distributor:9 storage:9");
        return options.build();
    }

    private void takeDownContentNode(int index) {
        // nodes list contains both distributors and storage nodes, with distributors
        // in even positions and storage nodes in odd positions.
        final int arrayIndex = index*2 + 1;
        assertFalse(nodes.get(arrayIndex).isDistributor());
        nodes.get(arrayIndex).disconnect();
    }

    @Test
    void bootstrap_min_ratio_option_is_propagated_to_group_availability_logic() throws Exception {
        setUp3x3ClusterWithMinNodeRatio(0.67);
        takeDownContentNode(0);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .0.s:d .1.s:d .2.s:d", asIntSet(0));
    }

    @Test
    void min_ratio_live_reconfig_immediately_takes_effect() throws Exception {
        // Initially, arbitrarily many nodes may be down in a group.
        var options = setUp3x3ClusterWithMinNodeRatio(0.0);
        takeDownContentNode(3);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d", asIntSet(3));

        reconfigureWithMinNodeRatio(options, 0.67);
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d .4.s:d .5.s:d", asIntSet(3));

        reconfigureWithMinNodeRatio(options, 0.0);
        // Aaaand back up again!
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .3.s:d", asIntSet(3));
    }

    @Test
    void live_distribution_config_changes_trigger_cluster_state_change() throws Exception {
        var options = setUp3x3ClusterWithMinNodeRatio(0.65);
        takeDownContentNode(6);

        // Not enough nodes down to trigger group take-down yet
        waitForStateExcludingNodeSubset("version:\\d+ distributor:9 storage:9 .6.s:d", asIntSet(6));
        // Removing a node from the same group as node 6 will dip it under the configured threshold,
        // taking down the entire group. In this case we configure out node 8.
        reconfigureWithDistribution(options, DistributionBuilder.withGroupNodes(3, 3, 2));
        waitForStateExcludingNodeSubset("version:\\d+ distributor:8 storage:6", asIntSet(6, 8));
    }
}
