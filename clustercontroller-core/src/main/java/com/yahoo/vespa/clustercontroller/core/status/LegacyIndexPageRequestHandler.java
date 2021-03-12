// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.Timer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.VdsClusterHtmlRenderer;

import java.util.*;

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
    private final boolean showLocalSystemStatesInLog;

    public LegacyIndexPageRequestHandler(Timer timer, boolean showLocalSystemStatesInLog, ContentCluster cluster,
                                         MasterElectionHandler masterElectionHandler,
                                         StateVersionTracker stateVersionTracker,
                                         EventLog eventLog, long startedTime, RunDataExtractor data)
    {
        this.timer = timer;
        this.showLocalSystemStatesInLog = showLocalSystemStatesInLog;
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
            cluster.writeHtmlState(
                    new VdsClusterHtmlRenderer(),
                    content,
                    timer,
                    stateVersionTracker.getVersionedClusterStateBundle(),
                    stateVersionTracker.getAggregatedClusterStats(),
                    data.getOptions().storageDistribution,
                    data.getOptions(),
                    eventLog
            );
            // Current cluster state and cluster state history
            writeHtmlState(stateVersionTracker, content, request);
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

    public void writeHtmlState(StateVersionTracker stateVersionTracker, StringBuilder sb, StatusPageServer.HttpRequest request) {
        boolean showLocal = showLocalSystemStatesInLog;
        if (request.hasQueryParameter("showlocal")) {
            showLocal = true;
        } else if (request.hasQueryParameter("hidelocal")) {
            showLocal = false;
        }

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

}
