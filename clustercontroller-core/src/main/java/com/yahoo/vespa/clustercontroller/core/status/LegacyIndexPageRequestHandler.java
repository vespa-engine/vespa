// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

/**
* @author Haakon Humberset
*/
public class LegacyIndexPageRequestHandler implements StatusPageServer.RequestHandler {

    private final Timer timer;
    private final ContentCluster cluster;
    private final MasterElectionHandler masterElectionHandler;
    private final StateVersionTracker stateVersionTracker;
    private final EventLog eventLog;
    private final long startedTime;
    private final RunDataExtractor data;

    public LegacyIndexPageRequestHandler(Timer timer,
                                         ContentCluster cluster,
                                         MasterElectionHandler masterElectionHandler,
                                         StateVersionTracker stateVersionTracker,
                                         EventLog eventLog,
                                         long startedTime,
                                         RunDataExtractor data) {
        this.timer = timer;
        this.cluster = cluster;
        this.masterElectionHandler = masterElectionHandler;
        this.stateVersionTracker = stateVersionTracker;
        this.eventLog = eventLog;
        this.startedTime = startedTime;
        this.data = data;
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        long currentTime = timer.getCurrentTimeInMillis();

        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("text/html");
        StringBuilder content = new StringBuilder();
        content.append("<!-- Answer to request " + request + " -->\n");
        response.writeHtmlHeader(content, cluster.getName() + " Cluster Controller " + data.getOptions().fleetControllerIndex + " Status Page");
        content.append("<p><font size=\"-1\">")
                .append(" [ <a href=\"#config\">Current config</a>")
                .append(" | <a href=\"#clusterstates\">Cluster states</a>")
                .append(" | <a href=\"#eventlog\">Event log</a>")
                .append(" ]</font></p>\n");
        content.append("<table><tr><td>UTC time when creating this page:</td><td align=\"right\">").append(RealTimer.printDateNoMilliSeconds(currentTime, tz)).append("</td></tr>");
        //content.append("<tr><td>Fleetcontroller version:</td><td align=\"right\">" + Vtag.V_TAG_PKG + "</td></tr/>");
        content.append("<tr><td>Cluster controller uptime:</td><td align=\"right\">" + RealTimer.printDuration(currentTime - startedTime) + "</td></tr></table>");
        if (masterElectionHandler.isAmongNthFirst(data.getOptions().stateGatherCount)) {
            // Table overview of all the nodes
            writeHtmlState(cluster, content, timer, stateVersionTracker, data.getOptions(), eventLog);
            // Current cluster state and cluster state history
            writeHtmlState(stateVersionTracker, content);
        } else {
            // Overview of current config
            data.getOptions().writeHtmlState(content);
        }
        // State of master election
        masterElectionHandler.writeHtmlState(content, data.getOptions().stateGatherCount);
        // Overview of current config
        data.getOptions().writeHtmlState(content);
        // Event log
        eventLog.writeHtmlState(content, null);
        response.writeHtmlFooter(content, "");
        response.writeContent(content.toString());

        return response;
    }

    public void writeHtmlState(StateVersionTracker stateVersionTracker, StringBuilder sb) {
        sb.append("<h2 id=\"clusterstates\">Cluster states</h2>\n");
        writeClusterStates(sb, stateVersionTracker.getVersionedClusterStateBundle());

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
        sb.append("<p>Baseline cluster state:<br><code>").append(clusterStates.getBaselineClusterState().toString()).append("</code></p>\n");
        clusterStates.getDerivedBucketSpaceStates().forEach((bucketSpace, state) -> {
            sb.append("<p>" + bucketSpace + " cluster state:<br><code>").append(state.getClusterState().toString()).append("</code></p>\n");
        });
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
        if (state.clusterFeedIsBlocked()) { // Implies FeedBlock != null
            table.appendRaw("<h3 style=\"color: red\">Cluster feeding is blocked!</h3>\n");
            table.appendRaw(String.format("<p>Summary: <strong>%s</strong></p>\n",
                                          HtmlTable.escape(state.getFeedBlockOrNull().getDescription())));
        }

        List<Group> groups = LeafGroups.enumerateFrom(options.storageDistribution.getRootGroup());

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
                              options.minMergeCompletionRatio,
                              options.maxPrematureCrashes,
                              options.clusterFeedBlockLimit,
                              eventLog,
                              cluster.getName(),
                              localName);
        }
        table.addTable(sb, options.stableStateTimePeriod);
    }

    private void storeNodeInfo(ContentCluster cluster, int nodeIndex, NodeType nodeType, Map<Integer, NodeInfo> nodeInfoByIndex) {
        NodeInfo nodeInfo = cluster.getNodeInfo(new Node(nodeType, nodeIndex));
        if (nodeInfo == null) return;
        nodeInfoByIndex.put(nodeIndex, nodeInfo);
    }

}
