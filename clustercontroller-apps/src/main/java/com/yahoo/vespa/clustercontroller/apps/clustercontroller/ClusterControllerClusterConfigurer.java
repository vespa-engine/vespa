// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.vespa.zookeeper.VespaZooKeeperServer;

import java.time.Duration;
import java.util.Map;

/**
 * When the cluster controller is reconfigured, a new instance of this is created, which will propagate configured
 * options to receivers such as the fleet controller.
 */
public class ClusterControllerClusterConfigurer extends AbstractComponent {

    private final FleetControllerOptions options;
    private final ClusterController controller;

    /**
     * The {@link VespaZooKeeperServer} argument is required by the injected {@link ClusterController},
     * to ensure that zookeeper has started before it starts polling it. It must be done here to avoid
     * duplicates being created by the dependency injection framework.
     */
    @Inject
    public ClusterControllerClusterConfigurer(ClusterController controller,
                                              StorDistributionConfig distributionConfig,
                                              FleetcontrollerConfig fleetcontrollerConfig,
                                              SlobroksConfig slobroksConfig,
                                              ZookeepersConfig zookeepersConfig,
                                              Metric metricImpl,
                                              VespaZooKeeperServer started) throws Exception {
        this.options = configure(distributionConfig, fleetcontrollerConfig, slobroksConfig, zookeepersConfig);
        this.controller = controller;
        if (controller != null) controller.setOptions(options, metricImpl);
    }

    @Override
    public void deconstruct() {
        if (controller != null) controller.countdown();
    }

    FleetControllerOptions getOptions() { return options; }

    private static FleetControllerOptions configure(StorDistributionConfig distributionConfig,
                                                    FleetcontrollerConfig fleetcontrollerConfig,
                                                    SlobroksConfig slobroksConfig,
                                                    ZookeepersConfig zookeepersConfig) {
        Distribution distribution = new Distribution(distributionConfig);
        FleetControllerOptions.Builder builder = new FleetControllerOptions.Builder(fleetcontrollerConfig.cluster_name(), distribution.getNodes());
        builder.setStorageDistribution(distribution);
        configure(builder, fleetcontrollerConfig);
        configure(builder, slobroksConfig);
        configure(builder, zookeepersConfig);
        return builder.build();
    }

    private static void configure(FleetControllerOptions.Builder builder, FleetcontrollerConfig config) {
        builder.setClusterName(config.cluster_name());
        builder.setIndex(config.index());
        builder.setCount(config.fleet_controller_count());
        builder.setZooKeeperSessionTimeout((int) (config.zookeeper_session_timeout() * 1000));
        builder.setMasterZooKeeperCooldownPeriod((int) (config.master_zookeeper_cooldown_period() * 1000));
        builder.setStateGatherCount(config.state_gather_count());
        builder.setRpcPort(config.rpc_port());
        builder.setHttpPort(config.http_port());
        builder.setMaxTransitionTime(NodeType.STORAGE, config.storage_transition_time());
        builder.setMaxTransitionTime(NodeType.DISTRIBUTOR, config.distributor_transition_time());
        builder.setMaxInitProgressTime(config.init_progress_time());
        builder.setMaxPrematureCrashes(config.max_premature_crashes());
        builder.setStableStateTimePeriod(config.stable_state_time_period());
        builder.setEventLogMaxSize(config.event_log_max_size());
        builder.setEventNodeLogMaxSize(config.event_node_log_max_size());
        builder.setMinDistributorNodesUp(config.min_distributors_up_count());
        builder.setMinStorageNodesUp(config.min_storage_up_count());
        builder.setMinRatioOfDistributorNodesUp(config.min_distributor_up_ratio());
        builder.setMinRatioOfStorageNodesUp(config.min_storage_up_ratio());
        builder.setCycleWaitTime((int) (config.cycle_wait_time() * 1000));
        builder.setMinTimeBeforeFirstSystemStateBroadcast((int) (config.min_time_before_first_system_state_broadcast() * 1000));
        builder.setNodeStateRequestTimeoutMS((int) (config.get_node_state_request_timeout() * 1000));
        builder.setShowLocalSystemStatesInEventLog(config.show_local_systemstates_in_event_log());
        builder.setMinTimeBetweenNewSystemStates(config.min_time_between_new_systemstates());
        builder.setMaxSlobrokDisconnectGracePeriod((int) (config.max_slobrok_disconnect_grace_period() * 1000));
        builder.setDistributionBits(config.ideal_distribution_bits());
        builder.setMinNodeRatioPerGroup(config.min_node_ratio_per_group());
        builder.setMaxDeferredTaskVersionWaitTime(Duration.ofMillis((int)(config.max_deferred_task_version_wait_time_sec() * 1000)));
        builder.setClusterHasGlobalDocumentTypes(config.cluster_has_global_document_types());
        builder.setMinMergeCompletionRatio(config.min_merge_completion_ratio());
        builder.enableTwoPhaseClusterStateActivation(config.enable_two_phase_cluster_state_transitions());
        builder.setClusterFeedBlockEnabled(config.enable_cluster_feed_block());
        builder.setClusterFeedBlockLimit(Map.copyOf(config.cluster_feed_block_limit()));
        builder.setClusterFeedBlockNoiseLevel(config.cluster_feed_block_noise_level());
    }

    private static void configure(FleetControllerOptions.Builder builder, SlobroksConfig config) {
        String[] specs = new String[config.slobrok().size()];
        for (int i = 0; i < config.slobrok().size(); i++) {
            specs[i] = config.slobrok().get(i).connectionspec();
        }
        builder.setSlobrokConnectionSpecs(specs);
    }

    private static void configure(FleetControllerOptions.Builder builder, ZookeepersConfig config) {
        builder.setZooKeeperServerAddress(config.zookeeperserverlist());
    }

}
