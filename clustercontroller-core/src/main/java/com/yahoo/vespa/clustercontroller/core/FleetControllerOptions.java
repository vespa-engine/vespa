// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import ai.vespa.validation.Validation;
import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.NodeType;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable class representing all the options that can be set in the fleetcontroller.
 * Tests typically just generate an instance of this object to use in fleet controller for testing.
 * A real application generates this object from config, and on config updates, post new options to the fleet controller.
 */
public class FleetControllerOptions {

    private final String clusterName;
    private final int fleetControllerIndex;
    private final int fleetControllerCount;
    private final int stateGatherCount;

    private final String[] slobrokConnectionSpecs;
    private final int rpcPort;
    private final int httpPort;
    private final int distributionBits;

    /** Timeout before breaking zookeeper session (in milliseconds) */
    private final int zooKeeperSessionTimeout;
    /**
     * Timeout between master disappearing before new master will take over.
     * (Grace period to allow old master to detect that it is disconnected from zookeeper)
     */
    private final int masterZooKeeperCooldownPeriod;

    private final String zooKeeperServerAddress;

    /**
     * Max amount of time to keep a node, that has previously been available
     * in steady state, in maintenance mode, while node is unreachable, before setting it down.
     */
    private final Map<NodeType, Integer> maxTransitionTime;

    /**
     * Max amount of time to keep a storage node, that is initializing, in maintenance mode, without any further
     * initializing progress being received, before setting it down.
     */
    private final int maxInitProgressTime;

    private final int maxPrematureCrashes;
    private final long stableStateTimePeriod;

    private final int eventLogMaxSize;
    private final int eventNodeLogMaxSize;

    private final int minDistributorNodesUp;
    private final int minStorageNodesUp;
    private final double minRatioOfDistributorNodesUp;
    private final double minRatioOfStorageNodesUp;

    /**
     * Minimum ratio of nodes in an "available" state (up, initializing or maintenance)
     * that shall be present in a group for the group itself to be considered available.
     * If the ratio of available nodes drop under this limit, the group's nodes will be
     * implicitly taken down.
     *
     * A value of 0.0 implies group auto-takedown feature is effectively disabled.
     */
    private final double minNodeRatioPerGroup;

    /**
     * Milliseconds to sleep after doing a work cycle where we did no work. Some events do not interrupt the sleeping,
     * such as slobrok changes, so shouldn't set this too high.
     */
    private final int cycleWaitTime;
    /**
     * Minimum time to pass (in milliseconds) before broadcasting our first systemstate. Set small in unit tests,
     * but should be a few seconds in a real system to prevent new nodes taking over from disturbing the system by
     * putting out a different systemstate just because all nodes don't answer witihin a single cycle.
     * The cluster state is allowed to be broadcasted before this time if all nodes have successfully
     * reported their state in Slobrok and getnodestate. This value should typically be in the order of
     * maxSlobrokDisconnectGracePeriod and nodeStateRequestTimeoutMS.
     */
    private final long minTimeBeforeFirstSystemStateBroadcast;

    /**
     * StateRequestTimeout for the request are randomized a bit to avoid congestion on replies. The effective
     * interval is
     * [nodeStateRequestTimeoutEarliestPercentage * nodeStateRequestTimeoutMS / 100,
     *                          nodeStateRequestTimeoutLatestPercentage * nodeStateRequestTimeoutMS / 100].
     */
    private final int nodeStateRequestTimeoutMS;
    private final int nodeStateRequestTimeoutEarliestPercentage;
    private final int nodeStateRequestTimeoutLatestPercentage;
    private final int nodeStateRequestRoundTripTimeMaxSeconds;

    private final int minTimeBetweenNewSystemStates;
    private final boolean showLocalSystemStatesInEventLog;

    /** Maximum time a node can be missing from slobrok before it is tagged down. */
    private final int maxSlobrokDisconnectGracePeriod;

    /** Set by tests to retry often. */
    private final BackOffPolicy slobrokBackOffPolicy;

    private final Distribution storageDistribution;

    // TODO: Get rid of this by always getting nodes by distribution.getNodes()
    private final Set<ConfiguredNode> nodes;

    private final Duration maxDeferredTaskVersionWaitTime;

    private final boolean clusterHasGlobalDocumentTypes;

    private final boolean enableTwoPhaseClusterStateActivation;

    private final double minMergeCompletionRatio;

    private final int maxDivergentNodesPrintedInTaskErrorMessages;

    private final boolean clusterFeedBlockEnabled;
    // Resource type -> limit in [0, 1]
    private final Map<String, Double> clusterFeedBlockLimit;

    private final double clusterFeedBlockNoiseLevel;

    private FleetControllerOptions(String clusterName,
                                   int fleetControllerIndex,
                                   int fleetControllerCount,
                                   int stateGatherCount,
                                   String[] slobrokConnectionSpecs,
                                   int rpcPort,
                                   int httpPort,
                                   int distributionBits,
                                   int zooKeeperSessionTimeout,
                                   int masterZooKeeperCooldownPeriod,
                                   String zooKeeperServerAddress,
                                   Map<NodeType, Integer> maxTransitionTime,
                                   int maxInitProgressTime,
                                   int maxPrematureCrashes,
                                   long stableStateTimePeriod,
                                   int eventLogMaxSize,
                                   int eventNodeLogMaxSize,
                                   int minDistributorNodesUp,
                                   int minStorageNodesUp,
                                   double minRatioOfDistributorNodesUp,
                                   double minRatioOfStorageNodesUp,
                                   double minNodeRatioPerGroup,
                                   int cycleWaitTime,
                                   long minTimeBeforeFirstSystemStateBroadcast,
                                   int nodeStateRequestTimeoutMS,
                                   int nodeStateRequestTimeoutEarliestPercentage,
                                   int nodeStateRequestTimeoutLatestPercentage,
                                   int nodeStateRequestRoundTripTimeMaxSeconds,
                                   int minTimeBetweenNewSystemStates,
                                   boolean showLocalSystemStatesInEventLog,
                                   int maxSlobrokDisconnectGracePeriod,
                                   BackOffPolicy slobrokBackOffPolicy,
                                   Distribution storageDistribution,
                                   Set<ConfiguredNode> nodes,
                                   Duration maxDeferredTaskVersionWaitTime,
                                   boolean clusterHasGlobalDocumentTypes,
                                   boolean enableTwoPhaseClusterStateActivation,
                                   double minMergeCompletionRatio,
                                   int maxDivergentNodesPrintedInTaskErrorMessages,
                                   boolean clusterFeedBlockEnabled,
                                   Map<String, Double> clusterFeedBlockLimit,
                                   double clusterFeedBlockNoiseLevel) {
        this.clusterName = clusterName;
        this.fleetControllerIndex = fleetControllerIndex;
        this.fleetControllerCount = fleetControllerCount;
        this.stateGatherCount = stateGatherCount;
        this.slobrokConnectionSpecs = slobrokConnectionSpecs;
        this.rpcPort = rpcPort;
        this.httpPort = httpPort;
        this.distributionBits = distributionBits;
        this.zooKeeperSessionTimeout = zooKeeperSessionTimeout;
        this.masterZooKeeperCooldownPeriod = masterZooKeeperCooldownPeriod;
        this.zooKeeperServerAddress = zooKeeperServerAddress;
        this.maxTransitionTime = maxTransitionTime;
        this.maxInitProgressTime = maxInitProgressTime;
        this.maxPrematureCrashes = maxPrematureCrashes;
        this.stableStateTimePeriod = stableStateTimePeriod;
        this.eventLogMaxSize = eventLogMaxSize;
        this.eventNodeLogMaxSize = eventNodeLogMaxSize;
        this.minDistributorNodesUp = minDistributorNodesUp;
        this.minStorageNodesUp = minStorageNodesUp;
        this.minRatioOfDistributorNodesUp = minRatioOfDistributorNodesUp;
        this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
        this.minNodeRatioPerGroup = minNodeRatioPerGroup;
        this.cycleWaitTime = Validation.requireAtLeast(cycleWaitTime, "cycleWaitTime must be positive", 1);
        this.minTimeBeforeFirstSystemStateBroadcast = minTimeBeforeFirstSystemStateBroadcast;
        this.nodeStateRequestTimeoutMS = nodeStateRequestTimeoutMS;
        this.nodeStateRequestTimeoutEarliestPercentage = nodeStateRequestTimeoutEarliestPercentage;
        this.nodeStateRequestTimeoutLatestPercentage = nodeStateRequestTimeoutLatestPercentage;
        this.nodeStateRequestRoundTripTimeMaxSeconds = nodeStateRequestRoundTripTimeMaxSeconds;
        this.minTimeBetweenNewSystemStates = minTimeBetweenNewSystemStates;
        this.showLocalSystemStatesInEventLog = showLocalSystemStatesInEventLog;
        this.maxSlobrokDisconnectGracePeriod = maxSlobrokDisconnectGracePeriod;
        this.slobrokBackOffPolicy = slobrokBackOffPolicy;
        this.storageDistribution = storageDistribution;
        this.nodes = nodes;
        this.maxDeferredTaskVersionWaitTime = maxDeferredTaskVersionWaitTime;
        this.clusterHasGlobalDocumentTypes = clusterHasGlobalDocumentTypes;
        this.enableTwoPhaseClusterStateActivation = enableTwoPhaseClusterStateActivation;
        this.minMergeCompletionRatio = minMergeCompletionRatio;
        this.maxDivergentNodesPrintedInTaskErrorMessages = maxDivergentNodesPrintedInTaskErrorMessages;
        this.clusterFeedBlockEnabled = clusterFeedBlockEnabled;
        this.clusterFeedBlockLimit = clusterFeedBlockLimit;
        this.clusterFeedBlockNoiseLevel = clusterFeedBlockNoiseLevel;
    }

    public Duration getMaxDeferredTaskVersionWaitTime() {
        return maxDeferredTaskVersionWaitTime;
    }

    public long storageNodeMaxTransitionTimeMs() {
        return maxTransitionTime.getOrDefault(NodeType.STORAGE, 10_000);
    }

    public String clusterName() {
        return clusterName;
    }

    public int fleetControllerIndex() {
        return fleetControllerIndex;
    }

    public int fleetControllerCount() {
        return fleetControllerCount;
    }

    public int stateGatherCount() {
        return stateGatherCount;
    }

    public String[] slobrokConnectionSpecs() {
        return slobrokConnectionSpecs;
    }

    public int rpcPort() {
        return rpcPort;
    }

    public int httpPort() {
        return httpPort;
    }

    public int distributionBits() {
        return distributionBits;
    }

    public int zooKeeperSessionTimeout() {
        return zooKeeperSessionTimeout;
    }

    public int masterZooKeeperCooldownPeriod() {
        return masterZooKeeperCooldownPeriod;
    }

    public String zooKeeperServerAddress() {
        return zooKeeperServerAddress;
    }

    public Map<NodeType, Integer> maxTransitionTime() {
        return maxTransitionTime;
    }

    public int maxInitProgressTime() {
        return maxInitProgressTime;
    }

    public int maxPrematureCrashes() {
        return maxPrematureCrashes;
    }

    public long stableStateTimePeriod() {
        return stableStateTimePeriod;
    }

    public int eventLogMaxSize() {
        return eventLogMaxSize;
    }

    public int eventNodeLogMaxSize() {
        return eventNodeLogMaxSize;
    }

    public int minDistributorNodesUp() {
        return minDistributorNodesUp;
    }

    public int minStorageNodesUp() {
        return minStorageNodesUp;
    }

    public double minRatioOfDistributorNodesUp() {
        return minRatioOfDistributorNodesUp;
    }

    public double minRatioOfStorageNodesUp() {
        return minRatioOfStorageNodesUp;
    }

    public double minNodeRatioPerGroup() {
        return minNodeRatioPerGroup;
    }

    public int cycleWaitTime() {
        return cycleWaitTime;
    }

    public long minTimeBeforeFirstSystemStateBroadcast() {
        return minTimeBeforeFirstSystemStateBroadcast;
    }

    public int nodeStateRequestTimeoutMS() {
        return nodeStateRequestTimeoutMS;
    }

    public int nodeStateRequestTimeoutEarliestPercentage() {
        return nodeStateRequestTimeoutEarliestPercentage;
    }

    public int nodeStateRequestTimeoutLatestPercentage() {
        return nodeStateRequestTimeoutLatestPercentage;
    }

    public int nodeStateRequestRoundTripTimeMaxSeconds() {
        return nodeStateRequestRoundTripTimeMaxSeconds;
    }

    public int minTimeBetweenNewSystemStates() {
        return minTimeBetweenNewSystemStates;
    }

    public boolean showLocalSystemStatesInEventLog() {
        return showLocalSystemStatesInEventLog;
    }

    public int maxSlobrokDisconnectGracePeriod() {
        return maxSlobrokDisconnectGracePeriod;
    }

    public BackOffPolicy slobrokBackOffPolicy() {
        return slobrokBackOffPolicy;
    }

    public Distribution storageDistribution() {
        return storageDistribution;
    }

    public Set<ConfiguredNode> nodes() {
        return nodes;
    }

    public Duration maxDeferredTaskVersionWaitTime() {
        return maxDeferredTaskVersionWaitTime;
    }

    public boolean clusterHasGlobalDocumentTypes() {
        return clusterHasGlobalDocumentTypes;
    }

    public boolean enableTwoPhaseClusterStateActivation() {
        return enableTwoPhaseClusterStateActivation;
    }

    public double minMergeCompletionRatio() {
        return minMergeCompletionRatio;
    }

    public int maxDivergentNodesPrintedInTaskErrorMessages() {
        return maxDivergentNodesPrintedInTaskErrorMessages;
    }

    public boolean clusterFeedBlockEnabled() {
        return clusterFeedBlockEnabled;
    }

    public Map<String, Double> clusterFeedBlockLimit() {
        return clusterFeedBlockLimit;
    }

    public double clusterFeedBlockNoiseLevel() {
        return clusterFeedBlockNoiseLevel;
    }

    public static class Builder {

        private String clusterName;
        private int index = 0;
        private int count = 1;
        private int stateGatherCount = 2;
        private String[] slobrokConnectionSpecs;
        private int rpcPort = 0;
        private int httpPort = 0;
        private int distributionBits = 16;
        private int zooKeeperSessionTimeout = 5 * 60 * 1000;
        private int masterZooKeeperCooldownPeriod = 15 * 1000;
        private String zooKeeperServerAddress = null;
        private Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
        private int maxInitProgressTime = 5000;
        private int maxPrematureCrashes = 4;
        private long stableStateTimePeriod = 2 * 60 * 60 * 1000;
        private int eventLogMaxSize = 1024;
        private int eventNodeLogMaxSize = 1024;
        private int minDistributorNodesUp = 1;
        private int minStorageNodesUp = 1;
        private double minRatioOfDistributorNodesUp = 0.50;
        private double minRatioOfStorageNodesUp = 0.50;
        private double minNodeRatioPerGroup = 0.0;
        private int cycleWaitTime = 100;
        private long minTimeBeforeFirstSystemStateBroadcast = 0;
        private int nodeStateRequestTimeoutMS = 5 * 60 * 1000;
        private int nodeStateRequestTimeoutEarliestPercentage = 80;
        private int nodeStateRequestTimeoutLatestPercentage = 95;
        private int nodeStateRequestRoundTripTimeMaxSeconds = 5;
        private int minTimeBetweenNewSystemStates = 0;
        private boolean showLocalSystemStatesInEventLog = true;
        private int maxSlobrokDisconnectGracePeriod = 1000;
        private BackOffPolicy slobrokBackOffPolicy = null;
        private Distribution storageDistribution;
        private Set<ConfiguredNode> nodes;
        private Duration maxDeferredTaskVersionWaitTime = Duration.ofSeconds(30);
        private boolean clusterHasGlobalDocumentTypes = false;
        private boolean enableTwoPhaseClusterStateActivation = false;
        private double minMergeCompletionRatio = 1.0;
        private int maxDivergentNodesPrintedInTaskErrorMessages = 10;
        private boolean clusterFeedBlockEnabled = false;
        private Map<String, Double> clusterFeedBlockLimit = Collections.emptyMap();
        private double clusterFeedBlockNoiseLevel = 0.01;

        public Builder(String clusterName, Collection<ConfiguredNode> nodes) {
            this.clusterName = clusterName;
            this.nodes = new TreeSet<>(nodes);
            maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
            maxTransitionTime.put(NodeType.STORAGE, 5000);
        }

        public String clusterName() {
            return clusterName;
        }

        public Builder setClusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public int fleetControllerIndex() {
            return index;
        }

        public Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        public Builder setCount(int count) {
            this.count = count;
            return this;
        }

        public Builder setStateGatherCount(int stateGatherCount) {
            this.stateGatherCount = stateGatherCount;
            return this;
        }

        public String[] slobrokConnectionSpecs() {
            return slobrokConnectionSpecs;
        }

        public Builder setSlobrokConnectionSpecs(String[] slobrokConnectionSpecs) {
            Objects.requireNonNull(slobrokConnectionSpecs, "slobrokConnectionSpecs cannot be null");
            this.slobrokConnectionSpecs = slobrokConnectionSpecs;
            return this;
        }

        public Builder setRpcPort(int rpcPort) {
            this.rpcPort = rpcPort;
            return this;
        }

        public Builder setHttpPort(int httpPort) {
            this.httpPort = httpPort;
            return this;
        }

        public Builder setDistributionBits(int distributionBits) {
            this.distributionBits = distributionBits;
            return this;
        }

        public Builder setZooKeeperSessionTimeout(int zooKeeperSessionTimeout) {
            this.zooKeeperSessionTimeout = zooKeeperSessionTimeout;
            return this;
        }

        public Builder setMasterZooKeeperCooldownPeriod(int masterZooKeeperCooldownPeriod) {
            this.masterZooKeeperCooldownPeriod = masterZooKeeperCooldownPeriod;
            return this;
        }

        public String zooKeeperServerAddress() {
            return zooKeeperServerAddress;
        }

        public Builder setZooKeeperServerAddress(String zooKeeperServerAddress) {
            if (zooKeeperServerAddress == null || "".equals(zooKeeperServerAddress)) {
                throw new IllegalArgumentException("zookeeper server address must be set, was '" + zooKeeperServerAddress + "'");
            }
            this.zooKeeperServerAddress = zooKeeperServerAddress;
            return this;
        }

        public Map<NodeType, Integer> maxTransitionTime() {
            return maxTransitionTime;
        }

        public Builder setMaxTransitionTime(NodeType nodeType, Integer maxTransitionTime) {
            this.maxTransitionTime.put(nodeType, maxTransitionTime);
            return this;
        }

        public int maxInitProgressTime() {
            return maxInitProgressTime;
        }

        public Builder setMaxInitProgressTime(int maxInitProgressTime) {
            this.maxInitProgressTime = maxInitProgressTime;
            return this;
        }

        public int maxPrematureCrashes() {
            return maxPrematureCrashes;
        }

        public Builder setMaxPrematureCrashes(int maxPrematureCrashes) {
            this.maxPrematureCrashes = maxPrematureCrashes;
            return this;
        }

        public long stableStateTimePeriod() {
            return stableStateTimePeriod;
        }

        public Builder setStableStateTimePeriod(long stableStateTimePeriod) {
            this.stableStateTimePeriod = stableStateTimePeriod;
            return this;
        }

        public Builder setEventLogMaxSize(int eventLogMaxSize) {
            this.eventLogMaxSize = eventLogMaxSize;
            return this;
        }

        public Builder setEventNodeLogMaxSize(int eventNodeLogMaxSize) {
            this.eventNodeLogMaxSize = eventNodeLogMaxSize;
            return this;
        }

        public Builder setMinDistributorNodesUp(int minDistributorNodesUp) {
            this.minDistributorNodesUp = minDistributorNodesUp;
            return this;
        }

        public Builder setMinStorageNodesUp(int minStorageNodesUp) {
            this.minStorageNodesUp = minStorageNodesUp;
            return this;
        }

        public Builder setMinRatioOfDistributorNodesUp(double minRatioOfDistributorNodesUp) {
            this.minRatioOfDistributorNodesUp = minRatioOfDistributorNodesUp;
            return this;
        }

        public Builder setMinRatioOfStorageNodesUp(double minRatioOfStorageNodesUp) {
            this.minRatioOfStorageNodesUp = minRatioOfStorageNodesUp;
            return this;
        }

        public Builder setMinNodeRatioPerGroup(double minNodeRatioPerGroup) {
            this.minNodeRatioPerGroup = minNodeRatioPerGroup;
            return this;
        }

        public Builder setCycleWaitTime(int cycleWaitTime) {
            this.cycleWaitTime = cycleWaitTime;
            return this;
        }

        public Builder setMinTimeBeforeFirstSystemStateBroadcast(long minTimeBeforeFirstSystemStateBroadcast) {
            this.minTimeBeforeFirstSystemStateBroadcast = minTimeBeforeFirstSystemStateBroadcast;
            return this;
        }

        public int nodeStateRequestTimeoutMS() {
            return nodeStateRequestTimeoutMS;
        }

        public Builder setNodeStateRequestTimeoutMS(int nodeStateRequestTimeoutMS) {
            this.nodeStateRequestTimeoutMS = nodeStateRequestTimeoutMS;
            return this;
        }

        public Builder setNodeStateRequestTimeoutEarliestPercentage(int nodeStateRequestTimeoutEarliestPercentage) {
            this.nodeStateRequestTimeoutEarliestPercentage = nodeStateRequestTimeoutEarliestPercentage;
            return this;
        }

        public Builder setNodeStateRequestTimeoutLatestPercentage(int nodeStateRequestTimeoutLatestPercentage) {
            this.nodeStateRequestTimeoutLatestPercentage = nodeStateRequestTimeoutLatestPercentage;
            return this;
        }

        public Builder setMinTimeBetweenNewSystemStates(int minTimeBetweenNewSystemStates) {
            this.minTimeBetweenNewSystemStates = minTimeBetweenNewSystemStates;
            return this;
        }

        public Builder setShowLocalSystemStatesInEventLog(boolean showLocalSystemStatesInEventLog) {
            this.showLocalSystemStatesInEventLog = showLocalSystemStatesInEventLog;
            return this;
        }

        public int maxSlobrokDisconnectGracePeriod() {
            return maxSlobrokDisconnectGracePeriod;
        }

        public Builder setMaxSlobrokDisconnectGracePeriod(int maxSlobrokDisconnectGracePeriod) {
            this.maxSlobrokDisconnectGracePeriod = maxSlobrokDisconnectGracePeriod;
            return this;
        }

        public Builder setStorageDistribution(Distribution storageDistribution) {
            this.storageDistribution = storageDistribution;
            return this;
        }

        public Set<ConfiguredNode> nodes() {
            return nodes;
        }

        public Builder setNodes(Set<ConfiguredNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder setMaxDeferredTaskVersionWaitTime(Duration maxDeferredTaskVersionWaitTime) {
            this.maxDeferredTaskVersionWaitTime = maxDeferredTaskVersionWaitTime;
            return this;
        }

        public Builder setClusterHasGlobalDocumentTypes(boolean clusterHasGlobalDocumentTypes) {
            this.clusterHasGlobalDocumentTypes = clusterHasGlobalDocumentTypes;
            return this;
        }

        public Builder enableTwoPhaseClusterStateActivation(boolean enableTwoPhaseClusterStateActivation) {
            this.enableTwoPhaseClusterStateActivation = enableTwoPhaseClusterStateActivation;
            return this;
        }

        public double minMergeCompletionRatio() {
            return minMergeCompletionRatio;
        }

        public Builder setMinMergeCompletionRatio(double minMergeCompletionRatio) {
            this.minMergeCompletionRatio = minMergeCompletionRatio;
            return this;
        }

        public Builder setMaxDivergentNodesPrintedInTaskErrorMessages(int maxDivergentNodesPrintedInTaskErrorMessages) {
            this.maxDivergentNodesPrintedInTaskErrorMessages = maxDivergentNodesPrintedInTaskErrorMessages;
            return this;
        }

        public Builder setClusterFeedBlockEnabled(boolean clusterFeedBlockEnabled) {
            this.clusterFeedBlockEnabled = clusterFeedBlockEnabled;
            return this;
        }

        public Builder setClusterFeedBlockLimit(Map<String, Double> clusterFeedBlockLimit) {
            this.clusterFeedBlockLimit = Map.copyOf(clusterFeedBlockLimit);
            return this;
        }

        public Builder setClusterFeedBlockNoiseLevel(double clusterFeedBlockNoiseLevel) {
            this.clusterFeedBlockNoiseLevel = clusterFeedBlockNoiseLevel;
            return this;
        }

        public FleetControllerOptions build() {
            return new FleetControllerOptions(clusterName,
                                              index,
                                              count,
                                              stateGatherCount,
                                              slobrokConnectionSpecs,
                                              rpcPort,
                                              httpPort,
                                              distributionBits,
                                              zooKeeperSessionTimeout,
                                              masterZooKeeperCooldownPeriod,
                                              zooKeeperServerAddress,
                                              maxTransitionTime,
                                              maxInitProgressTime,
                                              maxPrematureCrashes,
                                              stableStateTimePeriod,
                                              eventLogMaxSize,
                                              eventNodeLogMaxSize,
                                              minDistributorNodesUp,
                                              minStorageNodesUp,
                                              minRatioOfDistributorNodesUp,
                                              minRatioOfStorageNodesUp,
                                              minNodeRatioPerGroup,
                                              cycleWaitTime,
                                              minTimeBeforeFirstSystemStateBroadcast,
                                              nodeStateRequestTimeoutMS,
                                              nodeStateRequestTimeoutEarliestPercentage,
                                              nodeStateRequestTimeoutLatestPercentage,
                                              nodeStateRequestRoundTripTimeMaxSeconds,
                                              minTimeBetweenNewSystemStates,
                                              showLocalSystemStatesInEventLog,
                                              maxSlobrokDisconnectGracePeriod,
                                              slobrokBackOffPolicy,
                                              storageDistribution,
                                              nodes,
                                              maxDeferredTaskVersionWaitTime,
                                              clusterHasGlobalDocumentTypes,
                                              enableTwoPhaseClusterStateActivation,
                                              minMergeCompletionRatio,
                                              maxDivergentNodesPrintedInTaskErrorMessages,
                                              clusterFeedBlockEnabled,
                                              clusterFeedBlockLimit,
                                              clusterFeedBlockNoiseLevel);
        }

        public static Builder copy(FleetControllerOptions options) {
            Builder builder = new Builder(options.clusterName(), options.nodes());
            builder.clusterName = options.clusterName;
            builder.index = options.fleetControllerIndex;
            builder.count = options.fleetControllerCount;
            builder.stateGatherCount = options.stateGatherCount;
            builder.slobrokConnectionSpecs = options.slobrokConnectionSpecs;
            builder.rpcPort = options.rpcPort;
            builder.httpPort = options.httpPort;
            builder.distributionBits = options.distributionBits;
            builder.zooKeeperSessionTimeout = options.zooKeeperSessionTimeout;
            builder.masterZooKeeperCooldownPeriod = options.masterZooKeeperCooldownPeriod;
            builder.zooKeeperServerAddress = options.zooKeeperServerAddress;
            builder.maxTransitionTime = Map.copyOf(options.maxTransitionTime);
            builder.maxInitProgressTime = options.maxInitProgressTime;
            builder.maxPrematureCrashes = options.maxPrematureCrashes;
            builder.stableStateTimePeriod = options.stableStateTimePeriod;
            builder.eventLogMaxSize = options.eventLogMaxSize;
            builder.eventNodeLogMaxSize = options.eventNodeLogMaxSize;
            builder.minDistributorNodesUp = options.minDistributorNodesUp;
            builder.minStorageNodesUp = options.minStorageNodesUp;
            builder.minRatioOfDistributorNodesUp = options.minRatioOfStorageNodesUp;
            builder.minRatioOfStorageNodesUp = options.minRatioOfStorageNodesUp;
            builder.minNodeRatioPerGroup = options.minNodeRatioPerGroup;
            builder.cycleWaitTime = options.cycleWaitTime;
            builder.minTimeBeforeFirstSystemStateBroadcast = options.minTimeBeforeFirstSystemStateBroadcast;
            builder.nodeStateRequestTimeoutMS = options.nodeStateRequestTimeoutMS;
            builder.nodeStateRequestTimeoutEarliestPercentage = options.nodeStateRequestTimeoutEarliestPercentage;
            builder.nodeStateRequestTimeoutLatestPercentage = options.nodeStateRequestTimeoutLatestPercentage;
            builder.nodeStateRequestRoundTripTimeMaxSeconds = options.nodeStateRequestRoundTripTimeMaxSeconds;
            builder.minTimeBetweenNewSystemStates = options.minTimeBetweenNewSystemStates;
            builder.showLocalSystemStatesInEventLog = options.showLocalSystemStatesInEventLog;
            builder.maxSlobrokDisconnectGracePeriod = options.maxSlobrokDisconnectGracePeriod;
            builder.slobrokBackOffPolicy = options.slobrokBackOffPolicy;
            builder.storageDistribution = options.storageDistribution;
            builder.nodes = Set.copyOf(options.nodes);
            builder.maxDeferredTaskVersionWaitTime = options.maxDeferredTaskVersionWaitTime;
            builder.clusterHasGlobalDocumentTypes = options.clusterHasGlobalDocumentTypes;
            builder.enableTwoPhaseClusterStateActivation = options.enableTwoPhaseClusterStateActivation;
            builder.minMergeCompletionRatio = options.minMergeCompletionRatio;
            builder.maxDivergentNodesPrintedInTaskErrorMessages = options.maxDivergentNodesPrintedInTaskErrorMessages;
            builder.clusterFeedBlockEnabled = options.clusterFeedBlockEnabled;
            builder.clusterFeedBlockLimit = Map.copyOf(options.clusterFeedBlockLimit);
            builder.clusterFeedBlockNoiseLevel = options.clusterFeedBlockNoiseLevel;

            return builder;
        }
    }

}
