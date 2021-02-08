// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.cluster.DomResourceLimitsBuilder;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Class tracking the feed block resource limits for a content cluster.
 *
 * This includes the limits used by the cluster controller and the content nodes (proton).
 *
 * @author geirst
 */
public class ClusterResourceLimits {

    private final ResourceLimits clusterControllerLimits;
    private final ResourceLimits contentNodeLimits;

    private ClusterResourceLimits(Builder builder) {
        clusterControllerLimits = builder.ctrlBuilder.build();
        contentNodeLimits = builder.nodeBuilder.build();
    }

    public ResourceLimits getClusterControllerLimits() {
        return clusterControllerLimits;
    }

    public ResourceLimits getContentNodeLimits() {
        return contentNodeLimits;
    }

    public static class Builder {

        private final boolean enableFeedBlockInDistributor;
        private ResourceLimits.Builder ctrlBuilder = new ResourceLimits.Builder();
        private ResourceLimits.Builder nodeBuilder = new ResourceLimits.Builder();

        public Builder(boolean enableFeedBlockInDistributor) {
            this.enableFeedBlockInDistributor = enableFeedBlockInDistributor;
        }

        public ClusterResourceLimits build(ModelElement clusterElem) {
            ModelElement tuningElem = clusterElem.childByPath("tuning");
            if (tuningElem != null) {
                ctrlBuilder = DomResourceLimitsBuilder.createBuilder(tuningElem);
            }

            ModelElement protonElem = clusterElem.childByPath("engine.proton");
            if (protonElem != null) {
                nodeBuilder = DomResourceLimitsBuilder.createBuilder(protonElem);
            }

            deriveLimits();
            return new ClusterResourceLimits(this);
        }

        public void setClusterControllerBuilder(ResourceLimits.Builder builder) {
            ctrlBuilder = builder;
        }

        public void setContentNodeBuilder(ResourceLimits.Builder builder) {
            nodeBuilder = builder;
        }

        public ClusterResourceLimits build() {
            deriveLimits();
            return new ClusterResourceLimits(this);
        }

        private void deriveLimits() {
            if (enableFeedBlockInDistributor) {
                // This also ensures that content nodes limits are derived according to the formula in calcContentNodeLimit().
                considerSettingDefaultClusterControllerLimit(ctrlBuilder.getDiskLimit(), nodeBuilder.getDiskLimit(), ctrlBuilder::setDiskLimit);
                considerSettingDefaultClusterControllerLimit(ctrlBuilder.getMemoryLimit(), nodeBuilder.getMemoryLimit(), ctrlBuilder::setMemoryLimit);
            }

            deriveClusterControllerLimit(ctrlBuilder.getDiskLimit(), nodeBuilder.getDiskLimit(), ctrlBuilder::setDiskLimit);
            deriveClusterControllerLimit(ctrlBuilder.getMemoryLimit(), nodeBuilder.getMemoryLimit(), ctrlBuilder::setMemoryLimit);

            deriveContentNodeLimit(nodeBuilder.getDiskLimit(), ctrlBuilder.getDiskLimit(), nodeBuilder::setDiskLimit);
            deriveContentNodeLimit(nodeBuilder.getMemoryLimit(), ctrlBuilder.getMemoryLimit(), nodeBuilder::setMemoryLimit);
        }

        private void considerSettingDefaultClusterControllerLimit(Optional<Double> clusterControllerLimit,
                                                                  Optional<Double> contentNodeLimit,
                                                                  Consumer<Double> setter) {
            // TODO: remove this when feed block in distributor is default enabled.
            if (!clusterControllerLimit.isPresent() && !contentNodeLimit.isPresent()) {
                setter.accept(0.8);
            }
        }

        private void deriveClusterControllerLimit(Optional<Double> clusterControllerLimit,
                                                  Optional<Double> contentNodeLimit,
                                                  Consumer<Double> setter) {
            if (!clusterControllerLimit.isPresent()) {
                contentNodeLimit.ifPresent(limit ->
                        // TODO: emit warning when feed block in distributor is default enabled.
                        setter.accept(Double.max(0.0, (limit - 0.01))));
            }
        }

        private void deriveContentNodeLimit(Optional<Double> contentNodeLimit,
                                            Optional<Double> clusterControllerLimit,
                                            Consumer<Double> setter) {
            if (!contentNodeLimit.isPresent()) {
                clusterControllerLimit.ifPresent(limit ->
                        setter.accept(calcContentNodeLimit(limit)));
            }
        }

        private double calcContentNodeLimit(double clusterControllerLimit) {
            // Note that validation in the range [0.0-1.0] is handled by the rnc schema.
            return clusterControllerLimit + ((1.0 - clusterControllerLimit) / 2);
        }

    }

}
