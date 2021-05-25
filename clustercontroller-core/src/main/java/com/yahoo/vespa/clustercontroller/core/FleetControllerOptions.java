// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.NodeType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class represents all the options that can be set in the fleetcontroller.
 * Tests typically just generate an instance of this object to use in fleet controller for testing.
 * A real application generate this object from config, and on config updates, post new options to the fleet controller.
 */
public class FleetControllerOptions implements Cloneable {

    private final String fleetControllerConfigId;
    private final String slobrokConfigId;

    public final String clusterName;
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

    private Duration maxDeferredTaskVersionWaitTime = Duration.ofSeconds(30);

    public boolean clusterHasGlobalDocumentTypes = false;

    public boolean enableTwoPhaseClusterStateActivation = false;

    // TODO: Choose a default value
    public double minMergeCompletionRatio = 1.0;

    public int maxDivergentNodesPrintedInTaskErrorMessages = 10;

    public boolean clusterFeedBlockEnabled = false;
    // Resource type -> limit in [0, 1]
    public Map<String, Double> clusterFeedBlockLimit = Collections.emptyMap();

    public double clusterFeedBlockNoiseLevel = 0.01;

    public FleetControllerOptions(String fleetControllerConfigId,
                                  String slobrokConfigId,
                                  String clusterName,
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
                                  int statePollingFrequency,
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
        this.fleetControllerConfigId = fleetControllerConfigId;
        this.slobrokConfigId = slobrokConfigId;
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
        this.statePollingFrequency = statePollingFrequency;
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
        this.cycleWaitTime = cycleWaitTime;
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

    // Getters.  Keep this in order and sync with generateDiffReport().
    public String fleetControllerConfigId() { return fleetControllerConfigId; }
    public int fleetControllerIndex() { return fleetControllerIndex; }
    public String slobrokConfigId() { return slobrokConfigId; }
    public String clusterName() { return clusterName; }
    public int fleetControllerCount() { return fleetControllerCount; }
    public int stateGatherCount() { return stateGatherCount; }
    public String[] slobrokConnectionSpecs() { return slobrokConnectionSpecs; }
    public int rpcPort() { return rpcPort; }
    public int httpPort() { return httpPort; }
    public int distributionBits() { return distributionBits; }
    public int zooKeeperSessionTimeout() { return zooKeeperSessionTimeout; }
    public int masterZooKeeperCooldownPeriod() { return masterZooKeeperCooldownPeriod; }
    public String zooKeeperServerAddress() { return zooKeeperServerAddress; }
    public int statePollingFrequency() { return statePollingFrequency; }
    public Map<NodeType, Integer> maxTransitionTime() { return maxTransitionTime; }
    public int maxInitProgressTime() { return maxInitProgressTime; }
    public int maxPrematureCrashes() { return maxPrematureCrashes; }
    public long stableStateTimePeriod() { return stableStateTimePeriod; }
    public int eventLogMaxSize() { return eventLogMaxSize; }
    public int eventNodeLogMaxSize() { return eventNodeLogMaxSize; }
    public int minDistributorNodesUp() { return minDistributorNodesUp; }
    public int minStorageNodesUp() { return minStorageNodesUp; }
    public double minRatioOfDistributorNodesUp() { return minRatioOfDistributorNodesUp; }
    public double minRatioOfStorageNodesUp() { return minRatioOfStorageNodesUp; }
    public double minNodeRatioPerGroup() { return minNodeRatioPerGroup; }
    public int cycleWaitTime() { return cycleWaitTime; }
    public long minTimeBeforeFirstSystemStateBroadcast() { return minTimeBeforeFirstSystemStateBroadcast; }
    public int nodeStateRequestTimeoutMS() { return nodeStateRequestTimeoutMS; }
    public int nodeStateRequestTimeoutEarliestPercentage() { return nodeStateRequestTimeoutEarliestPercentage; }
    public int nodeStateRequestTimeoutLatestPercentage() { return nodeStateRequestTimeoutLatestPercentage; }
    public int nodeStateRequestRoundTripTimeMaxSeconds() { return nodeStateRequestRoundTripTimeMaxSeconds; }
    public int minTimeBetweenNewSystemStates() { return minTimeBetweenNewSystemStates; }
    public boolean showLocalSystemStatesInEventLog() { return showLocalSystemStatesInEventLog; }
    public int maxSlobrokDisconnectGracePeriod() { return maxSlobrokDisconnectGracePeriod; }
    public BackOffPolicy slobrokBackOffPolicy() { return slobrokBackOffPolicy; }
    public Distribution storageDistribution() { return storageDistribution; }
    public Set<ConfiguredNode> nodes() { return nodes; }
    public Duration maxDeferredTaskVersionWaitTime() { return maxDeferredTaskVersionWaitTime; }
    public boolean clusterHasGlobalDocumentTypes() { return clusterHasGlobalDocumentTypes; }
    public boolean enableTwoPhaseClusterStateActivation() { return enableTwoPhaseClusterStateActivation; }
    public double minMergeCompletionRatio() { return minMergeCompletionRatio; }
    public int maxDivergentNodesPrintedInTaskErrorMessages() { return maxDivergentNodesPrintedInTaskErrorMessages; }
    public boolean clusterFeedBlockEnabled() { return clusterFeedBlockEnabled; }
    public Map<String, Double> clusterFeedBlockLimit() { return clusterFeedBlockLimit; }
    public double clusterFeedBlockNoiseLevel() { return clusterFeedBlockNoiseLevel; }

    private static String generateDiffReport(FleetControllerOptions oldOptions, FleetControllerOptions newOptions) {
        return new Differ(oldOptions, newOptions)
                .addField(FleetControllerOptions::fleetControllerConfigId, "fleetControllerConfigId")
                .addField(FleetControllerOptions::fleetControllerIndex, "fleetControllerIndex")
                .addField(FleetControllerOptions::getMaxDeferredTaskVersionWaitTime, "maxDeferredTaskVersionWaitTime")
                .addField(FleetControllerOptions::clusterName, "clusterName")
                .addField(FleetControllerOptions::fleetControllerCount, "fleetControllerCount")
                .addField(FleetControllerOptions::stateGatherCount, "stateGatherCount")
                .addField(FleetControllerOptions::slobrokConnectionSpecs, "slobrokConnectionSpecs")
                .addField(FleetControllerOptions::rpcPort, "rpcPort")
                .addField(FleetControllerOptions::httpPort, "httpPort")
                .addField(FleetControllerOptions::distributionBits, "distributionBits")
                .addField(FleetControllerOptions::zooKeeperSessionTimeout, "zooKeeperSessionTimeout")
                .addField(FleetControllerOptions::masterZooKeeperCooldownPeriod, "masterZooKeeperCooldownPeriod")
                .addField(FleetControllerOptions::zooKeeperServerAddress, "zooKeeperServerAddress")
                .addField(FleetControllerOptions::statePollingFrequency, "statePollingFrequency")
                .addField(FleetControllerOptions::maxTransitionTime, "maxTransitionTime")
                .addField(FleetControllerOptions::maxInitProgressTime, "maxInitProgressTime")
                .addField(FleetControllerOptions::maxPrematureCrashes, "maxPrematureCrashes")
                .addField(FleetControllerOptions::stableStateTimePeriod, "stableStateTimePeriod")
                .addField(FleetControllerOptions::eventLogMaxSize, "eventLogMaxSize")
                .addField(FleetControllerOptions::eventNodeLogMaxSize, "eventNodeLogMaxSize")
                .addField(FleetControllerOptions::minDistributorNodesUp, "minDistributorNodesUp")
                .addField(FleetControllerOptions::minStorageNodesUp, "minStorageNodesUp")
                .addField(FleetControllerOptions::minRatioOfDistributorNodesUp, "minRatioOfDistributorNodesUp")
                .addField(FleetControllerOptions::minRatioOfStorageNodesUp, "minRatioOfStorageNodesUp")
                .addField(FleetControllerOptions::minNodeRatioPerGroup, "minNodeRatioPerGroup")
                .addField(FleetControllerOptions::cycleWaitTime, "cycleWaitTime")
                .addField(FleetControllerOptions::minTimeBeforeFirstSystemStateBroadcast, "minTimeBeforeFirstSystemStateBroadcast")
                .addField(FleetControllerOptions::nodeStateRequestTimeoutMS, "nodeStateRequestTimeoutMS")
                .addField(FleetControllerOptions::nodeStateRequestTimeoutEarliestPercentage, "nodeStateRequestTimeoutEarliestPercentage")
                .addField(FleetControllerOptions::nodeStateRequestTimeoutLatestPercentage, "nodeStateRequestTimeoutLatestPercentage")
                .addField(FleetControllerOptions::nodeStateRequestRoundTripTimeMaxSeconds, "nodeStateRequestRoundTripTimeMaxSeconds")
                .addField(FleetControllerOptions::minTimeBetweenNewSystemStates, "minTimeBetweenNewSystemStates")
                .addField(FleetControllerOptions::showLocalSystemStatesInEventLog, "showLocalSystemStatesInEventLog")
                .addField(FleetControllerOptions::maxSlobrokDisconnectGracePeriod, "maxSlobrokDisconnectGracePeriod")
                .addField(FleetControllerOptions::slobrokBackOffPolicy, "slobrokBackOffPolicy")
                .addField(FleetControllerOptions::storageDistribution, "storageDistribution")
                .addField(FleetControllerOptions::nodes, "nodes")
                .addField(FleetControllerOptions::maxDeferredTaskVersionWaitTime, "maxDeferredTaskVersionWaitTime")
                .addField(FleetControllerOptions::clusterHasGlobalDocumentTypes, "clusterHasGlobalDocumentTypes")
                .addField(FleetControllerOptions::enableTwoPhaseClusterStateActivation, "enableTwoPhaseClusterStateActivation")
                .addField(FleetControllerOptions::minMergeCompletionRatio, "minMergeCompletionRatio")
                .addField(FleetControllerOptions::maxDivergentNodesPrintedInTaskErrorMessages, "maxDivergentNodesPrintedInTaskErrorMessages")
                .addField(FleetControllerOptions::clusterFeedBlockEnabled, "clusterFeedBlockEnabled")
                .addField(FleetControllerOptions::clusterFeedBlockLimit, "clusterFeedBlockLimit")
                .addField(FleetControllerOptions::clusterFeedBlockNoiseLevel, "clusterFeedBlockNoiseLevel")
                .compileDiff();
    }

    public void setNodes(Set<ConfiguredNode> nodes) {
        this.nodes = nodes;
    }

    public void setClusterFeedBlockEnabled(boolean clusterFeedBlockEnabled) {
        this.clusterFeedBlockEnabled = clusterFeedBlockEnabled;
    }

    public void setClusterFeedBlockLimit(Map<String, Double> clusterFeedBlockLimit) {
        this.clusterFeedBlockLimit = clusterFeedBlockLimit;
    }

    public void setClusterFeedBlockNoiseLevel(double clusterFeedBlockNoiseLevel) {
        this.clusterFeedBlockNoiseLevel = clusterFeedBlockNoiseLevel;
    }

    private static class Differ {
        private final FleetControllerOptions oldOptions;
        private final FleetControllerOptions newOptions;
        private final List<String> diffs = new ArrayList<>();

        public Differ(FleetControllerOptions oldOptions, FleetControllerOptions newOptions) {
            this.oldOptions = oldOptions;
            this.newOptions = newOptions;
        }

        private <T> Differ addField(Function<FleetControllerOptions, T> getter, String name) {
            T oldValue = getter.apply(oldOptions);
            T newValue = getter.apply(newOptions);
            if (!Objects.equals(oldValue, newValue)) {
                diffs.add(name + " = " + oldValue + " -> " + newValue);
            }
            return this;
        }

        /** Returns an empty String if and only if all fields are Object::equals. */
        public String compileDiff() {
            return String.join(", ", diffs);
        }
    }

    public void setZooKeeperServerAddress(String zooKeeperServerAddress) {
        this.zooKeeperServerAddress = zooKeeperServerAddress;
    }

    public void setSlobrokConnectionSpecs(String[] slobrokConnectionSpecs) {
        this.slobrokConnectionSpecs = slobrokConnectionSpecs;
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

    public static String splitZooKeeperAddress(String s) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int index = s.indexOf(',');
            if (index > 0) {
                sb.append(s.substring(0, index + 1)).append(' ');
                s = s.substring(index+1);
            } else {
                break;
            }
        }
        sb.append(s);
        return sb.toString();
    }

    static DecimalFormat DecimalDot2 = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ENGLISH));

    public void writeHtmlState(StringBuilder sb) {
        String slobrokspecs = "";
        for (int i=0; i<slobrokConnectionSpecs.length; ++i) {
            if (i != 0) slobrokspecs += "<br>";
            slobrokspecs += slobrokConnectionSpecs[i];
        }
        sb.append("<h1>Current config</h1>\n")
          .append("<p>Fleet controller config id: ").append(fleetControllerConfigId == null ? null : fleetControllerConfigId.replaceAll("\n", "<br>\n")).append("</p>\n")
          .append("<p>Slobrok config id: ").append(slobrokConfigId == null ? null : slobrokConfigId.replaceAll("\n", "<br>\n")).append("</p>\n")
          .append("<table border=\"1\" cellspacing=\"0\"><tr><th>Property</th><th>Value</th></tr>\n");

        sb.append("<tr><td><nobr>Cluster name</nobr></td><td align=\"right\">").append(clusterName).append("</td></tr>");
        sb.append("<tr><td><nobr>Fleet controller index</nobr></td><td align=\"right\">").append(fleetControllerIndex).append("/").append(fleetControllerCount).append("</td></tr>");
        sb.append("<tr><td><nobr>Number of fleetcontrollers gathering states from nodes</nobr></td><td align=\"right\">").append(stateGatherCount).append("</td></tr>");

        sb.append("<tr><td><nobr>Slobrok connection spec</nobr></td><td align=\"right\">").append(slobrokspecs).append("</td></tr>");
        sb.append("<tr><td><nobr>RPC port</nobr></td><td align=\"right\">").append(rpcPort == 0 ? "Pick random available" : rpcPort).append("</td></tr>");
        sb.append("<tr><td><nobr>HTTP port</nobr></td><td align=\"right\">").append(httpPort == 0 ? "Pick random available" : httpPort).append("</td></tr>");
        sb.append("<tr><td><nobr>Master cooldown period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(masterZooKeeperCooldownPeriod)).append("</td></tr>");
        String zooKeeperAddress = (zooKeeperServerAddress == null ? "Not using Zookeeper" : splitZooKeeperAddress(zooKeeperServerAddress));
        sb.append("<tr><td><nobr>Zookeeper server address</nobr></td><td align=\"right\">").append(zooKeeperAddress).append("</td></tr>");
        sb.append("<tr><td><nobr>Zookeeper session timeout</nobr></td><td align=\"right\">").append(RealTimer.printDuration(zooKeeperSessionTimeout)).append("</td></tr>");

        sb.append("<tr><td><nobr>Cycle wait time</nobr></td><td align=\"right\">").append(cycleWaitTime).append(" ms</td></tr>");
        sb.append("<tr><td><nobr>Minimum time before first clusterstate broadcast as master</nobr></td><td align=\"right\">").append(RealTimer.printDuration(minTimeBeforeFirstSystemStateBroadcast)).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum time between official cluster states</nobr></td><td align=\"right\">").append(RealTimer.printDuration(minTimeBetweenNewSystemStates)).append("</td></tr>");
        sb.append("<tr><td><nobr>Slobrok mirror backoff policy</nobr></td><td align=\"right\">").append(slobrokBackOffPolicy == null ? "default" : "overridden").append("</td></tr>");

        sb.append("<tr><td><nobr>Node state request timeout</nobr></td><td align=\"right\">").append(RealTimer.printDuration(nodeStateRequestTimeoutMS)).append("</td></tr>");
        sb.append("<tr><td><nobr>VDS 4.1 node state polling frequency</nobr></td><td align=\"right\">").append(RealTimer.printDuration(statePollingFrequency)).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum distributor transition time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(maxTransitionTime.get(NodeType.DISTRIBUTOR))).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum storage transition time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(maxTransitionTime.get(NodeType.STORAGE))).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum initialize without progress time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(maxInitProgressTime)).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum premature crashes</nobr></td><td align=\"right\">").append(maxPrematureCrashes).append("</td></tr>");
        sb.append("<tr><td><nobr>Stable state time period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(stableStateTimePeriod)).append("</td></tr>");
        sb.append("<tr><td><nobr>Slobrok disconnect grace period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(maxSlobrokDisconnectGracePeriod)).append("</td></tr>");

        sb.append("<tr><td><nobr>Number of distributor nodes</nobr></td><td align=\"right\">").append(nodes == null ? "Autodetect" : nodes.size()).append("</td></tr>");
        sb.append("<tr><td><nobr>Number of storage nodes</nobr></td><td align=\"right\">").append(nodes == null ? "Autodetect" : nodes.size()).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum distributor nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(minDistributorNodesUp).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum storage nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(minStorageNodesUp).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum percentage of distributor nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(DecimalDot2.format(100 * minRatioOfDistributorNodesUp)).append(" %</td></tr>");
        sb.append("<tr><td><nobr>Minimum percentage of storage nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(DecimalDot2.format(100 * minRatioOfStorageNodesUp)).append(" %</td></tr>");

        sb.append("<tr><td><nobr>Show local cluster state changes</nobr></td><td align=\"right\">").append(showLocalSystemStatesInEventLog).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum event log size</nobr></td><td align=\"right\">").append(eventLogMaxSize).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum node event log size</nobr></td><td align=\"right\">").append(eventNodeLogMaxSize).append("</td></tr>");
        sb.append("<tr><td><nobr>Wanted distribution bits</nobr></td><td align=\"right\">").append(distributionBits).append("</td></tr>");
        sb.append("<tr><td><nobr>Max deferred task version wait time</nobr></td><td align=\"right\">").append(maxDeferredTaskVersionWaitTime.toMillis()).append("ms</td></tr>");
        sb.append("<tr><td><nobr>Cluster has global document types configured</nobr></td><td align=\"right\">").append(clusterHasGlobalDocumentTypes).append("</td></tr>");
        sb.append("<tr><td><nobr>Enable 2-phase cluster state activation protocol</nobr></td><td align=\"right\">").append(enableTwoPhaseClusterStateActivation).append("</td></tr>");
        sb.append("<tr><td><nobr>Cluster auto feed block on resource exhaustion enabled</nobr></td><td align=\"right\">")
                        .append(clusterFeedBlockEnabled).append("</td></tr>");
        sb.append("<tr><td><nobr>Feed block limits</nobr></td><td align=\"right\">")
                        .append(clusterFeedBlockLimit.entrySet().stream()
                                .map(kv -> String.format("%s: %.2f%%", kv.getKey(), kv.getValue() * 100.0))
                                .collect(Collectors.joining("<br/>"))).append("</td></tr>");

        sb.append("</table>");
    }

    public Builder toBuilder() {
        return new Builder(clusterName, nodes)
                .setFleetControllerConfigId(fleetControllerConfigId)
                .setSlobrokConfigId(slobrokConfigId)
                .setFleetControllerIndex(fleetControllerIndex)
                .setFleetControllerCount(fleetControllerCount)
                .setStateGatherCount(stateGatherCount)
                .setSlobrokConnectionSpecs(slobrokConnectionSpecs)
                .setRpcPort(rpcPort)
                .setHttpPort(httpPort)
                .setDistributionBits(distributionBits)
                .setZooKeeperSessionTimeout(zooKeeperSessionTimeout)
                .setMasterZooKeeperCooldownPeriod(masterZooKeeperCooldownPeriod)
                .setZooKeeperServerAddress(zooKeeperServerAddress)
                .setStatePollingFrequency(statePollingFrequency)
                .setMaxTransitionTime(maxTransitionTime)
                .setMaxInitProgressTime(maxInitProgressTime)
                .setMaxPrematureCrashes(maxPrematureCrashes)
                .setStableStateTimePeriod(stableStateTimePeriod)
                .setEventLogMaxSize(eventLogMaxSize)
                .setEventNodeLogMaxSize(eventNodeLogMaxSize)
                .setMinDistributorNodesUp(minDistributorNodesUp)
                .setMinStorageNodesUp(minStorageNodesUp)
                .setMinRatioOfDistributorNodesUp(minRatioOfDistributorNodesUp)
                .setMinRatioOfStorageNodesUp(minRatioOfStorageNodesUp)
                .setMinNodeRatioPerGroup(minNodeRatioPerGroup)
                .setCycleWaitTime(cycleWaitTime)
                .setMinTimeBeforeFirstSystemStateBroadcast(minTimeBeforeFirstSystemStateBroadcast)
                .setNodeStateRequestTimeoutMS(nodeStateRequestTimeoutMS)
                .setNodeStateRequestTimeoutEarliestPercentage(nodeStateRequestTimeoutEarliestPercentage)
                .setNodeStateRequestTimeoutLatestPercentage(nodeStateRequestTimeoutLatestPercentage)
                .setNodeStateRequestRoundTripTimeMaxSeconds(nodeStateRequestRoundTripTimeMaxSeconds)
                .setMinTimeBetweenNewSystemStates(minTimeBetweenNewSystemStates)
                .setShowLocalSystemStatesInEventLog(showLocalSystemStatesInEventLog)
                .setMaxSlobrokDisconnectGracePeriod(maxSlobrokDisconnectGracePeriod)
                .setSlobrokBackOffPolicy(slobrokBackOffPolicy)
                .setStorageDistribution(storageDistribution)
                .setMaxDeferredTaskVersionWaitTime(maxDeferredTaskVersionWaitTime)
                .setClusterHasGlobalDocumentTypes(clusterHasGlobalDocumentTypes)
                .setEnableTwoPhaseClusterStateActivation(enableTwoPhaseClusterStateActivation)
                .setMinMergeCompletionRatio(minMergeCompletionRatio)
                .setMaxDivergentNodesPrintedInTaskErrorMessages(maxDivergentNodesPrintedInTaskErrorMessages)
                .setClusterFeedBlockEnabled(clusterFeedBlockEnabled)
                .setClusterFeedBlockLimit(clusterFeedBlockLimit)
                .setClusterFeedBlockNoiseLevel(clusterFeedBlockNoiseLevel);
    }

    public static class Builder {
        private String fleetControllerConfigId;
        private String slobrokConfigId;
        private String clusterName;
        private int fleetControllerIndex = 0;
        private int fleetControllerCount = 1;
        private int stateGatherCount = 2;
        private String[] slobrokConnectionSpecs;
        private int rpcPort = 0;
        private int httpPort = 0;
        private int distributionBits = 16;
        private int zooKeeperSessionTimeout = 5 * 60 * 1000;
        private int masterZooKeeperCooldownPeriod = 15 * 1000;
        private String zooKeeperServerAddress = null;
        private int statePollingFrequency = 5000;
        private final Map<NodeType, Integer> maxTransitionTime = new TreeMap<>();
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
        private Map<String, Double> clusterFeedBlockLimit = Map.of();
        private double clusterFeedBlockNoiseLevel = 0.01;

        public Builder(String clusterName, Collection<ConfiguredNode> nodes) {
            this.clusterName = Objects.requireNonNull(clusterName, "clusterName cannot be null");
            this.nodes = new TreeSet<>(Objects.requireNonNull(nodes, "nodes cannot be null"));

            maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
            maxTransitionTime.put(NodeType.STORAGE, 5000);
        }

        public Builder setFleetControllerConfigId(String fleetControllerConfigId) {
            this.fleetControllerConfigId = fleetControllerConfigId;
            return this;
        }

        public Builder setSlobrokConfigId(String slobrokConfigId) {
            this.slobrokConfigId = slobrokConfigId;
            return this;
        }

        public Builder setClusterName(String clusterName) {
            this.clusterName = Objects.requireNonNull(clusterName);
            return this;
        }

        public Builder setFleetControllerIndex(int fleetControllerIndex) {
            this.fleetControllerIndex = fleetControllerIndex;
            return this;
        }

        public Builder setFleetControllerCount(int fleetControllerCount) {
            this.fleetControllerCount = fleetControllerCount;
            return this;
        }

        public Builder setStateGatherCount(int stateGatherCount) {
            this.stateGatherCount = stateGatherCount;
            return this;
        }

        public Builder setSlobrokConnectionSpecs(String[] slobrokConnectionSpecs) {
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

        public Builder setZooKeeperServerAddress(String zooKeeperServerAddress) {
            this.zooKeeperServerAddress = zooKeeperServerAddress;
            return this;
        }

        public Builder setStatePollingFrequency(int statePollingFrequency) {
            this.statePollingFrequency = statePollingFrequency;
            return this;
        }

        public Builder setMaxTransitionTime(Map<NodeType, Integer> maxTransitionTime) {
            this.maxTransitionTime.clear();
            this.maxTransitionTime.putAll(maxTransitionTime);
            return this;
        }

        public Builder setMaxTransitionTime(NodeType nodeType, int transitionTime) {
            maxTransitionTime.put(nodeType, transitionTime);
            return this;
        }

        public Builder setMaxInitProgressTime(int maxInitProgressTime) {
            this.maxInitProgressTime = maxInitProgressTime;
            return this;
        }

        public Builder setMaxPrematureCrashes(int maxPrematureCrashes) {
            this.maxPrematureCrashes = maxPrematureCrashes;
            return this;
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

        public Builder setNodeStateRequestRoundTripTimeMaxSeconds(int nodeStateRequestRoundTripTimeMaxSeconds) {
            this.nodeStateRequestRoundTripTimeMaxSeconds = nodeStateRequestRoundTripTimeMaxSeconds;
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

        public Builder setMaxSlobrokDisconnectGracePeriod(int maxSlobrokDisconnectGracePeriod) {
            this.maxSlobrokDisconnectGracePeriod = maxSlobrokDisconnectGracePeriod;
            return this;
        }

        public Builder setSlobrokBackOffPolicy(BackOffPolicy slobrokBackOffPolicy) {
            this.slobrokBackOffPolicy = slobrokBackOffPolicy;
            return this;
        }

        public Builder setStorageDistribution(Distribution storageDistribution) {
            this.storageDistribution = storageDistribution;
            return this;
        }

        public Builder setNodes(Set<ConfiguredNode> nodes) {
            this.nodes = Objects.requireNonNull(nodes);
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

        public Builder setEnableTwoPhaseClusterStateActivation(boolean enableTwoPhaseClusterStateActivation) {
            this.enableTwoPhaseClusterStateActivation = enableTwoPhaseClusterStateActivation;
            return this;
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
            this.clusterFeedBlockLimit = clusterFeedBlockLimit;
            return this;
        }

        public Builder setClusterFeedBlockNoiseLevel(double clusterFeedBlockNoiseLevel) {
            this.clusterFeedBlockNoiseLevel = clusterFeedBlockNoiseLevel;
            return this;
        }

        public FleetControllerOptions build() {
            return new FleetControllerOptions(fleetControllerConfigId,
                                              slobrokConfigId,
                                              clusterName,
                                              fleetControllerIndex,
                                              fleetControllerCount,
                                              stateGatherCount,
                                              slobrokConnectionSpecs,
                                              rpcPort,
                                              httpPort,
                                              distributionBits,
                                              zooKeeperSessionTimeout,
                                              masterZooKeeperCooldownPeriod,
                                              zooKeeperServerAddress,
                                              statePollingFrequency,
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
    }

}
