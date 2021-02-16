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
        FleetControllerOptions options = new FleetControllerOptions(fleetcontrollerConfig.cluster_name(), distribution.getNodes());
        options.setStorageDistribution(distribution);
        configure(options, fleetcontrollerConfig);
        configure(options, slobroksConfig);
        configure(options, zookeepersConfig);
        return options;
    }

    private static void configure(FleetControllerOptions options, FleetcontrollerConfig config) {
        options.clusterName = config.cluster_name();
        options.fleetControllerIndex = config.index();
        options.fleetControllerCount = config.fleet_controller_count();
        options.zooKeeperSessionTimeout = (int) (config.zookeeper_session_timeout() * 1000);
        options.masterZooKeeperCooldownPeriod = (int) (config.master_zookeeper_cooldown_period() * 1000);
        options.stateGatherCount = config.state_gather_count();
        options.rpcPort = config.rpc_port();
        options.httpPort = config.http_port();
        options.maxTransitionTime.put(NodeType.STORAGE, config.storage_transition_time());
        options.maxTransitionTime.put(NodeType.DISTRIBUTOR, config.distributor_transition_time());
        options.maxInitProgressTime = config.init_progress_time();
        options.statePollingFrequency = config.state_polling_frequency();
        options.maxPrematureCrashes = config.max_premature_crashes();
        options.stableStateTimePeriod = config.stable_state_time_period();
        options.eventLogMaxSize = config.event_log_max_size();
        options.eventNodeLogMaxSize = config.event_node_log_max_size();
        options.minDistributorNodesUp = config.min_distributors_up_count();
        options.minStorageNodesUp = config.min_storage_up_count();
        options.minRatioOfDistributorNodesUp = config.min_distributor_up_ratio();
        options.minRatioOfStorageNodesUp = config.min_storage_up_ratio();
        options.cycleWaitTime = (int) (config.cycle_wait_time() * 1000);
        options.minTimeBeforeFirstSystemStateBroadcast = (int) (config.min_time_before_first_system_state_broadcast() * 1000);
        options.nodeStateRequestTimeoutMS = (int) (config.get_node_state_request_timeout() * 1000);
        options.showLocalSystemStatesInEventLog = config.show_local_systemstates_in_event_log();
        options.minTimeBetweenNewSystemStates = config.min_time_between_new_systemstates();
        options.maxSlobrokDisconnectGracePeriod = (int) (config.max_slobrok_disconnect_grace_period() * 1000);
        options.distributionBits = config.ideal_distribution_bits();
        options.minNodeRatioPerGroup = config.min_node_ratio_per_group();
        options.setMaxDeferredTaskVersionWaitTime(Duration.ofMillis((int)(config.max_deferred_task_version_wait_time_sec() * 1000)));
        options.clusterHasGlobalDocumentTypes = config.cluster_has_global_document_types();
        options.minMergeCompletionRatio = config.min_merge_completion_ratio();
        options.enableTwoPhaseClusterStateActivation = config.enable_two_phase_cluster_state_transitions();
        options.clusterFeedBlockEnabled = config.enable_cluster_feed_block();
        options.clusterFeedBlockLimit = Map.copyOf(config.cluster_feed_block_limit());
        options.clusterFeedBlockNoiseLevel = config.cluster_feed_block_noise_level();
    }

    private static void configure(FleetControllerOptions options, SlobroksConfig config) {
        String[] specs = new String[config.slobrok().size()];
        for (int i = 0; i < config.slobrok().size(); i++) {
            specs[i] = config.slobrok().get(i).connectionspec();
        }
        options.slobrokConnectionSpecs = specs;
    }

    private static void configure(FleetControllerOptions options, ZookeepersConfig config) {
        options.zooKeeperServerAddress = verifyZooKeeperAddress(config.zookeeperserverlist());
    }

    private static String verifyZooKeeperAddress(String zooKeeperServerAddress) {
        if (zooKeeperServerAddress == null || "".equals(zooKeeperServerAddress)) {
            throw new IllegalArgumentException("zookeeper server address must be set, was '" + zooKeeperServerAddress + "'");
        }
        return zooKeeperServerAddress;
    }

}
