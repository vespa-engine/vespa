// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.state.Node;

import java.util.List;

public interface EventLogInterface {

    public void add(Event e);
    public void add(Event e, boolean logInfo);
    public void addNodeOnlyEvent(NodeEvent e, java.util.logging.Level level);
    public int getNodeEventsSince(Node n, long time);
    public long getRecentTimePeriod();
    public void writeHtmlState(StringBuilder sb, Node node);
    public void setMaxSize(int size, int nodesize);

}
