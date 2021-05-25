// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.google.inject.Inject;
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
public class ClusterControllerClusterConfigurer {

    private final FleetControllerOptions options;

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
        if (controller != null) {
            controller.setOptions(options, metricImpl);
        }
    }

    FleetControllerOptions getOptions() { return options; }

    private static FleetControllerOptions configure(StorDistributionConfig distributionConfig,
                                                    FleetcontrollerConfig fleetcontrollerConfig,
                                                    SlobroksConfig slobroksConfig,
                                                    ZookeepersConfig zookeepersConfig) {
        Distribution distribution = new Distribution(distributionConfig);

        var optionsBuilder = new FleetControllerOptions.Builder(fleetcontrollerConfig.cluster_name(), distribution.getNodes());
        optionsBuilder.setStorageDistribution(distribution);
        configure(optionsBuilder, fleetcontrollerConfig);
        configure(optionsBuilder, slobroksConfig);
        configure(optionsBuilder, zookeepersConfig);
        return optionsBuilder.build();
    }

    private static void configure(FleetControllerOptions.Builder builder, FleetcontrollerConfig config) {
        builder.setFleetControllerIndex(config.index())
               .setFleetControllerCount(config.fleet_controller_count())
               .setZooKeeperSessionTimeout((int) (config.zookeeper_session_timeout() * 1000))
               .setMasterZooKeeperCooldownPeriod((int) (config.master_zookeeper_cooldown_period() * 1000))
               .setStateGatherCount(config.state_gather_count())
               .setRpcPort(config.rpc_port())
               .setHttpPort(config.http_port())
               .setMaxTransitionTime(NodeType.STORAGE, config.storage_transition_time())
               .setMaxTransitionTime(NodeType.DISTRIBUTOR, config.distributor_transition_time())
               .setMaxInitProgressTime(config.init_progress_time())
               .setStatePollingFrequency(config.state_polling_frequency())
               .setMaxPrematureCrashes(config.max_premature_crashes())
               .setStableStateTimePeriod(config.stable_state_time_period())
               .setEventLogMaxSize(config.event_log_max_size())
               .setEventNodeLogMaxSize(config.event_node_log_max_size())
               .setMinDistributorNodesUp(config.min_distributors_up_count())
               .setMinStorageNodesUp(config.min_storage_up_count())
               .setMinRatioOfDistributorNodesUp(config.min_distributor_up_ratio())
               .setMinRatioOfStorageNodesUp(config.min_storage_up_ratio())
               .setCycleWaitTime((int) (config.cycle_wait_time() * 1000))
               .setMinTimeBeforeFirstSystemStateBroadcast((int) (config.min_time_before_first_system_state_broadcast() * 1000))
               .setNodeStateRequestTimeoutMS((int) (config.get_node_state_request_timeout() * 1000))
               .setShowLocalSystemStatesInEventLog(config.show_local_systemstates_in_event_log())
               .setMinTimeBetweenNewSystemStates(config.min_time_between_new_systemstates())
               .setMaxSlobrokDisconnectGracePeriod((int) (config.max_slobrok_disconnect_grace_period() * 1000))
               .setDistributionBits(config.ideal_distribution_bits())
               .setMinNodeRatioPerGroup(config.min_node_ratio_per_group())
               .setMaxDeferredTaskVersionWaitTime(Duration.ofMillis((int)(config.max_deferred_task_version_wait_time_sec() * 1000)))
               .setClusterHasGlobalDocumentTypes(config.cluster_has_global_document_types())
               .setMinMergeCompletionRatio(config.min_merge_completion_ratio())
               .setEnableTwoPhaseClusterStateActivation(config.enable_two_phase_cluster_state_transitions())
               .setClusterFeedBlockEnabled(config.enable_cluster_feed_block())
               .setClusterFeedBlockLimit(Map.copyOf(config.cluster_feed_block_limit()))
               .setClusterFeedBlockNoiseLevel(config.cluster_feed_block_noise_level());
    }

    private static void configure(FleetControllerOptions.Builder builder, SlobroksConfig config) {
        String[] specs = new String[config.slobrok().size()];
        for (int i = 0; i < config.slobrok().size(); i++) {
            specs[i] = config.slobrok().get(i).connectionspec();
        }
        builder.setSlobrokConnectionSpecs(specs);
    }

    private static void configure(FleetControllerOptions.Builder builder, ZookeepersConfig config) {
        builder.setZooKeeperServerAddress(verifyZooKeeperAddress(config.zookeeperserverlist()));
    }

    private static String verifyZooKeeperAddress(String zooKeeperServerAddress) {
        if (zooKeeperServerAddress == null || "".equals(zooKeeperServerAddress)) {
            throw new IllegalArgumentException("zookeeper server address must be set, was '" + zooKeeperServerAddress + "'");
        }
        return zooKeeperServerAddress;
    }

}
