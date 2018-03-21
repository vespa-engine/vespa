// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.jrt.slobrok.api.BackOffPolicy;
import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

import java.time.Duration;
import java.util.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

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
    public String slobrokConnectionSpecs[];
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
     * If all nodes have reported before this time, the min time is ignored and system state is broadcasted.
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

    // TODO replace this flag with a set of bucket spaces instead
    public boolean enableMultipleBucketSpaces = false;

    // TODO: Choose a default value
    public double minMergeCompletionRatio = 1.0;

    // TODO: Replace usage of this by usage where the nodes are explicitly passed (below)
    public FleetControllerOptions(String clusterName) {
        this.clusterName = clusterName;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
        nodes = new TreeSet<>();
        for (int i = 0; i < 10; i++)
            nodes.add(new ConfiguredNode(i, false));
    }

    public FleetControllerOptions(String clusterName, Collection<ConfiguredNode> nodes) {
        this.clusterName = clusterName;
        maxTransitionTime.put(NodeType.DISTRIBUTOR, 0);
        maxTransitionTime.put(NodeType.STORAGE, 5000);
        this.nodes = new TreeSet<>(nodes);
    }

    /** Called on reconfiguration of this cluster */
    public void setStorageDistribution(Distribution distribution) {
        this.storageDistribution = distribution;
        this.nodes = distribution.getNodes();
    }

    public Duration getMaxDeferredTaskVersionWaitTime() {
        return maxDeferredTaskVersionWaitTime;
    }

    public void setMaxDeferredTaskVersionWaitTime(Duration maxDeferredTaskVersionWaitTime) {
        this.maxDeferredTaskVersionWaitTime = maxDeferredTaskVersionWaitTime;
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

    public void writeHtmlState(StringBuilder sb, StatusPageServer.HttpRequest request) {
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
        sb.append("<tr><td><nobr>Multiple bucket spaces enabled</nobr></td><td align=\"right\">").append(enableMultipleBucketSpaces).append("</td></tr>");

        sb.append("</table>");
    }

}
