// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.application.api.DeployLogger;
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
        private final boolean hostedVespa;
        private final boolean throwIfSpecified;
        private final DeployLogger deployLogger;
        private final double resourceLimitDisk;
        private final double resourceLimitMemory;

        private ResourceLimits.Builder ctrlBuilder = new ResourceLimits.Builder();
        private ResourceLimits.Builder nodeBuilder = new ResourceLimits.Builder();

        public Builder(boolean enableFeedBlockInDistributor, boolean hostedVespa, boolean throwIfSpecified,
                       DeployLogger deployLogger, double resourceLimitDisk, double resourceLimitMemory) {
            this.enableFeedBlockInDistributor = enableFeedBlockInDistributor;
            this.hostedVespa = hostedVespa;
            this.throwIfSpecified = throwIfSpecified;
            this.deployLogger = deployLogger;
            this.resourceLimitDisk = resourceLimitDisk;
            this.resourceLimitMemory = resourceLimitMemory;
            verifyLimits(resourceLimitDisk, resourceLimitMemory);
        }

        public ClusterResourceLimits build(ModelElement clusterElem) {
            ctrlBuilder = createBuilder(clusterElem.childByPath("tuning"));
            nodeBuilder = createBuilder(clusterElem.childByPath("engine.proton"));

            deriveLimits();
            return new ClusterResourceLimits(this);
        }

        private ResourceLimits.Builder createBuilder(ModelElement element) {
            return element == null
                    ? new ResourceLimits.Builder()
                    : DomResourceLimitsBuilder.createBuilder(element, hostedVespa, throwIfSpecified, deployLogger);
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
                considerSettingDefaultClusterControllerLimit(ctrlBuilder.getDiskLimit(),
                                                             nodeBuilder.getDiskLimit(),
                                                             ctrlBuilder::setDiskLimit,
                                                             resourceLimitDisk);
                considerSettingDefaultClusterControllerLimit(ctrlBuilder.getMemoryLimit(),
                                                             nodeBuilder.getMemoryLimit(),
                                                             ctrlBuilder::setMemoryLimit,
                                                             resourceLimitMemory);
            }

            deriveClusterControllerLimit(ctrlBuilder.getDiskLimit(), nodeBuilder.getDiskLimit(), ctrlBuilder::setDiskLimit);
            deriveClusterControllerLimit(ctrlBuilder.getMemoryLimit(), nodeBuilder.getMemoryLimit(), ctrlBuilder::setMemoryLimit);

            deriveContentNodeLimit(nodeBuilder.getDiskLimit(), ctrlBuilder.getDiskLimit(), nodeBuilder::setDiskLimit);
            deriveContentNodeLimit(nodeBuilder.getMemoryLimit(), ctrlBuilder.getMemoryLimit(), nodeBuilder::setMemoryLimit);
        }

        private void considerSettingDefaultClusterControllerLimit(Optional<Double> clusterControllerLimit,
                                                                  Optional<Double> contentNodeLimit,
                                                                  Consumer<Double> setter,
                                                                  double resourceLimit) {
            // TODO: remove this when feed block in distributor is default enabled.
            if (clusterControllerLimit.isEmpty() && contentNodeLimit.isEmpty()) {
                setter.accept(resourceLimit);
            }
        }

        private void deriveClusterControllerLimit(Optional<Double> clusterControllerLimit,
                                                  Optional<Double> contentNodeLimit,
                                                  Consumer<Double> setter) {
            if (clusterControllerLimit.isEmpty()) {
                contentNodeLimit.ifPresent(limit ->
                        // TODO: emit warning when feed block in distributor is default enabled.
                        setter.accept(Double.max(0.0, (limit - 0.01))));
            }
        }

        private void deriveContentNodeLimit(Optional<Double> contentNodeLimit,
                                            Optional<Double> clusterControllerLimit,
                                            Consumer<Double> setter) {
            if (contentNodeLimit.isEmpty()) {
                clusterControllerLimit.ifPresent(limit ->
                        setter.accept(calcContentNodeLimit(limit)));
            }
        }

        private double calcContentNodeLimit(double clusterControllerLimit) {
            return clusterControllerLimit + ((1.0 - clusterControllerLimit) / 2);
        }


        private void verifyLimits(double resourceLimitDisk, double resourceLimitMemory) {
            verifyLimitInRange(resourceLimitDisk, "disk");
            verifyLimitInRange(resourceLimitMemory, "memory");
        }

        private void verifyLimitInRange(double limit, String type) {
            String message = "Resource limit for " + type + " is set to illegal value " + limit +
                       ", but must be in the range [0.0, 1.0]";
            if (limit < 0.0)
                throw new IllegalArgumentException(message);

            if (limit > 1.0)
                throw new IllegalArgumentException(message);
        }

    }

}
