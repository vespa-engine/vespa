// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.utils.Duration;
import org.w3c.dom.Element;
import java.util.Optional;

/**
 * Config generation for common parameters for all fleet controllers.
 */
public class ClusterControllerConfig extends AnyConfigProducer implements FleetcontrollerConfig.Producer {

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<ClusterControllerConfig> {
        private final String clusterName;
        private final ModelElement clusterElement;
        private final ResourceLimits resourceLimits;
        private final boolean allowMoreThanOneContentGroupDown;

        public Builder(String clusterName,
                       ModelElement clusterElement,
                       ResourceLimits resourceLimits,
                       boolean allowMoreThanOneContentGroupDown) {
            this.clusterName = clusterName;
            this.clusterElement = clusterElement;
            this.resourceLimits = resourceLimits;
            this.allowMoreThanOneContentGroupDown = allowMoreThanOneContentGroupDown;
        }

        @Override
        protected ClusterControllerConfig doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
            ModelElement tuning = clusterElement.child("tuning");
            ModelElement clusterControllerTuning = null;

            Optional<Double> minNodeRatioPerGroup = Optional.of(deployState.featureFlags().minNodeRatioPerGroup());
            Optional<Integer> bucketSplittingMinimumBits = Optional.empty();
            if (tuning != null) {
                minNodeRatioPerGroup = Optional.ofNullable(tuning.childAsDouble("min-node-ratio-per-group"));
                bucketSplittingMinimumBits = Optional.ofNullable(tuning.childAsInteger("bucket-splitting.minimum-bits"));
                clusterControllerTuning = tuning.child("cluster-controller");
            }

            var tuningConfig = new ClusterControllerTuningBuilder(clusterControllerTuning,
                                                            minNodeRatioPerGroup,
                                                            bucketSplittingMinimumBits)
                    .build();
            if (ancestor instanceof ContentCluster) {
                int numberOfLeafGroups = ((ContentCluster) ancestor).getRootGroup().getNumberOfLeafGroups();
                if (tuningConfig.maxGroupsAllowedDown().isPresent()) {
                    Integer maxGroupsAllowedDown = tuningConfig.maxGroupsAllowedDown().get();
                    if (deployState.zone().environment().isProduction() && (maxGroupsAllowedDown > numberOfLeafGroups))
                        throw new IllegalArgumentException("Cannot set max-groups-allowed-down (" + maxGroupsAllowedDown +
                                                                   ") larger than number of groups (" + numberOfLeafGroups + ")");
                } else {
                    // Reduce to numberOfLeafGroups for tests or in environments where number of groups are reduced by policy (dev, test, staging, perf)
                    tuningConfig = tuningConfig.withMaxGroupsAllowedDown(numberOfLeafGroups);
                }
            } else {
                // Reduce to 1 for tests (ancestor is a mock class)
                tuningConfig = tuningConfig.withMaxGroupsAllowedDown(1);
            }

            return new ClusterControllerConfig(ancestor,
                                               clusterName,
                                               tuningConfig,
                                               resourceLimits,
                                               allowMoreThanOneContentGroupDown);
        }
    }

    private final String clusterName;
    private final ClusterControllerTuning tuning;
    private final ResourceLimits resourceLimits;
    private final boolean allowMoreThanOneContentGroupDown;

    private ClusterControllerConfig(TreeConfigProducer<?> parent,
                                    String clusterName,
                                    ClusterControllerTuning tuning,
                                    ResourceLimits resourceLimits,
                                    boolean allowMoreThanOneContentGroupDown) {
        super(parent, "fleetcontroller");
        this.clusterName = clusterName;
        this.tuning = tuning;
        this.resourceLimits = resourceLimits;
        this.allowMoreThanOneContentGroupDown = allowMoreThanOneContentGroupDown;
    }

    @Override
    public void getConfig(FleetcontrollerConfig.Builder builder) {
        AbstractConfigProducerRoot root = getRoot();
        if (root instanceof VespaModel) {
            String zooKeeperAddress = root.getAdmin().getZooKeepersConfigProvider().getZooKeepersConnectionSpec();
            builder.zookeeper_server(zooKeeperAddress);
        } else {
            builder.zookeeper_server("");
        }

        builder.index(0);
        builder.cluster_name(clusterName);
        builder.fleet_controller_count(getChildren().size());

        tuning.initProgressTime.ifPresent(i -> builder.init_progress_time((int) i.getMilliSeconds()));
        tuning.transitionTime.ifPresent(t -> builder.storage_transition_time((int) t.getMilliSeconds()));
        tuning.maxPrematureCrashes.ifPresent(var -> builder.max_premature_crashes(var.intValue()));
        tuning.stableStateTimePeriod.ifPresent(var -> builder.stable_state_time_period((int) var.getMilliSeconds()));
        tuning.minDistributorUpRatio.ifPresent(builder::min_distributor_up_ratio);
        tuning.minStorageUpRatio.ifPresent(builder::min_storage_up_ratio);
        tuning.minSplitBits.ifPresent(builder::ideal_distribution_bits);
        tuning.minNodeRatioPerGroup.ifPresent(builder::min_node_ratio_per_group);
        tuning.maxGroupsAllowedDown.ifPresent(max -> builder.max_number_of_groups_allowed_to_be_down(allowMoreThanOneContentGroupDown ? max : -1));

        resourceLimits.getConfig(builder);
    }

    public ClusterControllerTuning tuning() {return tuning;}

private static class ClusterControllerTuningBuilder {

    private final Optional<Double> minNodeRatioPerGroup;
    private final Optional<Duration> initProgressTime;
    private final Optional<Duration> transitionTime;
    private final Optional<Long> maxPrematureCrashes;
    private final Optional<Duration> stableStateTimePeriod;
    private final Optional<Double> minDistributorUpRatio;
    private final Optional<Double> minStorageUpRatio;
    private final Optional<Integer> minSplitBits;
    final Optional<Integer> maxGroupsAllowedDown;

    ClusterControllerTuningBuilder(ModelElement tuning,
                                   Optional<Double> minNodeRatioPerGroup,
                                   Optional<Integer> bucketSplittingMinimumBits) {
        this.minSplitBits = bucketSplittingMinimumBits;
        this.minNodeRatioPerGroup = minNodeRatioPerGroup;
        if (tuning == null) {
            this.initProgressTime = Optional.empty();
            this.transitionTime = Optional.empty();
            this.maxPrematureCrashes = Optional.empty();
            this.stableStateTimePeriod = Optional.empty();
            this.minDistributorUpRatio = Optional.empty();
            this.minStorageUpRatio = Optional.empty();
            this.maxGroupsAllowedDown = Optional.empty();
        } else {
            this.initProgressTime = Optional.ofNullable(tuning.childAsDuration("init-progress-time"));
            this.transitionTime = Optional.ofNullable(tuning.childAsDuration("transition-time"));
            this.maxPrematureCrashes = Optional.ofNullable(tuning.childAsLong("max-premature-crashes"));
            this.stableStateTimePeriod = Optional.ofNullable(tuning.childAsDuration("stable-state-period"));
            this.minDistributorUpRatio = Optional.ofNullable(tuning.childAsDouble("min-distributor-up-ratio"));
            this.minStorageUpRatio = Optional.ofNullable(tuning.childAsDouble("min-storage-up-ratio"));
            this.maxGroupsAllowedDown = Optional.ofNullable(tuning.childAsInteger("max-groups-allowed-down"));
        }
    }

    private ClusterControllerTuning build() {
        return new ClusterControllerTuning(initProgressTime,
                                           transitionTime,
                                           maxPrematureCrashes,
                                           stableStateTimePeriod,
                                           minDistributorUpRatio,
                                           minStorageUpRatio,
                                           maxGroupsAllowedDown,
                                           minNodeRatioPerGroup,
                                           minSplitBits);
    }

}

private record ClusterControllerTuning(Optional<Duration> initProgressTime,
                                       Optional<Duration> transitionTime,
                                       Optional<Long> maxPrematureCrashes,
                                       Optional<Duration> stableStateTimePeriod,
                                       Optional<Double> minDistributorUpRatio,
                                       Optional<Double> minStorageUpRatio,
                                       Optional<Integer> maxGroupsAllowedDown,
                                       Optional<Double> minNodeRatioPerGroup,
                                       Optional<Integer> minSplitBits) {

    public ClusterControllerTuning withMaxGroupsAllowedDown(int maxGroupsAllowedDown) {
        return new ClusterControllerConfig.ClusterControllerTuning(
                initProgressTime,
                transitionTime,
                maxPrematureCrashes,
                stableStateTimePeriod,
                minDistributorUpRatio,
                minStorageUpRatio,
                Optional.of(maxGroupsAllowedDown),
                minNodeRatioPerGroup,
                minSplitBits);
    }

}

}
