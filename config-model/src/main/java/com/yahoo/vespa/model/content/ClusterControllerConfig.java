// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.utils.Duration;
import org.w3c.dom.Element;

/**
 * Config generation for common parameters for all fleet controllers.
 *
 * TODO: Author
 */
public class ClusterControllerConfig extends AbstractConfigProducer implements FleetcontrollerConfig.Producer {

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<ClusterControllerConfig> {
        String clusterName;
        ModelElement clusterElement;

        public Builder(String clusterName, ModelElement clusterElement) {
            this.clusterName = clusterName;
            this.clusterElement = clusterElement;
        }

        @Override
        protected ClusterControllerConfig doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
            ModelElement tuning = null;

            ModelElement clusterTuning = clusterElement.child("tuning");
            Integer bucketSplittingMinimumBits = null;
            Double minNodeRatioPerGroup = null;
            if (clusterTuning != null) {
                tuning = clusterTuning.child("cluster-controller");
                minNodeRatioPerGroup = clusterTuning.childAsDouble("min-node-ratio-per-group");
                bucketSplittingMinimumBits = clusterTuning.childAsInteger("bucket-splitting.minimum-bits");
            }

            if (tuning != null) {
                return new ClusterControllerConfig(ancestor, clusterName,
                        tuning.childAsDuration("init-progress-time"),
                        tuning.childAsDuration("transition-time"),
                        tuning.childAsLong("max-premature-crashes"),
                        tuning.childAsDuration("stable-state-period"),
                        tuning.childAsDouble("min-distributor-up-ratio"),
                        tuning.childAsDouble("min-storage-up-ratio"),
                        bucketSplittingMinimumBits,
                        minNodeRatioPerGroup
                );
            } else {
                return new ClusterControllerConfig(ancestor, clusterName, null, null, null, null, null, null,
                        bucketSplittingMinimumBits, minNodeRatioPerGroup);
            }
        }
    }

    String clusterName;
    Duration initProgressTime;
    Duration transitionTime;
    Long maxPrematureCrashes;
    Duration stableStateTimePeriod;
    Double minDistributorUpRatio;
    Double minStorageUpRatio;
    Integer minSplitBits;
    private Double minNodeRatioPerGroup;

    // TODO refactor; too many args
    private ClusterControllerConfig(AbstractConfigProducer parent,
                                    String clusterName,
                                    Duration initProgressTime,
                                    Duration transitionTime,
                                    Long maxPrematureCrashes,
                                    Duration stableStateTimePeriod,
                                    Double minDistributorUpRatio,
                                    Double minStorageUpRatio,
                                    Integer minSplitBits,
                                    Double minNodeRatioPerGroup) {
        super(parent, "fleetcontroller");

        this.clusterName = clusterName;
        this.initProgressTime = initProgressTime;
        this.transitionTime = transitionTime;
        this.maxPrematureCrashes = maxPrematureCrashes;
        this.stableStateTimePeriod = stableStateTimePeriod;
        this.minDistributorUpRatio = minDistributorUpRatio;
        this.minStorageUpRatio = minStorageUpRatio;
        this.minSplitBits = minSplitBits;
        this.minNodeRatioPerGroup = minNodeRatioPerGroup;
    }

    @Override
    public void getConfig(FleetcontrollerConfig.Builder builder) {
        AbstractConfigProducerRoot root = getRoot();
        if (root instanceof VespaModel) {
            String zooKeeperAddress =
                    root.getAdmin().getZooKeepersConfigProvider().getZooKeepersConnectionSpec();
            builder.zookeeper_server(zooKeeperAddress);
        } else {
            builder.zookeeper_server("");
        }

        builder.index(0);
        builder.cluster_name(clusterName);
        builder.fleet_controller_count(getChildren().size());

        if (initProgressTime != null) {
            builder.init_progress_time((int) initProgressTime.getMilliSeconds());
        }
        if (transitionTime != null) {
            builder.storage_transition_time((int) transitionTime.getMilliSeconds());
        }
        if (maxPrematureCrashes != null) {
            builder.max_premature_crashes(maxPrematureCrashes.intValue());
        }
        if (stableStateTimePeriod != null) {
            builder.stable_state_time_period((int) stableStateTimePeriod.getMilliSeconds());
        }
        if (minDistributorUpRatio != null) {
            builder.min_distributor_up_ratio(minDistributorUpRatio);
        }
        if (minStorageUpRatio != null) {
            builder.min_storage_up_ratio(minStorageUpRatio);
        }
        if (minSplitBits != null) {
            builder.ideal_distribution_bits(minSplitBits);
        }
        if (minNodeRatioPerGroup != null) {
            builder.min_node_ratio_per_group(minNodeRatioPerGroup);
        }
    }
}
