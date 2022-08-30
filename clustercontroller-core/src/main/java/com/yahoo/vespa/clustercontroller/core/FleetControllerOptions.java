// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.NodeType;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This class represents all the options that can be set in the fleetcontroller.
 * Tests typically just generate an instance of this object to use in fleet controller for testing.
 * A real application generate this object from config, and on config updates, post new options to the fleet controller.
 */
public class FleetControllerOptions implements Cloneable {

    // TODO: Make fields private

    public String fleetControllerConfigId;
    public String slobrokConfigId;

    public String clusterName;
    public int fleetControllerIndex = 0;
    public int fleetControllerCount = 1;
    public int stateGatherCount = 2;

    // TODO: This cannot be null but nonnull is not verified
    public String[] slobrokConnectionSpecs;
    public int rpcPort = 0;
    public int httpPort = 0;
    public int distributionBits = 16;

    /** Timeout before breaking zookeeper session (in milliseconds) */
    public int zooKeeperSessionTimeout = 5 * 60 * 1000;
    /**
     * Timeout between master disappearing before new master will take over.
     * (Grace period to allow old master to detect that it is disconnected from zookeeper)
     */
    public int masterZooKeeperCooldownPeriod = 15 * 1000;

    public String zooKeeperServerAddress = null;

    public int statePollingFrequency = 5000;
    /**
     * Max amount of time to keep a node, that has previously been available
     * in steady state, in maintenance mode, while node is unreachable, before setting it down.
     */
    public Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();

    /**
     * Max amount of time to keep a storage node, that is initializing, in maintenance mode, without any further
     * initializing progress being received, before setting it down.
     */
    public int maxInitProgressTime = 5000;

    public int maxPrematureCrashes = 4;
    public long stableStateTimePeriod = 2 * 60 * 60 * 1000;

    public int eventLogMaxSize = 1024;
    public int eventNodeLogMaxSize = 1024;

    public int minDistributorNodesUp = 1;
    public int minStorageNodesUp = 1;
    public double minRatioOfDistributorNodesUp = 0.50;
    public double minRatioOfStorageNodesUp = 0.50;

    /**
     * Minimum ratio of nodes in an "available" state (up, initializing or maintenance)
     * that shall be present in a group for the group itself to be considered available.
     * If the ratio of available nodes drop under this limit, the group's nodes will be
     * implicitly taken down.
     *
     * A value of 0.0 implies group auto-takedown feature is effectively disabled.
     */
    public double minNodeRatioPerGroup = 0.0;

    /**
     * Milliseconds to sleep after doing a work cycle where we did no work. Some events do not interrupt the sleeping,
     * such as slobrok changes, so shouldn't set this too high.
     */
    public int cycleWaitTime = 100;
    /**
     * Minimum time to pass (in milliseconds) before broadcasting our first systemstate. Set small in unit tests,
     * but should be a few seconds in a real system to prevent new nodes taking over from disturbing the system by
     * putting out a different systemstate just because all nodes don't answer witihin a single cycle.
     * The cluster state is allowed to be broadcasted before this time if all nodes have successfully
     * reported their state in Slobrok and getnodestate. This value should typically be in the order of
     * maxSlobrokDisconnectGracePeriod and nodeStateRequestTimeoutMS.
     */
    public long minTimeBeforeFirstSystemStateBroadcast = 0;

    /**
     * StateRequestTimeout for the request are randomized a bit to avoid congestion on replies. The effective
     * interval is
     * [nodeStateRequestTimeoutEarliestPercentage * nodeStateRequestTimeoutMS / 100,
     *                          nodeStateRequestTimeoutLatestPercentage * nodeStateRequestTimeoutMS / 100].
     */
    public int nodeStateRequestTimeoutMS = 5 * 60 * 1000;
    public int nodeStateRequestTimeoutEarliestPercentage = 80;
    public int nodeStateRequestTimeoutLatestPercentage = 95;
    public int nodeStateRequestRoundTripTimeMaxSeconds = 5;

    public int minTimeBetweenNewSystemStates = 0;
    public boolean showLocalSystemStatesInEventLog = true;

    /** Maximum time a node can be missing from slobrok before it is tagged down. */
    public int maxSlobrokDisconnectGracePeriod = 1000;

    /** Set by tests to retry often. */
    public BackOffPolicy slobrokBackOffPolicy = null;

    public Distribution storageDistribution;

    // TODO: Get rid of this by always getting nodes by distribution.getNodes()
    public Set<ConfiguredNode> nodes;

    public Duration maxDeferredTaskVersionWaitTime = Duration.ofSeconds(30);

    public boolean clusterHasGlobalDocumentTypes = false;

    public boolean enableTwoPhaseClusterStateActivation = false;

    // TODO: Choose a default value
    public double minMergeCompletionRatio = 1.0;

    public int maxDivergentNodesPrintedInTaskErrorMessages = 10;

    public boolean clusterFeedBlockEnabled = false;
    // Resource type -> limit in [0, 1]
    public Map<String, Double> clusterFeedBlockLimit = Collections.emptyMap();

    public double clusterFeedBlockNoiseLevel = 0.01;

    public FleetControllerOptions(String clusterName, Collection<ConfiguredNode> nodes) {
        this.clusterName = clusterName;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
        this.nodes = new TreeSet<>(nodes);
    }

    /** Called on reconfiguration of this cluster */
    public void setStorageDistribution(Distribution distribution) {
        this.storageDistribution = distribution;
    }

    public Duration getMaxDeferredTaskVersionWaitTime() {
        return maxDeferredTaskVersionWaitTime;
    }

    public void setMaxDeferredTaskVersionWaitTime(Duration maxDeferredTaskVersionWaitTime) {
        this.maxDeferredTaskVersionWaitTime = maxDeferredTaskVersionWaitTime;
    }

    public long storageNodeMaxTransitionTimeMs() {
        return maxTransitionTime.getOrDefault(NodeType.STORAGE, 10_000);
    }

    public FleetControllerOptions clone() {
        try {
            // TODO: This should deep clone
            return (FleetControllerOptions) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Will not happen");
        }
    }

}
