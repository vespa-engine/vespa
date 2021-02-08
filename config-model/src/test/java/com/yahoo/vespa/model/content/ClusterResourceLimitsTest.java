// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author geirst
 */
public class ClusterResourceLimitsTest {

    private static class Fixture {
        private final boolean enableFeedBlockInDistributor;
        private final ResourceLimits.Builder ctrlBuilder = new ResourceLimits.Builder();
        private final ResourceLimits.Builder nodeBuilder = new ResourceLimits.Builder();

        public Fixture() {
            this.enableFeedBlockInDistributor = false;
        }

        public Fixture(boolean enableFeedBlockInDistributor) {
            this.enableFeedBlockInDistributor = enableFeedBlockInDistributor;
        }

        public Fixture ctrlDisk(double limit) {
            ctrlBuilder.setDiskLimit(limit);
            return this;
        }
        public Fixture ctrlMemory(double limit) {
            ctrlBuilder.setMemoryLimit(limit);
            return this;
        }
        public Fixture nodeDisk(double limit) {
            nodeBuilder.setDiskLimit(limit);
            return this;
        }
        public Fixture nodeMemory(double limit) {
            nodeBuilder.setMemoryLimit(limit);
            return this;
        }
        public ClusterResourceLimits build() {
            var builder = new ClusterResourceLimits.Builder(enableFeedBlockInDistributor);
            builder.setClusterControllerBuilder(ctrlBuilder);
            builder.setContentNodeBuilder(nodeBuilder);
            return builder.build();
        }
    }

    @Test
    public void content_node_limits_are_derived_from_cluster_controller_limits_if_not_set() {
        assertLimits(0.4, 0.7, 0.7, 0.85,
                new Fixture().ctrlDisk(0.4).ctrlMemory(0.7));
        assertLimits(0.4, null, 0.7, null,
                new Fixture().ctrlDisk(0.4));
        assertLimits(null, 0.7, null, 0.85,
                new Fixture().ctrlMemory(0.7));


        assertLimits(0.4, 0.7, 0.7, 0.85,
                new Fixture(true).ctrlDisk(0.4).ctrlMemory(0.7));
        assertLimits(0.4, 0.8, 0.7, 0.9,
                new Fixture(true).ctrlDisk(0.4));
        assertLimits(0.8, 0.7, 0.9, 0.85,
                new Fixture(true).ctrlMemory(0.7));
    }

    @Test
    public void content_node_limits_can_be_set_explicit() {
        assertLimits(0.4, 0.7, 0.9, 0.95,
                new Fixture().ctrlDisk(0.4).ctrlMemory(0.7).nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.4, null, 0.95, null,
                new Fixture().ctrlDisk(0.4).nodeDisk(0.95));
        assertLimits(null, 0.7, null, 0.95,
                new Fixture().ctrlMemory(0.7).nodeMemory(0.95));

        assertLimits(0.4, 0.7, 0.9, 0.95,
                new Fixture(true).ctrlDisk(0.4).ctrlMemory(0.7).nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.4, 0.8, 0.95, 0.9,
                new Fixture(true).ctrlDisk(0.4).nodeDisk(0.95));
        assertLimits(0.8, 0.7, 0.9, 0.95,
                new Fixture(true).ctrlMemory(0.7).nodeMemory(0.95));
    }

    @Test
    public void cluster_controller_limits_are_equal_to_content_node_limits_minus_one_percent_if_not_set() {
        assertLimits(0.89, 0.94, 0.9, 0.95,
                new Fixture().nodeDisk(0.9).nodeMemory(0.95));
        assertLimits(0.89, null, 0.9, null,
                new Fixture().nodeDisk(0.9));
        assertLimits(null, 0.94, null, 0.95,
                new Fixture().nodeMemory(0.95));
        assertLimits(null, 0.0, null, 0.005,
                new Fixture().nodeMemory(0.005));

        assertLimits(0.89, 0.94, 0.9, 0.95,
                new Fixture(true).nodeDisk(0.9).nodeMemory(0.95));
    }

    @Test
    public void limits_are_derived_from_the_other_if_not_set() {
        assertLimits(0.6, 0.94, 0.8, 0.95,
                new Fixture().ctrlDisk(0.6).nodeMemory(0.95));
        assertLimits(0.89, 0.7, 0.9, 0.85,
                new Fixture().ctrlMemory(0.7).nodeDisk(0.9));
    }

    @Test
    public void default_resource_limits_when_feed_block_is_enabled_in_distributor() {
        assertLimits(0.8, 0.8, 0.9, 0.9,
                new Fixture(true));
    }

    private void assertLimits(Double expCtrlDisk, Double expCtrlMemory, Double expNodeDisk, Double expNodeMemory, Fixture f) {
        var limits = f.build();
        assertLimits(expCtrlDisk, expCtrlMemory, limits.getClusterControllerLimits());
        assertLimits(expNodeDisk, expNodeMemory, limits.getContentNodeLimits());
    }

    private void assertLimits(Double expDisk, Double expMemory, ResourceLimits limits) {
        assertLimit(expDisk, limits.getDiskLimit());
        assertLimit(expMemory, limits.getMemoryLimit());
    }

    private void assertLimit(Double expLimit, Optional<Double> actLimit) {
        if (expLimit == null) {
            assertFalse(actLimit.isPresent());
        } else {
            assertEquals(expLimit, actLimit.get(), 0.00001);
        }
    }

}
