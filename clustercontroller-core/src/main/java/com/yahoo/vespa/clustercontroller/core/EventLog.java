// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.log.LogLevel;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.utils.util.MetricReporter;

import java.util.*;
import java.util.logging.Logger;

public class EventLog implements EventLogInterface {

    public static Logger log = Logger.getLogger(EventLog.class.getName());

    private final Timer timer;
    private final LinkedList<Event> eventLog = new LinkedList<>();
    private final Map<Node, LinkedList<NodeEvent>> nodeLog = new TreeMap<>();
    private MetricUpdater metricUpdater;  // may be null
    private long eventsSeen = 0;
    private long startTime;
    private int maxSize = 1024;
    private int maxNodeSize = 1024;
    private long recentTimePeriod = 7 * 24 * 60 * 60 * 1000; // millisecs - 1 week

    /** Note: metricReporter may be null. */
    public EventLog(Timer timer, MetricUpdater metricUpdater) {
        this.timer = timer;
        this.startTime = timer.getCurrentTimeInMillis();
        this.metricUpdater = metricUpdater;
    }

    public void setMaxSize(int size, int nodesize) {
        if (size < 1 || nodesize < 1) {
            throw new IllegalArgumentException("Max size must be at least 1");
        }
        maxSize = size;
        while (eventLog.size() > maxSize) {
            eventLog.remove(0);
        }
        maxNodeSize = nodesize;
        for (List<NodeEvent> list : nodeLog.values()) {
            while (list.size() > maxNodeSize) {
                list.remove(0);
            }
        }
    }

    public long getRecentTimePeriod() { return recentTimePeriod; }

    public void add(Event e) { add(e, true); }

    public void add(Event e, boolean logInfo) {
        ++eventsSeen;
        eventLog.add(e);
        if (eventLog.size() > maxSize) {
            eventLog.remove(0);
        }

        if (e instanceof NodeEvent) {
            addNodeOnlyEvent((NodeEvent)e, logInfo ? LogLevel.INFO: LogLevel.DEBUG);
        } else {
            log.log(logInfo ? LogLevel.INFO : LogLevel.DEBUG, e.toString());
        }
    }

    public void addNodeOnlyEvent(NodeEvent e, java.util.logging.Level level) {
        log.log(level, "Added node only event: " + e.toString());
        if (metricUpdater != null) {
            metricUpdater.recordNewNodeEvent();
        }
        LinkedList<NodeEvent> nodeList = nodeLog.get(e.getNode().getNode());
        if (nodeList == null) {
            nodeList = new LinkedList<>();
            nodeLog.put(e.getNode().getNode(), nodeList);
        }
        nodeList.add(e);
        if (nodeList.size() > maxNodeSize) {
            nodeList.remove(0);
        }
    }

    public int getNodeEventsSince(Node n, long time) {
        LinkedList<NodeEvent> events = nodeLog.get(n);
        int count = 0;
        if (events != null) {
            Iterator<NodeEvent> it = events.descendingIterator();
            while (it.hasNext()) {
                NodeEvent e = it.next();
                if (e.getTimeMs() < time) break;
                ++count;
            }
        }
        return count;
    }

    /** Used in unit testing to verify events generated. */
    public List<NodeEvent> getNodeEvents(Node n) {
        return new ArrayList<>(nodeLog.get(n));
    }

    public void writeHtmlState(StringBuilder sb, Node node) {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        LinkedList<Event> events = new LinkedList<>();
        long currentTime = timer.getCurrentTimeInMillis();
        long recentNodeEvents = 0;
        if (node == null) {
            events = eventLog;
            sb.append("<h2 id=\"eventlog\">Event log</h2>\n")
              .append("<p>A total number of " + eventsSeen + " has been seen since ").append(RealTimer.printDate(startTime, tz)).append(".</p>\n");
        } else {
            if (nodeLog.containsKey(node)) {
                events.addAll(nodeLog.get(node));
            }
            recentNodeEvents = getNodeEventsSince(node, currentTime - recentTimePeriod);
            sb.append("<h2>Node event log for " + node + "</h2>\n")
              .append("<p>A total number of " + events.size() + " events has been seen since ")
              .append(RealTimer.printDate(startTime, tz)).append(".</p>\n")
              .append("<p>Recently, " + recentNodeEvents + " events has been seen since ")
              .append(RealTimer.printDate(currentTime - recentTimePeriod, tz)).append(".</p>\n");
        }
        sb.append("<table border=\"1\" cellspacing=\"0\">\n")
          .append("<tr><td>Date (").append(tz.getDisplayName(false, TimeZone.SHORT))
                .append(")</td><td>Type</td><td>Node</td><td>Bucket space</td><td>Event</td></tr>\n");
        int nr = 0;
        Iterator<Event> eventIterator = (events == null ? null : events.descendingIterator());
        if (eventIterator != null) while (eventIterator.hasNext()) {
            Event e = eventIterator.next();
            String colStart = "<font color=\"" + (++nr > recentNodeEvents ? "grey" : "black") + "\">";
            String colEnd = "</font>";
            sb.append("<tr>\n");

            addNobrTableCell(sb, colStart, colEnd, RealTimer.printDate(e.getTimeMs(), tz));
            addNobrTableCell(sb, colStart, colEnd, e.getCategory());
            if (e instanceof NodeEvent) {
                NodeEvent nodeEvent = (NodeEvent)e;
                addNobrTableCell(sb, colStart, colEnd, nodeEvent.getNode().toString());
                addNobrTableCell(sb, colStart, colEnd, nodeEvent.getBucketSpace().orElse(" - "));
            } else {
                addNobrTableCell(sb, colStart, colEnd, " - ");
                addNobrTableCell(sb, colStart, colEnd, " - ");
            }
            addTableCell(sb, colStart, colEnd, e.getDescription());

            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
    }

    private static void addNobrTableCell(StringBuilder sb, String colStart, String colEnd, String cellValue) {
        sb.append("  <td><nobr>").append(colStart).append(cellValue).append(colEnd).append("</nobr></td>\n");
    }

    private static void addTableCell(StringBuilder sb, String colStart, String colEnd, String cellValue) {
        sb.append("  <td>").append(colStart).append(cellValue).append(colEnd).append("</td>\n");
    }

}
