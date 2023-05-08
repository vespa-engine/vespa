// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.mocks;

import com.yahoo.vdslib.state.Node;
import com.yahoo.vespa.clustercontroller.core.Event;
import com.yahoo.vespa.clustercontroller.core.EventLogInterface;
import com.yahoo.vespa.clustercontroller.core.NodeEvent;

import java.util.logging.Level;

public class TestEventLog implements EventLogInterface {
    private StringBuilder events = new StringBuilder();

    public void clear() { events = new StringBuilder(); }
    public String toString() { return events.toString(); }

    @Override
    public void add(Event e) {
        events.append("add(" + e.getDescription() + ")\n");
    }

    @Override
    public void add(Event e, boolean logInfo) {
        events.append("add(" + e + ", log ? " + logInfo + ")\n");
    }

    @Override
    public void addNodeOnlyEvent(NodeEvent e, Level level) {
        events.append("add(" + e + ", " + level + ")\n");
    }

    @Override
    public int getNodeEventsSince(Node n, long time) {
        throw new IllegalStateException("Should never be called.");
    }

    @Override
    public long getRecentTimePeriod() {
        throw new IllegalStateException("Should never be called.");
    }

    @Override
    public void writeHtmlState(StringBuilder sb, Node node) {
        throw new IllegalStateException("Should never be called.");
    }

    @Override
    public void setMaxSize(int size, int nodesize) {
        throw new IllegalStateException("Should never be called.");
    }
}
