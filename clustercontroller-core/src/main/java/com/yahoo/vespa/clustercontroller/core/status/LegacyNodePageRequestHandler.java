// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vespa.clustercontroller.core.*;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;

import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author Haakon Humberset
*/
public class LegacyNodePageRequestHandler implements StatusPageServer.RequestHandler {

    private static final Pattern nodePattern = Pattern.compile("/node=([a-z]+)\\.(\\d+)");
    private final Timer timer;
    private final EventLog eventLog;
    private final ContentCluster cluster;

    public LegacyNodePageRequestHandler(Timer timer, EventLog eventLog, ContentCluster cluster) {
        this.timer = timer;
        this.eventLog = eventLog;
        this.cluster = cluster;
    }

    @Override
    public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
        Matcher m = nodePattern.matcher(request.getPath());
        if (!m.matches()) {
            throw new IllegalStateException("Node request handler invoked but failed to match path");
        }
        TimeZone tz = TimeZone.getTimeZone("UTC");
        long currentTime = timer.getCurrentTimeInMillis();
        NodeType nodeType = NodeType.get(m.group(1));
        int index = Integer.parseInt(m.group(2));
        Node node = new Node(nodeType, index);

        StatusPageResponse response = new StatusPageResponse();
        response.setContentType("text/html");
        StringBuilder content = new StringBuilder();
        content.append("<!-- Answer to request " + request + " -->\n");
        response.writeHtmlHeader(content, "Cluster Controller Status Page - Node status for " + node);
        content.append("<p>UTC time when creating this page: ").append(RealTimer.printDateNoMilliSeconds(currentTime, tz)).append("</p>");
        content.append("[ <a href=\"..\">Back to cluster overview</a> ] <br><br>");
        eventLog.writeHtmlState(content, node);
        NodeInfo nodeInfo = cluster.getNodeInfo(node);
        content.append("<h2>Host info</h2>\n");
        if (nodeInfo.getHostInfo() != null) {
            content.append("<pre>\n").append(nodeInfo.getHostInfo().getRawCreationString()).append("\n</pre>\n");
        } else {
            content.append("Not retrieved\n");
        }
        response.writeHtmlFooter(content, "");
        response.writeContent(content.toString());
        return response;
    }

}
