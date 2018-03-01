// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.VdsClusterHtmlRendrer;

import java.util.Iterator;
import java.util.TimeZone;

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
    private boolean showLocalSystemStatesInLog = true;

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
        // State of master election
        masterElectionHandler.writeHtmlState(content, data.getOptions().stateGatherCount);
        if (masterElectionHandler.isAmongNthFirst(data.getOptions().stateGatherCount)) {
            // Table overview of all the nodes
            cluster.writeHtmlState(
                    new VdsClusterHtmlRendrer(),
                    content,
                    timer,
                    stateVersionTracker.getVersionedClusterState(),
                    stateVersionTracker.getAggregatedClusterStats(),
                    data.getOptions().storageDistribution,
                    data.getOptions(),
                    eventLog
            );
            // Overview of current config
            data.getOptions().writeHtmlState(content, request);
            // Current cluster state and cluster state history
            writeHtmlState(stateVersionTracker, content, request);
        } else {
            // Overview of current config
            data.getOptions().writeHtmlState(content, request);
        }
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

        sb.append("<h2 id=\"clusterstates\">Cluster states</h2>\n")
          .append("<p>Current cluster state:<br><code>").append(stateVersionTracker.getVersionedClusterState().toString()).append("</code></p>\n");

        if ( ! stateVersionTracker.getClusterStateHistory().isEmpty()) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            sb.append("<h3 id=\"clusterstatehistory\">Cluster state history</h3>\n");
            if (showLocal) {
                sb.append("<p>Cluster states shown in gray are just transition states on the fleet controller and has never been sent to any nodes.</p>");
            }
            sb.append("<table border=\"1\" cellspacing=\"0\"><tr>\n")
              .append("  <th>Creation date (").append(tz.getDisplayName(false, TimeZone.SHORT)).append(")</th>\n")
              .append("  <th>Cluster state</th>\n")
              .append("</tr>\n");
            // Write cluster state history in reverse order (newest on top)
            Iterator<ClusterStateHistoryEntry> stateIterator = stateVersionTracker.getClusterStateHistory().iterator();
            ClusterStateHistoryEntry current = null;
            while (stateIterator.hasNext()) {
                ClusterStateHistoryEntry nextEntry = stateIterator.next();
                if (nextEntry.state().isOfficial() || showLocal) {
                    if (current != null) writeClusterStateEntry(current, nextEntry, sb, tz);
                    current = nextEntry;
                }
            }
            if (current != null) writeClusterStateEntry(current, null, sb, tz);
            sb.append("</table>\n");
        }
    }

    private void writeClusterStateEntry(ClusterStateHistoryEntry entry, ClusterStateHistoryEntry last, StringBuilder sb, TimeZone tz) {
        sb.append("<tr><td>").append(RealTimer.printDate(entry.time(), tz))
                .append("</td><td>").append(entry.state().isOfficial() ? "" : "<font color=\"grey\">");
        sb.append(entry.state());
        if (last != null) {
            sb.append("<br><b>Diff</b>: ").append(last.state().getHtmlDifference(entry.state()));
        }
        sb.append(entry.state().isOfficial() ? "" : "</font>").append("</td></tr>\n");
    }

}
