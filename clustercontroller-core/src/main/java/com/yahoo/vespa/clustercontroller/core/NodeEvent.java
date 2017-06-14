// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

public class NodeEvent implements Event {

    private final NodeInfo node;
    private final String description;
    private final long eventTime;

    public enum Type {
        REPORTED,
        CURRENT,
        WANTED
    }

    private final Type type;

    public NodeEvent(NodeInfo node, String description, Type type, long currentTime) {
        this.node = node;
        this.description = description;
        this.eventTime = currentTime;
        this.type = type;
    }

    public NodeInfo getNode() {
        return node;
    }

    @Override
    public long getTimeMs() {
        return eventTime;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Event: " + node.getNode() + ": " + description;
    }

    @Override
    public String getCategory() {
        return type.toString();
    }

    public Type getType() {
        return type;
    }
}
