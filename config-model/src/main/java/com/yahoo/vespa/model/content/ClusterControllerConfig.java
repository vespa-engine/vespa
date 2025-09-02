// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static com.yahoo.vespa.model.content.CoveragePolicy.Policy.GROUP;
import static com.yahoo.vespa.model.content.CoveragePolicy.Policy.NODE;
import static java.util.logging.Level.INFO;

/**
 * Config generation for parameters for fleet controllers.
 */
public class ClusterControllerConfig extends AnyConfigProducer implements FleetcontrollerConfig.Producer {

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<ClusterControllerConfig> {
        private final String clusterName;
        private final ModelElement clusterElement;
        private final ResourceLimits resourceLimits;

        public Builder(String clusterName, ModelElement clusterElement, ResourceLimits resourceLimits) {
            this.clusterName = clusterName;
            this.clusterElement = clusterElement;
            this.resourceLimits = resourceLimits;
        }

        @Override
        protected ClusterControllerConfig doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
            ModelElement tuning = clusterElement.child("tuning");
            ModelElement clusterControllerTuning = null;

            Optional<Double> minNodeRatioPerGroup = Optional.empty();
            Optional<Integer> bucketSplittingMinimumBits = Optional.empty();
            if (tuning != null) {
                minNodeRatioPerGroup = Optional.ofNullable(tuning.childAsDouble("min-node-ratio-per-group"));
                bucketSplittingMinimumBits = Optional.ofNullable(tuning.childAsInteger("bucket-splitting.minimum-bits"));
                clusterControllerTuning = tuning.child("cluster-controller");
            }

            var numberOfLeafGroups = ((ContentCluster) ancestor).getRootGroup().getNumberOfLeafGroups();
            var maxGroupsAllowedDown = maxGroupsAllowedDown(clusterControllerTuning, numberOfLeafGroups, clusterElement, deployState);
            var tuningConfig = new ClusterControllerTuningBuilder(clusterControllerTuning,
                                                                  minNodeRatioPerGroup,
                                                                  bucketSplittingMinimumBits,
                                                                  maxGroupsAllowedDown)
                    .build();

            return new ClusterControllerConfig(ancestor, clusterName, tuningConfig, resourceLimits);
        }

    }

    private final String clusterName;
    private final ClusterControllerTuning tuning;
    private final ResourceLimits resourceLimits;

    private ClusterControllerConfig(TreeConfigProducer<?> parent,
                                    String clusterName,
                                    ClusterControllerTuning tuning,
                                    ResourceLimits resourceLimits) {
        super(parent, "fleetcontroller");
        this.clusterName = clusterName;
        this.tuning = tuning;
        this.resourceLimits = resourceLimits;
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
        builder.max_number_of_groups_allowed_to_be_down(tuning.maxGroupsAllowedDown().orElse(-1));

        resourceLimits.getConfig(builder);
    }

    public ClusterControllerTuning tuning() { return tuning; }

    private static CoveragePolicy.Policy coveragePolicy(ModelElement content) {
        var policy = Optional.ofNullable(content.childAsString("coverage-policy"));
        return policy.map(s -> new CoveragePolicy(CoveragePolicy.Policy.valueOf(s.toUpperCase())))
                     .orElseGet(CoveragePolicy::group).policy();
    }

    private static Optional<Integer> maxGroupsAllowedDown(ModelElement tuning,
                                                          int numberOfLeafGroups,
                                                          ModelElement clusterElement,
                                                          DeployState deployState) {
        var coveragePolicy = coveragePolicy(clusterElement);
        if (coveragePolicy == GROUP && numberOfLeafGroups == 2) {
            deployState.getDeployLogger()
                       .logApplicationPackage(INFO, "Coverage policy is '" + coveragePolicy.name().toLowerCase() +
                               "', but with 2 groups in the cluster all load will be placed on 1 group when the" +
                               " other group is allowed to be down when doing maintenance or upgrades." +
                               " This might lead to overload. See https://docs.vespa.ai/en/reference/services-content.html#coverage-policy.");
        }

        if (tuning != null) {
            var groupsAllowedDownRatio = tuning.childAsDouble("groups-allowed-down-ratio");

            if (groupsAllowedDownRatio != null) {
                if (groupsAllowedDownRatio < 0 || groupsAllowedDownRatio > 1)
                    throw new IllegalArgumentException("groups-allowed-down-ratio must be between 0 and 1, got " + groupsAllowedDownRatio);

                if (coveragePolicy == NODE)
                    throw new IllegalArgumentException("Cannot set groups-allowed-down-ratio when coverage-policy is 'node'");

                var maxGroupsAllowedDown = Math.max(1, (int) Math.floor(groupsAllowedDownRatio * numberOfLeafGroups));
                return Optional.of(maxGroupsAllowedDown);
            }
        }

        return coveragePolicy.equals(GROUP)
                ? Optional.empty()
                : Optional.of(0);
    }

    private static class ClusterControllerTuningBuilder {

        private final Optional<Double> minNodeRatioPerGroup;
        private final Optional<Duration> initProgressTime;
        private final Optional<Duration> transitionTime;
        private final Optional<Long> maxPrematureCrashes;
        private final Optional<Duration> stableStateTimePeriod;
        private final Optional<Double> minDistributorUpRatio;
        private final Optional<Double> minStorageUpRatio;
        private final Optional<Integer> minSplitBits;
        private final Optional<Integer> maxGroupsAllowedDown;

        ClusterControllerTuningBuilder(ModelElement tuning,
                                       Optional<Double> minNodeRatioPerGroup,
                                       Optional<Integer> bucketSplittingMinimumBits,
                                       Optional<Integer> maxGroupsAllowedDown) {
            this.minSplitBits = bucketSplittingMinimumBits;
            this.minNodeRatioPerGroup = minNodeRatioPerGroup;
            this.maxGroupsAllowedDown = maxGroupsAllowedDown;
            if (tuning == null) {
                this.initProgressTime = Optional.empty();
                this.transitionTime = Optional.empty();
                this.maxPrematureCrashes = Optional.empty();
                this.stableStateTimePeriod = Optional.empty();
                this.minDistributorUpRatio = Optional.empty();
                this.minStorageUpRatio = Optional.empty();
            }
            else {
                this.initProgressTime = Optional.ofNullable(tuning.childAsDuration("init-progress-time"));
                this.transitionTime = Optional.ofNullable(tuning.childAsDuration("transition-time"));
                this.maxPrematureCrashes = Optional.ofNullable(tuning.childAsLong("max-premature-crashes"));
                this.stableStateTimePeriod = Optional.ofNullable(tuning.childAsDuration("stable-state-period"));
                this.minDistributorUpRatio = Optional.ofNullable(tuning.childAsDouble("min-distributor-up-ratio"));
                this.minStorageUpRatio = Optional.ofNullable(tuning.childAsDouble("min-storage-up-ratio"));
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

    public record ClusterControllerTuning(Optional<Duration> initProgressTime,
                                          Optional<Duration> transitionTime,
                                          Optional<Long> maxPrematureCrashes,
                                          Optional<Duration> stableStateTimePeriod,
                                          Optional<Double> minDistributorUpRatio,
                                          Optional<Double> minStorageUpRatio,
                                          Optional<Integer> maxGroupsAllowedDown,
                                          Optional<Double> minNodeRatioPerGroup,
                                          Optional<Integer> minSplitBits) {
    }

}
