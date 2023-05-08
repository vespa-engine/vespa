// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;

public interface EventLogInterface {

    void add(Event e);
    void add(Event e, boolean logInfo);
    void addNodeOnlyEvent(NodeEvent e, java.util.logging.Level level);
    int getNodeEventsSince(Node n, long time);
    long getRecentTimePeriod();
    void writeHtmlState(StringBuilder sb, Node node);
    void setMaxSize(int size, int nodesize);

}
