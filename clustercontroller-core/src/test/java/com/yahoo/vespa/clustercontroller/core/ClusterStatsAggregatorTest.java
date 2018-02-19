// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 * @since 5.34
 */
public class ClusterStatsAggregatorTest {

    private static class StatsBuilder {
        private final Map<Integer, Map<String, ContentNodeStats.BucketSpaceStats> > stats = new HashMap<>();

        public StatsBuilder add(int nodeIndex, String bucketSpace, long bucketsTotal, long bucketsPending) {
            return add(nodeIndex, bucketSpace, new ContentNodeStats.BucketSpaceStats(bucketsTotal, bucketsPending));
        }
        public StatsBuilder add(int nodeIndex, String bucketSpace) {
            return add(nodeIndex, bucketSpace, new ContentNodeStats.BucketSpaceStats());
        }
        public StatsBuilder add(int nodeIndex, String bucketSpace, ContentNodeStats.BucketSpaceStats bucketSpaceStats) {
            Map<String, ContentNodeStats.BucketSpaceStats> contentNodeStats = stats.get(nodeIndex);
            if (contentNodeStats == null) {
                contentNodeStats = new HashMap<>();
                stats.put(nodeIndex, contentNodeStats);
            }
            contentNodeStats.put(bucketSpace, bucketSpaceStats);
            return this;
        }
        public StatsBuilder add(int nodeIndex) {
            stats.put(nodeIndex, new HashMap<>());
            return this;
        }
        public ContentClusterStats build() {
            Map<Integer, ContentNodeStats> nodeToStatsMap = new HashMap<>();
            stats.forEach((nodeIndex, bucketSpaces) ->
                    nodeToStatsMap.put(nodeIndex, new ContentNodeStats(nodeIndex, bucketSpaces)));
            return new ContentClusterStats(nodeToStatsMap);
        }
    }

    private static class Fixture {
        private ClusterStatsAggregator aggregator;
        public Fixture(Set<Integer> distributorNodes,
                Set<Integer> contentNodes) {
            aggregator = new ClusterStatsAggregator(distributorNodes, contentNodes);
        }
        public void update(int distributorIndex, StatsBuilder clusterStats) {
            aggregator.updateForDistributor(distributorIndex, clusterStats.build());
        }
        public void verify(StatsBuilder expectedStats) {
            assertEquals(expectedStats.build(), aggregator.getAggregatedStats());
        }
    }

    private static Set<Integer> distributorNodes(Integer... indices) {
        return Sets.newHashSet(indices);
    }

    private static Set<Integer> contentNodes(Integer... indices) {
        return Sets.newHashSet(indices);
    }

    @Test
    public void aggregator_handles_updates_to_single_distributor_and_content_node() {
        Fixture f = new Fixture(distributorNodes(1), contentNodes(3));
        StatsBuilder stats = new StatsBuilder()
                .add(3, "default", 10, 1)
                .add(3, "global", 11, 2);
        f.update(1, stats);
        f.verify(stats);
    }

    @Test
    public void aggregator_handles_updates_to_multiple_distributors_and_content_nodes() {
        Fixture f = new Fixture(distributorNodes(1, 2), contentNodes(3, 4));

        f.update(1, new StatsBuilder()
                .add(3, "default", 10, 1)
                .add(3, "global", 11, 2)
                .add(4, "default", 12, 3)
                .add(4, "global", 13, 4));
        f.update(2, new StatsBuilder()
                .add(3, "default", 14, 5)
                .add(3, "global", 15, 6)
                .add(4, "default", 16, 7)
                .add(4, "global", 17, 8));
        f.verify(new StatsBuilder()
                .add(3, "default", 10 + 14, 1 + 5)
                .add(3, "global", 11 + 15, 2 + 6)
                .add(4, "default", 12 + 16, 3 + 7)
                .add(4, "global", 13 + 17, 4 + 8));
    }

    @Test
    public void aggregator_handles_multiple_updates_from_same_distributor() {
        Fixture f = new Fixture(distributorNodes(1, 2), contentNodes(3));

        f.update(1, new StatsBuilder().add(3, "default"));
        f.verify(new StatsBuilder().add(3, "default"));

        f.update(2, new StatsBuilder().add(3, "default", 10, 1));
        f.verify(new StatsBuilder().add(3, "default", 10, 1));

        f.update(1, new StatsBuilder().add(3, "default", 11, 2));
        f.verify(new StatsBuilder().add(3, "default", 10 + 11, 1 + 2));

        f.update(2, new StatsBuilder().add(3, "default", 15, 6));
        f.verify(new StatsBuilder().add(3, "default", 11 + 15, 2 + 6));

        f.update(1, new StatsBuilder().add(3, "default", 16, 7));
        f.verify(new StatsBuilder().add(3, "default", 15 + 16, 6 + 7));

        f.update(2, new StatsBuilder().add(3, "default", 12, 3));
        f.verify(new StatsBuilder().add(3, "default", 16 + 12, 7 + 3));
    }

    @Test
    public void aggregator_handles_more_content_nodes_that_distributors() {
        Fixture f = new Fixture(distributorNodes(1), contentNodes(3, 4));
        StatsBuilder stats = new StatsBuilder()
                .add(3, "default", 10, 1)
                .add(4, "default", 11, 2);
        f.update(1, stats);
        f.verify(stats);
    }

    @Test
    public void aggregator_ignores_updates_to_unknown_distributor() {
        Fixture f = new Fixture(distributorNodes(1), contentNodes(3));
        final int downDistributorIndex = 2;
        f.update(downDistributorIndex, new StatsBuilder()
                .add(3, "default", 7, 3));
        f.verify(new StatsBuilder().add(3));
    }

}
