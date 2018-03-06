// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author hakonhall
 * @since 5.34
 */
public class ClusterStatsAggregatorTest {

    private static class Fixture {
        private ClusterStatsAggregator aggregator;

        public Fixture(Set<Integer> distributorNodes,
                Set<Integer> contentNodes) {
            aggregator = new ClusterStatsAggregator(distributorNodes, contentNodes);
        }

        public void update(int distributorIndex, ContentClusterStatsBuilder clusterStats) {
            aggregator.updateForDistributor(distributorIndex, clusterStats.build());
        }

        public void verify(ContentClusterStatsBuilder expectedStats) {
            assertEquals(expectedStats.build(), aggregator.getAggregatedStats().getStats());
        }

        public void verify(int distributorIndex, ContentNodeStatsBuilder expectedStats) {
            assertEquals(expectedStats.build(), aggregator.getAggregatedStatsForDistributor(distributorIndex));
        }

        public boolean hasUpdatesFromAllDistributors() {
            return aggregator.getAggregatedStats().hasUpdatesFromAllDistributors();
        }

    }

    private static class FourNodesFixture extends Fixture {
        public FourNodesFixture() {
            super(distributorNodes(1, 2), contentNodes(3, 4));

            update(1, new ContentClusterStatsBuilder()
                    .add(3, "default", 10, 1)
                    .add(3, "global", 11, 2)
                    .add(4, "default", 12, 3)
                    .add(4, "global", 13, 4));
            update(2, new ContentClusterStatsBuilder()
                    .add(3, "default", 14, 5)
                    .add(3, "global", 15, 6)
                    .add(4, "default", 16, 7)
                    .add(4, "global", 17, 8));
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
        ContentClusterStatsBuilder stats = new ContentClusterStatsBuilder()
                .add(3, "default", 10, 1)
                .add(3, "global", 11, 2);
        f.update(1, stats);
        f.verify(stats);
    }

    @Test
    public void aggregator_handles_updates_to_multiple_distributors_and_content_nodes() {
        Fixture f = new FourNodesFixture();

        f.verify(new ContentClusterStatsBuilder()
                .add(3, "default", 10 + 14, 1 + 5)
                .add(3, "global", 11 + 15, 2 + 6)
                .add(4, "default", 12 + 16, 3 + 7)
                .add(4, "global", 13 + 17, 4 + 8));
    }

    @Test
    public void aggregator_handles_multiple_updates_from_same_distributor() {
        Fixture f = new Fixture(distributorNodes(1, 2), contentNodes(3));

        f.update(1, new ContentClusterStatsBuilder().add(3, "default"));
        f.verify(new ContentClusterStatsBuilder().add(3, "default"));

        f.update(2, new ContentClusterStatsBuilder().add(3, "default", 10, 1));
        f.verify(new ContentClusterStatsBuilder().addInvalid(3, "default", 10, 1));

        f.update(1, new ContentClusterStatsBuilder().add(3, "default", 11, 2));
        f.verify(new ContentClusterStatsBuilder().add(3, "default", 10 + 11, 1 + 2));

        f.update(2, new ContentClusterStatsBuilder().add(3, "default", 15, 6));
        f.verify(new ContentClusterStatsBuilder().add(3, "default", 11 + 15, 2 + 6));

        f.update(1, new ContentClusterStatsBuilder().add(3, "default", 16, 7));
        f.verify(new ContentClusterStatsBuilder().add(3, "default", 15 + 16, 6 + 7));

        f.update(2, new ContentClusterStatsBuilder().add(3, "default", 12, 3));
        f.verify(new ContentClusterStatsBuilder().add(3, "default", 16 + 12, 7 + 3));
    }

    @Test
    public void aggregator_handles_more_content_nodes_that_distributors() {
        Fixture f = new Fixture(distributorNodes(1), contentNodes(3, 4));
        ContentClusterStatsBuilder stats = new ContentClusterStatsBuilder()
                .add(3, "default", 10, 1)
                .add(4, "default", 11, 2);
        f.update(1, stats);
        f.verify(stats);
    }

    @Test
    public void aggregator_ignores_updates_to_unknown_distributor() {
        Fixture f = new Fixture(distributorNodes(1), contentNodes(3));
        final int downDistributorIndex = 2;
        f.update(downDistributorIndex, new ContentClusterStatsBuilder()
                .add(3, "default", 7, 3));
        f.verify(new ContentClusterStatsBuilder().add(3));
    }

    @Test
    public void aggregator_tracks_when_it_has_updates_from_all_distributors() {
        Fixture f = new Fixture(distributorNodes(1, 2), contentNodes(3));
        assertFalse(f.hasUpdatesFromAllDistributors());
        f.update(1, new ContentClusterStatsBuilder().add(3, "default"));
        assertFalse(f.hasUpdatesFromAllDistributors());
        f.update(1, new ContentClusterStatsBuilder().add(3, "default", 10, 1));
        assertFalse(f.hasUpdatesFromAllDistributors());
        f.update(2, new ContentClusterStatsBuilder().add(3, "default"));
        assertTrue(f.hasUpdatesFromAllDistributors());
    }

    @Test
    public void aggregator_can_provide_aggregated_stats_per_distributor() {
        Fixture f = new FourNodesFixture();

        f.verify(1, ContentNodeStatsBuilder.forNode(1)
                .add("default", 10 + 12, 1 + 3)
                .add("global", 11 + 13, 2 + 4));

        f.verify(2, ContentNodeStatsBuilder.forNode(2)
                .add("default", 14 + 16, 5 + 7)
                .add("global", 15 + 17, 6 + 8));
    }

}
