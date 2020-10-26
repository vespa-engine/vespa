// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.ClusterState;
import org.junit.Test;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class GroupAvailabilityCalculatorTest {

    private static ClusterState clusterState(String state) {
        try {
            return new ClusterState(state);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static GroupAvailabilityCalculator calcForFlatCluster(int nodeCount, double minNodeRatioPerGroup) {
        return GroupAvailabilityCalculator.builder()
                .withDistribution(DistributionBuilder.forFlatCluster(nodeCount))
                .withMinNodeRatioPerGroup(minNodeRatioPerGroup)
                .build();
    }

    private static GroupAvailabilityCalculator calcForHierarchicCluster(
            DistributionBuilder.GroupBuilder rootGroup,
            final double minNodeRatioPerGroup)
    {
        return GroupAvailabilityCalculator.builder()
                .withDistribution(DistributionBuilder.forHierarchicCluster(rootGroup))
                .withMinNodeRatioPerGroup(minNodeRatioPerGroup)
                .build();
    }

    private static Set<Integer> indices(Integer... idx) {
        Set<Integer> indices = new HashSet<>();
        Collections.addAll(indices, idx);
        return indices;
    }

    private static Set<Integer> emptySet() { return indices(); }

    @Test
    public void flat_cluster_does_not_implicitly_take_down_nodes() {
        GroupAvailabilityCalculator calc = calcForFlatCluster(5, 0.99);

        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:5 storage:5 .1.s:d .2.s:d")), equalTo(emptySet()));

    }

    @Test
    public void group_node_down_edge_implicitly_marks_down_rest_of_nodes_in_group() {
        // 3 groups of 2 nodes, take down node #4 (1st node in last group). Since we require
        // at least 51% of group capacity to be available, implicitly take down the last group
        // entirely.
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.51);

        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:6 .4.s:d")), equalTo(indices(5)));
    }

    // Setting 50% as min ratio in a group with 2 nodes should let group be up if
    // one node goes down.
    @Test
    public void min_ratio_per_group_is_closed_interval() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.50);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:6 .4.s:d")), equalTo(emptySet()));
    }

    @Test
    public void retired_node_is_counted_as_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:6 .1.s:r")), equalTo(indices(0)));
    }

    @Test
    public void initializing_node_not_counted_as_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:6 .4.s:i")), equalTo(emptySet()));
    }

    @Test
    public void maintenance_node_not_counted_as_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:6 .4.s:m")), equalTo(emptySet()));
    }

    @Test
    public void existing_maintenance_node_not_implicitly_downed_when_group_taken_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:9 storage:9 .4.s:m .5.s:d")), equalTo(indices(3))); // _not_ {3, 4}
    }

    @Test
    public void existing_retired_node_not_implicitly_downed_when_group_taken_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(3), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:9 storage:9 .4.s:r .5.s:d")), equalTo(indices(3))); // _not_ {3, 4}
    }

    @Test
    public void down_to_down_edge_keeps_group_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(2).eachWithNodeCount(4), 0.76);

        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8 .1.s:d")), equalTo(indices(0, 2, 3)));

        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8 .1.s:d .2.s:d")), equalTo(indices(0, 3)));
    }

    // Cluster state representations "prune" downed nodes at the end of the state,
    // causing "storage:6 .5.s:d" to be reduced to "storage:5". This still implies a
    // node is down according to the distribution config and must be handled as such.
    @Test
    public void implicitly_downed_node_at_state_end_is_counted_as_explicitly_down() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(3).eachWithNodeCount(2), 0.99);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:6 storage:5")), equalTo(indices(4)));
    }

    @Test
    public void non_uniform_group_sizes_are_supported() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroupNodes(1, 2, 3, 4), 0.67);

        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:10")), equalTo(emptySet()));
        // Group 0 has only 1 node and should not cause any other nodes to be taken down
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:10 .0.s:d")), equalTo(emptySet()));
        // Too little availability in group 1
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:10 .1.s:d")), equalTo(indices(2)));
        // Too little availability in group 2
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:10 .3.s:d")), equalTo(indices(4, 5)));
        // Group 4 has 75% availability (>= 67%), so no auto take-down there
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:10 .7.s:d")), equalTo(emptySet()));
        // Drop group 4 availability to 50%; it should now be taken down entirely
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:10 storage:9 .7.s:d")), equalTo(indices(6, 8)));
    }

    @Test
    public void min_ratio_of_zero_never_takes_down_groups_implicitly() {
        GroupAvailabilityCalculator calc = calcForHierarchicCluster(
                DistributionBuilder.withGroups(2).eachWithNodeCount(4), 0.0);
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8")), equalTo(emptySet()));
        // 1 down in each group
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8 .0.s:d .4.s:d")), equalTo(emptySet()));
        // 2 down in each group
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8 .0.s:d .1.s:d .4.s:d .5.s:d")), equalTo(emptySet()));
        // 3 down in each group
        assertThat(calc.nodesThatShouldBeDown(clusterState(
                "distributor:8 storage:8 .0.s:d .1.s:d .2.s:d .4.s:d .5.s:d .6.s:d")), equalTo(emptySet()));
    }

}
