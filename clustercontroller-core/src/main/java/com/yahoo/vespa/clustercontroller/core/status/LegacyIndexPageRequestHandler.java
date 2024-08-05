// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.distribution.ConfiguredNode;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.ClusterStateBundle;
import com.yahoo.vespa.clustercontroller.core.ClusterStateHistoryEntry;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.EventLog;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.GlobalBucketSyncStatsCalculator;
import com.yahoo.vespa.clustercontroller.core.LeafGroups;
import com.yahoo.vespa.clustercontroller.core.MasterElectionHandler;
import com.yahoo.vespa.clustercontroller.core.NodeInfo;
import com.yahoo.vespa.clustercontroller.core.RealTimer;
import com.yahoo.vespa.clustercontroller.core.StateVersionTracker;
import com.yahoo.vespa.clustercontroller.core.Timer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.HtmlTable;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.VdsClusterHtmlRenderer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
* @author Haakon Humberset
*/
public class LegacyIndexPageRequestHandler implements StatusPageServer.RequestHandler {

    private static final DecimalFormat DecimalDot2 = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.ENGLISH));

    private final Timer timer;
    private final ContentCluster cluster;
    private final MasterElectionHandler masterElectionHandler;
    private final StateVersionTracker stateVersionTracker;
    private final EventLog eventLog;
    private final long startedTime;

    private FleetControllerOptions options;

    public LegacyIndexPageRequestHandler(Timer timer,
                                         ContentCluster cluster,
                                         MasterElectionHandler masterElectionHandler,
                                         StateVersionTracker stateVersionTracker,
                                         EventLog eventLog,
                                         FleetControllerOptions options) {
        this.timer = timer;
        this.cluster = cluster;
        this.masterElectionHandler = masterElectionHandler;
        this.stateVersionTracker = stateVersionTracker;
        this.eventLog = eventLog;
        this.startedTime = timer.getCurrentTimeInMillis();
        this.options = options;
    }

    public void propagateOptions(FleetControllerOptions options) {
        this.options = options;
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        long currentTime = timer.getCurrentTimeInMillis();

        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("text/html");
        StringBuilder content = new StringBuilder();
        content.append("<!-- Answer to request " + request + " -->\n");
        response.writeHtmlHeader(content, cluster.getName() + " Cluster Controller " + options.fleetControllerIndex() + " Status Page");
        content.append("<p><font size=\"-1\">")
                .append(" [ <a href=\"#config\">Current config</a>")
                .append(" | <a href=\"#clusterstates\">Cluster states</a>")
                .append(" | <a href=\"#eventlog\">Event log</a>")
                .append(" ]</font></p>\n");
        content.append("<table><tr><td>UTC time when creating this page:</td><td align=\"right\">").append(RealTimer.printDateNoMilliSeconds(currentTime, tz)).append("</td></tr>");
        content.append("<tr><td>Cluster controller uptime:</td><td align=\"right\">" + RealTimer.printDuration(currentTime - startedTime) + "</td></tr></table>");
        if (masterElectionHandler.isFirstInLine()) {
            // Table overview of all the nodes
            writeHtmlState(cluster, content, timer, stateVersionTracker, options, eventLog);
            // Current cluster state and cluster state history
            writeHtmlState(stateVersionTracker, content);
        } else {
            // Overview of current config
            writeHtmlState(content, options);
        }
        // State of master election
        masterElectionHandler.writeHtmlState(content);
        // Overview of current config
        writeHtmlState(content, options);
        // Event log
        eventLog.writeHtmlState(content, null);
        response.writeHtmlFooter(content, "");
        response.writeContent(content.toString());

        return response;
    }

    @Override
    public String pattern() { return "^/$"; }

    public void writeHtmlState(StateVersionTracker stateVersionTracker, StringBuilder sb) {
        sb.append("<h2 id=\"clusterstates\">Cluster states</h2>\n");
        writeClusterStates(sb, stateVersionTracker.getVersionedClusterStateBundle());
        writeDistributionConfig(sb, stateVersionTracker.getVersionedClusterStateBundle());

        if ( ! stateVersionTracker.getClusterStateHistory().isEmpty()) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            sb.append("<h3 id=\"clusterstatehistory\">Cluster state history</h3>\n");
            sb.append("<table border=\"1\" cellspacing=\"0\"><tr>\n")
              .append("  <th>Creation date (").append(tz.getDisplayName(false, TimeZone.SHORT)).append(")</th>\n")
              .append("  <th>Bucket space</th>\n")
              .append("  <th>Cluster state</th>\n")
              .append("</tr>\n");
            // Write cluster state history in descending time point order (newest on top)
            for (var historyEntry : stateVersionTracker.getClusterStateHistory()) {
                writeClusterStateEntry(historyEntry, sb, tz);
            }
            sb.append("</table>\n");
        }
    }

    private static void writeClusterStates(StringBuilder sb, ClusterStateBundle clusterStates) {
        sb.append("<p>Baseline cluster state:<br><code>")
          .append(escaped(clusterStates.getBaselineClusterState().toString()))
          .append("</code></p>\n");
        clusterStates.getDerivedBucketSpaceStates().forEach((bucketSpace, state) -> {
            sb.append("<p>").append(bucketSpace).append(" cluster state:<br><code>")
              .append(escaped(state.getClusterState().toString()))
              .append("</code></p>\n");
        });
    }

    private static void writeDistributionConfig(StringBuilder sb, ClusterStateBundle stateBundle) {
        // If distribution config is not pushed by the cluster controller, the state bundle will
        // not contain it. To avoid confusing output, simply avoid printing anything in this case.
        if (stateBundle.distributionConfig().isEmpty()) {
            return;
        }
        sb.append("<h3 id=\"distribution-config\">Current distribution config</h3>\n<p>")
          .append(escaped(stateBundle.distributionConfig().get().highLevelDescription()))
          .append("</p>\n");
    }

    private void writeClusterStateEntry(ClusterStateHistoryEntry entry, StringBuilder sb, TimeZone tz) {
        sb.append("<tr><td rowspan=\"").append(entry.getRawStates().size()).append("\">")
          .append(RealTimer.printDate(entry.time(), tz)).append("</td>");

        for (var space : entry.getRawStates().keySet()) {
            if (!space.equals(ClusterStateHistoryEntry.BASELINE)) { // Always ordered first
                sb.append("<tr>");
            }
            writeClusterStateTransition(space, entry.getStateString(space), entry.getDiffString(space),
                                        entry.getStateString(ClusterStateHistoryEntry.BASELINE),
                                        entry.getDiffString(ClusterStateHistoryEntry.BASELINE), sb);
        }
    }

    private void writeClusterStateTransition(String bucketSpace, String state, String diff,
                                             String baselineState, String baselineDiff, StringBuilder sb) {
        sb.append("<td align=\"center\">").append(bucketSpace).append("</td><td>");
        if (!bucketSpace.equals(ClusterStateHistoryEntry.BASELINE) &&
            state.equals(baselineState) && diff.equals(baselineDiff))
        {
            // Don't bother duplicating output for non-baseline states if they exactly match
            // what's being output for the baseline state.
            sb.append("<span style=\"color: gray\">(identical to baseline state)</span>");
        } else {
            sb.append(state);
            if (!diff.isEmpty()) {
                sb.append("<br><b>Diff</b>: ").append(diff);
            }
        }
        sb.append("</td></tr>\n");
    }

    private void writeHtmlState(ContentCluster cluster,
                                StringBuilder sb,
                                Timer timer,
                                StateVersionTracker stateVersionTracker,
                                FleetControllerOptions options,
                                EventLog eventLog) {
        VdsClusterHtmlRenderer renderer = new VdsClusterHtmlRenderer();
        VdsClusterHtmlRenderer.Table table = renderer.createNewClusterHtmlTable(cluster.getName(), cluster.getSlobrokGenerationCount());

        ClusterStateBundle state = stateVersionTracker.getVersionedClusterStateBundle();
        renderClusterFeedBlockIfPresent(state, table);
        renderClusterOutOfSyncRatio(state, stateVersionTracker, table);

        List<Group> groups = LeafGroups.enumerateFrom(cluster.getDistribution().getRootGroup());
        for (Group group : groups) {
            assert (group != null);
            String localName = group.getUnixStylePath();
            assert (localName != null);
            TreeMap<Integer, NodeInfo> storageNodeInfoByIndex = new TreeMap<>();
            TreeMap<Integer, NodeInfo> distributorNodeInfoByIndex = new TreeMap<>();
            for (ConfiguredNode configuredNode : group.getNodes()) {
                storeNodeInfo(cluster, configuredNode.index(), NodeType.STORAGE, storageNodeInfoByIndex);
                storeNodeInfo(cluster, configuredNode.index(), NodeType.DISTRIBUTOR, distributorNodeInfoByIndex);
            }
            table.renderNodes(storageNodeInfoByIndex,
                              distributorNodeInfoByIndex,
                              timer,
                              state,
                              stateVersionTracker.getAggregatedClusterStats(),
                              options.minMergeCompletionRatio(),
                              options.maxPrematureCrashes(),
                              options.clusterFeedBlockLimit(),
                              eventLog,
                              cluster.getName(),
                              localName);
        }
        table.addTable(sb, options.stableStateTimePeriod());
    }

    private static void renderClusterFeedBlockIfPresent(ClusterStateBundle state, VdsClusterHtmlRenderer.Table table) {
        if (state.clusterFeedIsBlocked()) { // Implies FeedBlock != null
            table.appendRaw("<h3 style=\"color: red\">Cluster feeding is blocked!</h3>\n");
            table.appendRaw(String.format("<p>Summary: <strong>%s</strong></p>\n",
                                          escaped(state.getFeedBlockOrNull().getDescription())));
        }
    }

    private static void renderClusterOutOfSyncRatio(ClusterStateBundle state, StateVersionTracker stateVersionTracker,
                                                    VdsClusterHtmlRenderer.Table table) {
        var stats = stateVersionTracker.getAggregatedClusterStats().getAggregatedStats();
        if (!stats.hasUpdatesFromAllDistributors()) {
            table.appendRaw("<p>Current cluster out of sync ratio cannot be computed, as not all " +
                            "distributors have reported in statistics for the most recent cluster state.</p>\n");
            return;
        }
        var outOfSync = GlobalBucketSyncStatsCalculator.clusterBucketsOutOfSyncRatio(stats.getGlobalStats());
        if (outOfSync.isEmpty()) {
            table.appendRaw("<p>Current cluster out of sync ratio cannot be computed, as not all " +
                            "distributors have reported valid statistics.</p>\n");
            return;
        }
        boolean hasMaintenance = stateHasAtLeastOneMaintenanceNode(state);
        if (!hasMaintenance && outOfSync.get() == 0.0) {
            table.appendRaw("<p>Cluster is currently in sync.</p>\n");
        } else {
            table.appendRaw("<p>Cluster is currently <strong>%.2f%% out of sync</strong>.</p>\n".formatted(outOfSync.get() * 100.0));
            if (hasMaintenance) {
                // It is intentional that a cluster with no pending buckets but with nodes in maintenance mode rather
                // emits "0% out of sync" with a caveat rather than "in sync", as we don't know the latter for sure.
                table.appendRaw("<p><strong>Note:</strong> since one or more nodes are currently in " +
                                "Maintenance mode, the true out of sync ratio may be higher.</p>\n");
            }
        }
    }

    private static boolean stateHasAtLeastOneMaintenanceNode(ClusterStateBundle state) {
        var baseline = state.getBaselineClusterState();
        int nodes = baseline.getNodeCount(NodeType.STORAGE);
        for (int i = 0; i < nodes; ++i) {
            if (baseline.getNodeState(Node.ofStorage(i)).getState().oneOf("m")) {
                return true;
            }
        }
        return false;
    }

    private void storeNodeInfo(ContentCluster cluster, int nodeIndex, NodeType nodeType, Map<Integer, NodeInfo> nodeInfoByIndex) {
        NodeInfo nodeInfo = cluster.getNodeInfo(new Node(nodeType, nodeIndex));
        if (nodeInfo == null) return;
        nodeInfoByIndex.put(nodeIndex, nodeInfo);
    }

    public void writeHtmlState(StringBuilder sb, FleetControllerOptions options) {
        String slobrokspecs = "";
        for (int i = 0; i < options.slobrokConnectionSpecs().length; ++i) {
            if (i != 0) slobrokspecs += "<br>";
            slobrokspecs += options.slobrokConnectionSpecs()[i];
        }
        sb.append("<h1>Current config</h1>\n")
          .append("<table border=\"1\" cellspacing=\"0\"><tr><th>Property</th><th>Value</th></tr>\n");

        sb.append("<tr><td><nobr>Cluster name</nobr></td><td align=\"right\">").append(escaped(options.clusterName())).append("</td></tr>");
        sb.append("<tr><td><nobr>Fleet controller index</nobr></td><td align=\"right\">").append(options.fleetControllerIndex()).append("/").append(options.fleetControllerCount()).append("</td></tr>");

        sb.append("<tr><td><nobr>Slobrok connection spec</nobr></td><td align=\"right\">").append(escaped(slobrokspecs)).append("</td></tr>");
        sb.append("<tr><td><nobr>RPC port</nobr></td><td align=\"right\">").append(options.rpcPort() == 0 ? "Pick random available" : options.rpcPort()).append("</td></tr>");
        sb.append("<tr><td><nobr>HTTP port</nobr></td><td align=\"right\">").append(options.httpPort() == 0 ? "Pick random available" : options.httpPort()).append("</td></tr>");
        sb.append("<tr><td><nobr>Master cooldown period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.masterZooKeeperCooldownPeriod())).append("</td></tr>");
        String zooKeeperAddress = splitZooKeeperAddress(options.zooKeeperServerAddress());
        sb.append("<tr><td><nobr>Zookeeper server address</nobr></td><td align=\"right\">").append(escaped(zooKeeperAddress)).append("</td></tr>");
        sb.append("<tr><td><nobr>Zookeeper session timeout</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.zooKeeperSessionTimeout())).append("</td></tr>");

        sb.append("<tr><td><nobr>Cycle wait time</nobr></td><td align=\"right\">").append(options.cycleWaitTime()).append(" ms</td></tr>");
        sb.append("<tr><td><nobr>Minimum time before first clusterstate broadcast as master</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.minTimeBeforeFirstSystemStateBroadcast())).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum time between official cluster states</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.minTimeBetweenNewSystemStates())).append("</td></tr>");

        sb.append("<tr><td><nobr>Node state request timeout</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.nodeStateRequestTimeoutMS())).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum distributor transition time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.maxTransitionTime().get(NodeType.DISTRIBUTOR))).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum storage transition time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.maxTransitionTime().get(NodeType.STORAGE))).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum initialize without progress time</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.maxInitProgressTime())).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum premature crashes</nobr></td><td align=\"right\">").append(options.maxPrematureCrashes()).append("</td></tr>");
        sb.append("<tr><td><nobr>Stable state time period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.stableStateTimePeriod())).append("</td></tr>");
        sb.append("<tr><td><nobr>Slobrok disconnect grace period</nobr></td><td align=\"right\">").append(RealTimer.printDuration(options.maxSlobrokDisconnectGracePeriod())).append("</td></tr>");

        sb.append("<tr><td><nobr>Number of distributor nodes</nobr></td><td align=\"right\">").append(options.nodes() == null ? "Autodetect" : options.nodes().size()).append("</td></tr>");
        sb.append("<tr><td><nobr>Number of storage nodes</nobr></td><td align=\"right\">").append(options.nodes() == null ? "Autodetect" : options.nodes().size()).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum distributor nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(options.minDistributorNodesUp()).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum storage nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(options.minStorageNodesUp()).append("</td></tr>");
        sb.append("<tr><td><nobr>Minimum percentage of distributor nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(DecimalDot2.format(100 * options.minRatioOfDistributorNodesUp())).append(" %</td></tr>");
        sb.append("<tr><td><nobr>Minimum percentage of storage nodes being up for cluster to be up</nobr></td><td align=\"right\">").append(DecimalDot2.format(100 * options.minRatioOfStorageNodesUp())).append(" %</td></tr>");

        sb.append("<tr><td><nobr>Show local cluster state changes</nobr></td><td align=\"right\">").append(options.showLocalSystemStatesInEventLog()).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum event log size</nobr></td><td align=\"right\">").append(options.eventLogMaxSize()).append("</td></tr>");
        sb.append("<tr><td><nobr>Maximum node event log size</nobr></td><td align=\"right\">").append(options.eventNodeLogMaxSize()).append("</td></tr>");
        sb.append("<tr><td><nobr>Wanted distribution bits</nobr></td><td align=\"right\">").append(options.distributionBits()).append("</td></tr>");
        sb.append("<tr><td><nobr>Max deferred task version wait time</nobr></td><td align=\"right\">").append(options.maxDeferredTaskVersionWaitTime().toMillis()).append("ms</td></tr>");
        sb.append("<tr><td><nobr>Cluster has global document types configured</nobr></td><td align=\"right\">").append(options.clusterHasGlobalDocumentTypes()).append("</td></tr>");
        sb.append("<tr><td><nobr>Enable 2-phase cluster state activation protocol</nobr></td><td align=\"right\">").append(options.enableTwoPhaseClusterStateActivation()).append("</td></tr>");
        sb.append("<tr><td><nobr>Cluster auto feed block on resource exhaustion enabled</nobr></td><td align=\"right\">")
          .append(options.clusterFeedBlockEnabled()).append("</td></tr>");
        sb.append("<tr><td><nobr>Feed block limits</nobr></td><td align=\"right\">")
          .append(options.clusterFeedBlockLimit().entrySet().stream()
                                       .map(kv -> String.format("%s: %.2f%%", escaped(kv.getKey()), kv.getValue() * 100.0))
                                       .sorted()
                                       .collect(Collectors.joining("<br/>"))).append("</td></tr>");

        sb.append("</table>");
    }

    private static String escaped(String input) {
        return HtmlTable.escape(input);
    }

    private static String splitZooKeeperAddress(String s) {
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

}
